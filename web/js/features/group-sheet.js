/** Sheet de detalhes de um grupo. Abre do card, das vagas e dos matches. */
import { state, groupById } from '../core/state.js';
import { DECISIONS, DECISION_ORDER } from '../core/domain.js';
import { esc } from '../core/format.js';
import { openSheet } from '../ui/sheet.js';
import { icon } from '../ui/icon.js';
import { palette } from '../ui/avatar.js';
import { actionButton } from '../ui/action-button.js';
import { coverArt, typePill } from '../ui/group-art.js';
import { groupDetails, fillMembers } from './group-details.js';
import { requestDecision } from './swipe.js';

/** Rodapé: os mesmos botões do deck, ou o que já foi decidido. */
function footFor(group) {
  const decided = state.decisions.get(group.id);
  if (decided || group.swiped) {
    return `<p class="detail-note">${icon('check')}${decided ? DECISIONS[decided].done : 'Você já avaliou este grupo'}</p>`;
  }
  if (group.status === 'Inativo') return '<p class="detail-note">Grupo inativo no momento</p>';
  return `<div class="detail-actions">${DECISION_ORDER.map((d) => actionButton(d, 'data-sheet-decision')).join('')}</div>`;
}

export function openGroupSheet(groupId) {
  const group = groupById(groupId);
  if (!group) return null;
  const body = `
    <div class="detail-cover" style="${palette(group.id)}">
      ${coverArt(group)}
      <div class="card-top">${typePill(group)}</div>
      <div class="card-info">
        <h2 class="card-name">${esc(group.name)}</h2>
        <p class="card-meta">${icon('pin')}${esc(group.city || 'Local não informado')}</p>
      </div>
    </div>
    ${groupDetails(group)}`;

  const sheet = openSheet({ label: group.name, body, foot: footFor(group), className: 'sheet-group', floatingClose: true });
  sheet.el.addEventListener('click', (event) => {
    const button = event.target.closest('[data-sheet-decision]');
    if (!button) return;
    sheet.close();
    requestDecision(group.id, button.dataset.sheetDecision);
  });
  fillMembers(sheet.el, group.id, () => sheet.isOpen);
  return sheet;
}
