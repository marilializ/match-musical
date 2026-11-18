/**
 * Sheet modal sobre <dialog> nativo: foco preso dentro, Esc fecha, o resto da página fica inerte.
 *   compacto → sobe de baixo; fecha por botão, toque fora, Esc ou arrastando a alça para baixo
 *   regular  → modal centralizado; fecha por botão, clique fora ou Esc
 *
 * O arrasto segue o dedo 1:1; ao soltar, projeta a velocidade para decidir entre
 * fechar ou voltar — um peteleco curto fecha, um arrasto lento até a metade volta.
 */
import { spring, SPRING, VelocityTracker, rubberBand, project, reducedMotion } from '../core/motion.js';
import { media } from '../core/dom.js';
import { esc } from '../core/format.js';
import { icon } from './icon.js';

let sequence = 0;
const closeButton = (floating) =>
  `<button class="close-btn${floating ? ' is-floating' : ''}" type="button" data-sheet-close aria-label="Fechar"><span>${icon('x')}</span></button>`;

/**
 * @param {object} options
 *   title          título visível (vira o nome acessível do diálogo)
 *   label          nome acessível quando não há título visível
 *   body, foot     HTML do conteúdo e do rodapé fixo
 *   className      classe extra no <dialog>
 *   floatingClose  botão de fechar sobre a capa (sheets com imagem no topo)
 *   onClose        chamado depois que a sheet sai da tela
 * @returns {{el: HTMLDialogElement, body: HTMLElement, isOpen: boolean, close(): void}}
 */
export function openSheet({ title = '', label = '', body = '', foot = '', className = '', floatingClose = false, onClose } = {}) {
  const dialog = document.createElement('dialog');
  const titleId = `sheet-title-${++sequence}`;
  dialog.className = ['sheet', className].filter(Boolean).join(' ');
  if (title) dialog.setAttribute('aria-labelledby', titleId);
  else if (label) dialog.setAttribute('aria-label', label);

  const head = title
    ? `<div class="sheet-head"><h2 class="sheet-title" id="${titleId}">${esc(title)}</h2>${closeButton(false)}</div>`
    : floatingClose ? closeButton(true) : `<div class="sheet-head is-bare">${closeButton(false)}</div>`;
  dialog.innerHTML = `
    <div class="sheet-panel">
      <div class="sheet-handle" data-sheet-drag><span class="sheet-grabber" aria-hidden="true"></span>${head}</div>
      <div class="sheet-body">${body}</div>
      ${foot ? `<div class="sheet-foot">${foot}</div>` : ''}
    </div>`;
  document.body.append(dialog);
  dialog.showModal();
  const panel = dialog.querySelector('.sheet-panel');
  requestAnimationFrame(() => requestAnimationFrame(() => dialog.classList.add('is-open')));

  let closed = false;
  const finish = () => {
    if (!dialog.isConnected) return;
    dialog.close();
    dialog.remove();
    onClose?.();
  };
  const close = () => {
    if (closed) return;
    closed = true;
    dialog.classList.remove('is-open');
    dialog.classList.add('is-closing');
    setTimeout(finish, reducedMotion() ? 220 : 300);
  };

  dialog.addEventListener('cancel', (event) => { event.preventDefault(); close(); });
  dialog.addEventListener('click', (event) => {
    if (event.target === dialog || event.target.closest('[data-sheet-close]')) close();
  });
  bindDragToDismiss(dialog, panel, {
    canDrag: () => !closed && !reducedMotion() && !media.regular.matches,
    onDismiss: () => { closed = true; dialog.classList.remove('is-open'); },
    onGone: finish,
  });

  return {
    el: dialog,
    body: dialog.querySelector('.sheet-body'),
    get isOpen() { return !closed; },
    close,
  };
}

function bindDragToDismiss(dialog, panel, { canDrag, onDismiss, onGone }) {
  const handle = dialog.querySelector('[data-sheet-drag]');
  let drag = null;
  const paint = ({ y }) => { panel.style.transform = `translateY(${y}px)`; };

  handle.addEventListener('pointerdown', (event) => {
    if (!canDrag() || event.target.closest('button') || (event.pointerType === 'mouse' && event.button !== 0)) return;
    drag = { id: event.pointerId, y0: event.clientY, y: 0, height: panel.offsetHeight, tracker: new VelocityTracker() };
    handle.setPointerCapture(event.pointerId);
    panel.style.transition = 'none';
  });

  handle.addEventListener('pointermove', (event) => {
    if (!drag || event.pointerId !== drag.id) return;
    const raw = event.clientY - drag.y0;
    drag.y = raw < 0 ? rubberBand(raw, drag.height) * 0.3 : raw; // para cima resiste
    drag.tracker.add(0, drag.y);
    paint(drag);
  });

  const release = (event) => {
    if (!drag || event.pointerId !== drag.id) return;
    const { y, height } = drag;
    const velocity = event.type === 'pointercancel' ? 0 : drag.tracker.velocity().y;
    drag = null;
    const shouldClose = y + project(velocity, 0.99) > height * 0.4 || (velocity > 800 && y > 8);
    if (shouldClose) {
      onDismiss();
      spring({ y }, { y: height }, { ...SPRING.snappy, velocity: { y: Math.max(velocity, 600) }, onUpdate: paint, until: (v) => v.y >= height })
        .done.then(onGone);
    } else {
      spring({ y }, { y: 0 }, { ...(Math.abs(velocity) > 300 ? SPRING.gentle : SPRING.smooth), velocity: { y: velocity }, onUpdate: paint })
        .done.then(() => { panel.style.transform = ''; panel.style.transition = ''; });
    }
  };
  handle.addEventListener('pointerup', release);
  handle.addEventListener('pointercancel', release);
  handle.addEventListener('lostpointercapture', release); // o "soltar" se perdeu (saiu da janela): não deixa a sheet presa
}
