/**
 * Decisão de swipe, de qualquer origem (gesto, botão, teclado, sheet do grupo).
 *
 * Otimista: a decisão vale na tela no mesmo instante; o banco grava depois.
 * Se o banco recusar, a decisão é desfeita e o card volta (evento swipe:reverted).
 */
import { state, groupById } from '../core/state.js';
import { emit } from '../core/events.js';
import { store, KEYS } from '../core/store.js';
import { DECISIONS } from '../core/domain.js';
import { errorMessage } from '../core/format.js';
import * as repo from '../core/repo.js';
import { toast, announce } from '../ui/toast.js';

let deckAnimator = null;

/** O Descobrir registra aqui quem sabe animar o card do topo. Devolve true se assumiu a decisão. */
export function setDeckAnimator(animator) { deckAnimator = animator; }

/** Pedido vindo de fora do deck. Se o card estiver no topo e visível, ele sai voando; senão, só grava. */
export function requestDecision(groupId, decision) {
  if (deckAnimator?.(Number(groupId), decision)) return;
  commitDecision(groupId, decision, { confirm: true });
}

/**
 * Registra a decisão.
 * @param confirm  mostrar aviso de confirmação (quando não houve card voando para confirmar)
 */
export async function commitDecision(groupId, decision, { confirm = false } = {}) {
  const group = groupById(groupId);
  if (!group) return;
  state.decisions.set(group.id, decision);
  store.set(KEYS.hint, '1');
  emit('deck');
  announce(DECISIONS[decision].said(group.name));

  try {
    const result = await repo.swipe(group.id, decision);
    if (!result.matched && confirm) toast(DECISIONS[decision].said(group.name));
  } catch (error) {
    if (error.status === 409) { group.swiped = true; return; }           // já avaliado: fica fora do deck
    if (error.status === 404) { group.status = 'Inativo'; toast(error.message); return; }
    state.decisions.delete(group.id);
    toast(errorMessage(error));
    emit('swipe:reverted', { groupId: group.id, decision });
  }
}
