/**
 * Motor do deck: pilha de cards + gesto de arrastar.
 *
 * Princípios (skill motion/animation):
 *  - Manipulação direta: durante o arrasto o card fica sob o dedo, 1:1, sem easing.
 *  - Ao soltar, a decisão usa posição + velocidade (um peteleco curto já decide).
 *  - O card sai com mola herdando a velocidade do dedo; volta com bounce só se houve energia.
 *  - Tudo é interrompível: pegar um card no meio da animação continua dali.
 *  - O próximo card é liberado na hora; ninguém espera a animação terminar.
 *  - Reduce Motion: o deslocamento automático vira fade; a informação (carimbo) fica.
 */
import { spring, SPRING, VelocityTracker, rubberBand, clamp, haptic, reducedMotion } from '../../core/motion.js';
import { DECISION_DIRECTION } from '../../core/domain.js';
import { createCard, measure, paint, paintStamps, intent, THRESHOLD } from './card.js';

const VISIBLE = 3;                 // cards no DOM: topo + 2 atrás
const REST = [                     // pose de repouso por profundidade
  { sc: 1, oy: 0 },
  { sc: 0.95, oy: 12 },
  { sc: 0.95, oy: 12 },            // o 3º fica escondido atrás do 2º (opacity 0 no CSS)
];
const SLOP = 6;                    // px antes de virar arrasto; abaixo disso é toque
const LOOKAHEAD = 0.15;            // s de projeção da velocidade ao soltar
const MIN_FLING_SPEED = 1100;      // px/s: botão e teclado também "arremessam"
const NO_INTENT = { like: 0, nope: 0, super: 0 };

/**
 * @param {HTMLElement} root
 * @param {object} hooks
 *   getGroups()                 grupos que devem estar no deck, em ordem
 *   onDecide(groupId, decision) o card saiu: registrar a decisão
 *   onOpen(groupId)             toque no card: abrir detalhes
 *   onGrab(groupId)             um arrasto começou (toque virou gesto)
 *   onIntent(intent)            o gesto mudou (os botões reagem)
 *   onTopChange(groupId|null)   outro card chegou ao topo
 *   renderEmpty()               HTML do estado vazio
 */
export function createDeck(root, hooks) {
  let stack = [];        // cards na pilha (topo primeiro), sem os que estão saindo
  let gesture = null;
  let pendingSync = false;
  let frame = 0;
  let lastTop = undefined;

  /* ---------- pintura ---------- */

  function paintTop(card) {
    paint(card);
    const wants = intent(card);
    paintStamps(card, wants);
    if (card !== stack[0]) return;
    // o próximo card sobe junto com o arrasto: mostra que há mais e que ele é o próximo
    const next = stack[1];
    if (next && !next._depthAnim) {
      const progress = Math.max(wants.like, wants.nope, wants.super);
      next._s.sc = REST[1].sc + (1 - REST[1].sc) * progress;
      next._s.oy = REST[1].oy * (1 - progress);
      paint(next);
    }
    hooks.onIntent?.(wants);
  }

  function setInteractive(card, on) {
    card.inert = !on;
    card.tabIndex = on ? 0 : -1;
    if (on) card.removeAttribute('aria-hidden');
    else card.setAttribute('aria-hidden', 'true');
  }

  /** Mola no movimento do arrasto (x, y, r). Guarda a animação para poder interromper. */
  function animateMove(card, target, options) {
    card._anim?.stop();
    const s = card._s;
    const anim = spring({ x: s.x, y: s.y, r: s.r }, target, { rest: { x: 0.5, y: 0.5, r: 0.05 }, ...options });
    card._anim = anim;
    anim.done.then(() => { if (card._anim === anim) card._anim = null; });
    return anim;
  }

  /** Leva um card para a pose de uma profundidade da pilha. */
  function moveToDepth(card, depth) {
    card._depthAnim?.stop();
    const target = REST[depth];
    if (reducedMotion()) { Object.assign(card._s, target); paint(card); return; }
    const anim = spring({ sc: card._s.sc, oy: card._s.oy }, target, {
      ...SPRING.snappy,
      rest: { sc: 0.001, oy: 0.1 },
      onUpdate: (value) => { Object.assign(card._s, value); if (card === stack[0]) paintTop(card); else paint(card); },
    });
    card._depthAnim = anim;
    anim.done.then(() => { if (card._depthAnim === anim) card._depthAnim = null; });
  }

  /** Volta ao centro. Com energia no gesto, um bounce discreto; sem, assenta. */
  function settle(card, velocity = { x: 0, y: 0 }, shape = null) {
    const energetic = Math.hypot(velocity.x, velocity.y) > 300;
    const curve = reducedMotion() ? { duration: 0.2, bounce: 0 } : shape ?? (energetic ? SPRING.gentle : SPRING.smooth);
    return animateMove(card, { x: 0, y: 0, r: 0 }, {
      ...curve,
      velocity: { x: velocity.x, y: velocity.y },
      onUpdate: (value) => { Object.assign(card._s, value); paintTop(card); },
    });
  }

  /* ---------- sincronizar com os dados ---------- */

  function sync({ restore = null } = {}) {
    const groups = hooks.getGroups();
    if (gesture) { pendingSync = true; return groups.length; }
    root.querySelector('.skeleton-card')?.remove();

    const byId = new Map(stack.map((card) => [card._id, card]));
    const next = groups.slice(0, VISIBLE).map((group) => byId.get(group.id) ?? createCard(group));
    const previousDepth = new Map(stack.map((card, depth) => [card, depth]));
    for (const card of stack) if (!next.includes(card)) dismiss(card);
    stack = next;

    stack.forEach((card, depth) => {
      card.dataset.depth = String(depth);
      card.style.zIndex = String(30 - depth * 10);
      setInteractive(card, depth === 0);
      if (!card.isConnected) {
        root.append(card);
        measure(card);
        Object.assign(card._s, REST[depth]);
        if (depth === 0 && restore?.groupId === card._id) flyBack(card, restore.decision);
        else { paint(card); if (depth === 0) card.classList.add('is-entering'); }
      } else if (previousDepth.get(card) !== depth) {
        moveToDepth(card, depth);
      }
    });

    renderEmpty(groups.length === 0);
    const topId = stack[0]?._id ?? null;
    if (topId !== lastTop) { lastTop = topId; hooks.onTopChange?.(topId); }
    return groups.length;
  }

  function renderEmpty(isEmpty) {
    let empty = root.querySelector('.deck-empty');
    if (!isEmpty) { empty?.remove(); return; }
    if (!empty) {
      empty = document.createElement('div');
      empty.className = 'deck-empty';
      root.prepend(empty); // atrás dos cards: o último card sai e revela o vazio
    }
    empty.innerHTML = hooks.renderEmpty();
  }

  /** Sai do deck sem decisão (mudou o filtro): some rápido, sem chamar atenção. */
  function dismiss(card) {
    card._anim?.stop();
    card._depthAnim?.stop();
    setInteractive(card, false);
    card.style.zIndex = '0';
    if (reducedMotion()) { card.remove(); return; }
    card.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 150, easing: 'ease', fill: 'forwards' })
      .finished.then(() => card.remove(), () => card.remove());
  }

  /* ---------- decisão ---------- */

  function fling(card, decision, velocity = { x: 0, y: 0 }) {
    const hadFocus = card.contains(document.activeElement);
    card.classList.remove('is-dragging');
    card.dataset.depth = 'leaving';
    card.style.zIndex = '40';
    setInteractive(card, false);
    stack = stack.filter((c) => c !== card);
    hooks.onIntent?.(NO_INTENT);
    haptic(decision === 'Dislike' ? 6 : 12);
    flyOut(card, decision, velocity);
    hooks.onDecide(card._id, decision); // → sync(): o próximo card sobe na hora
    if (hadFocus) stack[0]?.focus({ preventScroll: true });
  }

  function flyOut(card, decision, velocity) {
    const dir = DECISION_DIRECTION[decision];
    if (reducedMotion()) {
      paintStamps(card, { like: dir.x > 0 ? 1 : 0, nope: dir.x < 0 ? 1 : 0, super: dir.y < 0 ? 1 : 0 });
      card.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 240, easing: 'ease', fill: 'forwards' })
        .finished.finally(() => card.remove());
      return;
    }
    const s = card._s;
    const stage = (root.closest('.view') ?? root).getBoundingClientRect();
    const target = dir.x
      ? { x: dir.x * (stage.width + card._w), y: s.y + clamp(velocity.y * 0.3, -card._h, card._h), r: (Math.sign(s.r) || dir.x) * 24 }
      : { x: s.x + velocity.x * 0.2, y: -(stage.height + card._h), r: s.r };
    const speed = dir.x
      ? { x: dir.x * Math.max(Math.abs(velocity.x), MIN_FLING_SPEED), y: velocity.y }
      : { x: velocity.x, y: Math.min(velocity.y, -MIN_FLING_SPEED) };
    const anim = animateMove(card, target, {
      ...SPRING.fling,
      velocity: speed,
      onUpdate: (value) => { Object.assign(s, value); paint(card); paintStamps(card, intent(card)); },
      until: () => {
        const box = card.getBoundingClientRect();
        return box.right < stage.left || box.left > stage.right || box.bottom < stage.top;
      },
    });
    anim.done.then(() => card.remove());
  }

  /** O banco recusou a decisão: o card volta pelo mesmo caminho por onde saiu. */
  function flyBack(card, decision) {
    const dir = DECISION_DIRECTION[decision] ?? { x: 0, y: 0 };
    if (reducedMotion()) { paintTop(card); return; }
    Object.assign(card._s, { x: dir.x * (card._w + 80), y: dir.y * (card._h + 80), r: dir.x * 16 });
    paintTop(card);
    settle(card, { x: 0, y: 0 }, SPRING.gentle);
  }

  function releaseDecision(card, velocity) {
    const { x, y } = card._s;
    const px = x + velocity.x * LOOKAHEAD;
    const py = y + velocity.y * LOOKAHEAD;
    if (y < 0 && py < -card._h * THRESHOLD.y && Math.abs(py) > Math.abs(px)) return 'Super Like';
    if (x > 0 && px > card._w * THRESHOLD.x) return 'Like';
    if (x < 0 && px < -card._w * THRESHOLD.x) return 'Dislike';
    return null;
  }

  /* ---------- gesto ---------- */

  root.addEventListener('pointerdown', (event) => {
    const card = event.target.closest('.card');
    if (!card || card !== stack[0] || gesture || (event.pointerType === 'mouse' && event.button !== 0)) return;
    card._anim?.stop(); // interrompe (dica, volta ao centro): o gesto continua de onde está
    measure(card);
    const box = card.getBoundingClientRect();
    gesture = {
      card,
      pointerId: event.pointerId,
      x0: event.clientX, y0: event.clientY,
      baseX: card._s.x, baseY: card._s.y,
      moved: false,
      onControl: Boolean(event.target.closest('button')),
      grabbedTop: event.clientY - box.top < box.height / 2,
      tracker: new VelocityTracker(),
      armed: null,
    };
    gesture.tracker.add(card._s.x, card._s.y);
  });

  root.addEventListener('pointermove', (event) => {
    const g = gesture;
    if (!g || event.pointerId !== g.pointerId) return;
    const dx = event.clientX - g.x0;
    const dy = event.clientY - g.y0;
    if (!g.moved) {
      if (Math.hypot(dx, dy) < SLOP) return;
      g.moved = true;
      g.card.classList.add('is-dragging');
      try { g.card.setPointerCapture(event.pointerId); } catch { /* ponteiro já liberado */ }
      hooks.onGrab?.(g.card._id);
    }
    const s = g.card._s;
    s.x = g.baseX + dx;
    const rawY = g.baseY + dy;
    s.y = rawY > 0 ? rubberBand(rawY, g.card._h) : rawY; // para baixo não há decisão: resiste
    // pegou em cima, gira para o lado do arrasto; pegou embaixo, gira ao contrário (como um papel)
    s.r = (s.x / g.card._w) * 18 * (g.grabbedTop ? 1 : -1);
    g.tracker.add(s.x, s.y);

    const wants = intent(g.card);
    const armed = wants.like >= 1 ? 'Like' : wants.nope >= 1 ? 'Dislike' : wants.super >= 1 ? 'Super Like' : null;
    if (armed !== g.armed) { if (armed) haptic(8); g.armed = armed; }

    if (!frame) frame = requestAnimationFrame(() => { frame = 0; if (gesture) paintTop(gesture.card); });
  });

  const release = (event) => {
    const g = gesture;
    if (!g || event.pointerId !== g.pointerId) return;
    gesture = null;
    const { card } = g;
    if (frame) { cancelAnimationFrame(frame); frame = 0; }
    card.classList.remove('is-dragging');
    try { card.releasePointerCapture(event.pointerId); } catch { /* idem */ }

    if (!g.moved) {
      if (event.type === 'pointerup' && !g.onControl) hooks.onOpen(card._id);
      if (card._s.x || card._s.y || card._s.r) settle(card); // foi pego no meio de uma animação
    } else {
      paintTop(card);
      const velocity = event.type === 'pointercancel' ? { x: 0, y: 0 } : g.tracker.velocity();
      const decision = event.type === 'pointercancel' ? null : releaseDecision(card, velocity);
      if (decision) fling(card, decision, velocity);
      else settle(card, velocity);
    }
    if (pendingSync) { pendingSync = false; sync(); }
  };
  root.addEventListener('pointerup', release);
  root.addEventListener('pointercancel', release);
  root.addEventListener('lostpointercapture', release); // o "soltar" se perdeu: o card não fica preso no meio

  root.addEventListener('keydown', (event) => {
    const card = event.target.closest?.('.card');
    if (card && event.target === card && (event.key === 'Enter' || event.key === ' ')) {
      event.preventDefault();
      hooks.onOpen(card._id);
    }
  });

  /* ---------- API ---------- */

  return {
    sync,

    /** Remede e repinta (a tela voltou a aparecer ou mudou de tamanho). */
    relayout() {
      for (const card of stack) {
        measure(card);
        if (card === stack[0]) paintTop(card); else paint(card);
      }
    },

    /** Decide o card do topo por botão/teclado. false se não há card ou há um gesto em curso. */
    decideTop(decision) {
      const card = stack[0];
      if (!card || gesture) return false;
      measure(card);
      fling(card, decision);
      return true;
    },

    topId: () => stack[0]?._id ?? null,

    /** Carregando (troca de perfil): tira os cards e mostra o esqueleto. */
    showLoading() {
      for (const card of stack) { card._anim?.stop(); card._depthAnim?.stop(); card.remove(); }
      stack = [];
      lastTop = undefined;
      root.innerHTML = '<div class="skeleton-card" aria-hidden="true"></div>';
    },

    /**
     * Dica de uso, uma vez na vida: o card balança para a direita e para a esquerda,
     * mostrando os carimbos "Curtir" e "Passar" e acendendo os botões.
     * Qualquer toque interrompe. Devolve true se completou.
     */
    async nudge() {
      const card = stack[0];
      if (!card || gesture || reducedMotion()) return false;
      measure(card);
      const dx = card._w * 0.22;
      for (const step of [{ x: dx, r: 4 }, { x: -dx, r: -4 }, { x: 0, r: 0 }]) {
        const anim = animateMove(card, { ...step, y: 0 }, {
          duration: 0.5, bounce: 0.1,
          onUpdate: (value) => { Object.assign(card._s, value); paintTop(card); },
        });
        if (!(await anim.done) || stack[0] !== card) return false;
      }
      return true;
    },
  };
}
