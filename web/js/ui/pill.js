import { esc } from '../core/format.js';
import { MATCH_STATUS } from '../core/domain.js';
import { icon } from './icon.js';

/** Etiqueta curta. tone: '' | 'like' | 'super' | 'warning' | 'accent' */
export const pill = (text, tone = '', iconName = '') =>
  `<span class="pill${tone ? ` pill-${tone}` : ''}">${iconName ? icon(iconName) : ''}${esc(text)}</span>`;

/** Situação de um match ("Ativo", "No grupo"…). */
export function statusPill(status) {
  const info = MATCH_STATUS[status] ?? { label: status, tone: '' };
  return pill(info.label, info.tone);
}
