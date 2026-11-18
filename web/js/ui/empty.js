import { esc } from '../core/format.js';
import { icon } from './icon.js';

/** Estado vazio: o que deveria estar aqui + no máximo uma ou duas ações (HTML). */
export const emptyState = ({ icon: iconName, title, text = '', actions = '' }) => `
  <div class="empty">
    <span class="empty-icon">${icon(iconName)}</span>
    <h2>${esc(title)}</h2>
    ${text ? `<p>${esc(text)}</p>` : ''}
    ${actions ? `<div class="empty-actions">${actions}</div>` : ''}
  </div>`;
