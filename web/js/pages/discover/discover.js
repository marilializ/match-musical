/**
 * Tela Descobrir — o primeiro contato com o app. Só o deck, os botões e o filtro:
 * a pessoa chega e já começa a deslizar (direita curte, esquerda passa, cima é super like).
 *
 * "Ver mais" (ou tocar no card):
 *   celular          → sheet de baixo (não há "lado direito")
 *   tablet e desktop → um card de detalhes sai de dentro do principal, deslizando para a direita
 */
import { state, deckGroups, filterActive, groupById } from '../../core/state.js';
import { on } from '../../core/events.js';
import { store, KEYS } from '../../core/store.js';
import { reducedMotion } from '../../core/motion.js';
import { media, isTyping } from '../../core/dom.js';
import { DECISIONS, DECISION_ORDER } from '../../core/domain.js';
import * as repo from '../../core/repo.js';
import { icon } from '../../ui/icon.js';
import { emptyState } from '../../ui/empty.js';
import { actionButton } from '../../ui/action-button.js';
import { openGroupSheet } from '../../features/group-sheet.js';
import { commitDecision, setDeckAnimator } from '../../features/swipe.js';
import { createDeck } from './deck.js';
import { createDetailPanel } from './detail-panel.js';
import { openFilterSheet } from './filter-sheet.js';

const KEY_TO_DECISION = Object.fromEntries(DECISION_ORDER.map((d) => [DECISIONS[d].key, d]));

let view;
let deck;
let details;
let buttons;
let nudgeScheduled = false;

export function init(section) {
  view = section;
  view.innerHTML = `
    <header class="discover-header">
      <h1 class="wordmark only-compact"><span class="logo-mark" aria-hidden="true">${icon('wave')}</span>sync</h1>
      <h1 class="sr-only only-regular">Descobrir</h1>
      <button class="icon-btn has-label" type="button" data-open-filters aria-label="Filtros">
        ${icon('sliders')}<span class="only-regular" aria-hidden="true">Filtros</span><span class="icon-dot" hidden></span>
      </button>
    </header>
    <div class="discover-stage">
      <div class="discover-main" data-main>
        <div class="deck-area">
          <div class="deck" data-deck><div class="skeleton-card" aria-hidden="true"></div></div>
          <aside class="detail-panel" data-detail-panel></aside>
        </div>
        <div class="actions">${DECISION_ORDER.map((d) => actionButton(d)).join('')}</div>
      </div>
    </div>`;

  buttons = new Map([...view.querySelectorAll('[data-decision]')].map((button) => [button.dataset.decision, button]));
  details = createDetailPanel(view.querySelector('[data-main]'), view.querySelector('[data-detail-panel]'), { onClose: syncExpanded });
  deck = createDeck(view.querySelector('[data-deck]'), {
    getGroups: deckGroups,
    onDecide: (groupId, decision) => commitDecision(groupId, decision),
    onOpen: toggleDetails,
    onGrab: closeDetails,       // o card começou a se mover: os detalhes eram dele
    onTopChange: closeDetails,  // outro card chegou ao topo
    onIntent: paintButtons,
    renderEmpty,
  });

  // Decisão pedida pela sheet do grupo: se é o card do topo, ele sai voando daqui.
  setDeckAnimator((groupId, decision) => {
    if (state.route !== 'descobrir' || deck.topId() !== groupId) return false;
    setTimeout(() => deck.decideTop(decision), 120); // deixa a sheet começar a descer
    return true;
  });

  view.addEventListener('click', onClick);
  document.addEventListener('keydown', onKey);
  on('data', refresh);
  on('deck', refresh);
  on('user', () => { closeDetails(); deck.showLoading(); });
  on('swipe:reverted', ({ groupId, decision }) => { deck.sync({ restore: { groupId, decision } }); refresh(); });
  media.regular.addEventListener('change', closeDetails);
}

export function onShow() {
  deck.relayout();
  scheduleNudge();
}

export function onResize() { deck.relayout(); }

function refresh() {
  const count = deck.sync();
  for (const button of buttons.values()) button.setAttribute('aria-disabled', String(!count));
  const active = filterActive();
  view.querySelector('.icon-dot').hidden = !active;
  view.querySelector('[data-open-filters]').setAttribute('aria-label', active ? 'Filtros (ativos)' : 'Filtros');
  scheduleNudge();
}

function onClick(event) {
  const target = event.target;
  const action = target.closest('.actions [data-decision]');
  if (action) { decide(action.dataset.decision); return; }
  if (target.closest('[data-open-filters]')) { closeDetails(); openFilterSheet(); return; }
  if (target.closest('[data-details]')) { const card = target.closest('.card'); if (card) toggleDetails(card._id); return; }
  if (target.closest('[data-clear-filters]')) { state.filter = { type: 'Todos', q: '' }; refresh(); return; }
  if (target.closest('[data-demo-reset]')) repo.resetDemo();
}

function onKey(event) {
  if (state.route !== 'descobrir' || event.defaultPrevented || event.metaKey || event.ctrlKey || event.altKey) return;
  if (document.querySelector('dialog[open]') || isTyping(event.target)) return;
  if (event.key === 'Escape' && details.openId != null) { closeDetails(); return; }
  const decision = KEY_TO_DECISION[event.key];
  if (!decision) return;
  event.preventDefault();
  decide(decision, { fromKeyboard: true });
}

function decide(decision, { fromKeyboard = false } = {}) {
  const button = buttons.get(decision);
  if (button.getAttribute('aria-disabled') === 'true') return;
  closeDetails();
  if (!deck.decideTop(decision)) return;
  if (fromKeyboard) {
    // a tecla "aperta" o botão correspondente: liga o atalho ao botão na cabeça de quem usa
    button.classList.add('is-pressed');
    setTimeout(() => button.classList.remove('is-pressed'), 140);
  }
}

/* ---------- Ver mais ---------- */

function toggleDetails(groupId) {
  const group = groupById(groupId);
  if (!group) return;
  if (!media.regular.matches) { openGroupSheet(groupId); return; }
  if (details.openId === group.id) details.close();
  else details.open(group);
  syncExpanded();
}

function closeDetails() {
  if (details.openId == null) return;
  details.close();
  syncExpanded();
}

/** O botão do card do topo reflete o estado: "Ver mais" fechado, "Ver menos" aberto. */
function syncExpanded() {
  const button = view.querySelector('.card[data-depth="0"] [data-details]');
  if (!button) return;
  const open = details.openId != null;
  button.querySelector('.card-more-label').textContent = open ? 'Ver menos' : 'Ver mais';
  if (media.regular.matches) button.setAttribute('aria-expanded', String(open)); // o nome acessível fica "Ver mais"
  else button.removeAttribute('aria-expanded');
}

/* ---------- Botões e estados ---------- */

/** Os botões crescem e acendem conforme o arrasto vai na direção deles. */
function paintButtons(wants) {
  const amount = { Like: wants.like, Dislike: wants.nope, 'Super Like': wants.super };
  for (const [decision, button] of buttons) {
    const value = amount[decision];
    button.style.transform = value > 0 ? `scale(${1 + 0.12 * value})` : '';
    button.classList.toggle('is-armed', value >= 1);
  }
}

function renderEmpty() {
  if (filterActive()) {
    return emptyState({
      icon: 'sliders', title: 'Nenhum grupo aqui', text: 'Tente outros filtros.',
      actions: '<button class="btn btn-secondary" type="button" data-clear-filters>Limpar filtros</button>',
    });
  }
  return emptyState({
    icon: 'sparkles', title: 'Você viu todos', text: 'Novos grupos aparecem aqui.',
    actions: `<a class="btn btn-secondary" href="#vagas">Ver vagas</a>${state.demo ? '<button class="btn btn-plain" type="button" data-demo-reset>Recomeçar</button>' : ''}`,
  });
}

/** Dica de deslizar: uma vez só, quando a pessoa chega e ainda não decidiu nada. */
function scheduleNudge() {
  if (nudgeScheduled || store.get(KEYS.hint) || reducedMotion() || state.route !== 'descobrir' || !deck.topId()) return;
  nudgeScheduled = true;
  setTimeout(async () => {
    if (state.route !== 'descobrir' || document.querySelector('dialog[open]') || details.openId != null || store.get(KEYS.hint)) {
      nudgeScheduled = false;
      return;
    }
    if (await deck.nudge()) store.set(KEYS.hint, '1');
  }, 900);
}
