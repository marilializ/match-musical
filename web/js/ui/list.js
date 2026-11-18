/** Lista agrupada estilo iOS: um container arredondado com linhas separadas por fios. */
import { esc } from '../core/format.js';
import { icon } from './icon.js';

/** Envolve linhas já renderizadas numa lista. compact = linhas com ícone pequeno em vez de avatar. */
export const list = (rows, { compact = false, className = '' } = {}) =>
  `<ul class="group${compact ? ' is-compact' : ''}${className ? ` ${className}` : ''}">${rows.map((row) => `<li>${row}</li>`).join('')}</ul>`;

/**
 * Uma linha. Com `attrs` (ex.: data-open-group="3") vira <button>; sem, é estática.
 * title/subtitle/subtitle2 são texto (escapados). leading/trailing são HTML.
 */
export function row({ leading = '', title = '', subtitle = '', subtitle2 = '', subtitleHtml = '', trailing = '', chevron = false, attrs = '' } = {}) {
  const tag = attrs ? 'button' : 'div';
  const type = tag === 'button' ? ' type="button"' : '';
  const subs = [
    subtitleHtml ? `<span class="row-sub">${subtitleHtml}</span>` : subtitle ? `<span class="row-sub">${esc(subtitle)}</span>` : '',
    subtitle2 ? `<span class="row-sub">${esc(subtitle2)}</span>` : '',
  ].join('');
  const trail = trailing || chevron ? `<span class="row-trail">${trailing}${chevron ? icon('chevron', 'chev') : ''}</span>` : '';
  return `<${tag} class="row"${type}${attrs ? ` ${attrs}` : ''}>${leading}<span class="row-main"><span class="row-title">${esc(title)}</span>${subs}</span>${trail}</${tag}>`;
}

/** Ícone colorido quadrado (como em Ajustes). tint = qualquer cor ou var(--token). */
export const rowIcon = (name, tint) => `<span class="row-icon" style="--tint:${tint}">${icon(name)}</span>`;

/** Esqueleto enquanto a lista carrega: a forma do conteúdo aparece antes dele. */
export const skeletonList = (count = 3) => `<div class="group" aria-hidden="true">${'<div class="skeleton-row"></div>'.repeat(count)}</div>`;
