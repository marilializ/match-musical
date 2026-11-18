import { DECISIONS } from '../core/domain.js';
import { icon } from './icon.js';

/**
 * Botão redondo de decisão (passar / super like / curtir). O mesmo ícone e a mesma cor
 * no deck e na sheet do grupo: um significado, um visual.
 * @param attr  atributo que carrega a decisão (o dono do botão escuta esse atributo)
 */
export function actionButton(decision, attr = 'data-decision') {
  const info = DECISIONS[decision];
  // o atalho aparece no hover (title) em vez de ocupar a tela com uma legenda
  return `<button class="action action-${info.tone}" type="button" ${attr}="${decision}" aria-label="${info.label}" aria-keyshortcuts="${info.key}" title="${info.label} (${info.keyLabel})">${icon(info.icon)}</button>`;
}
