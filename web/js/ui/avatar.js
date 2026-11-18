/**
 * Identidade visual gerada a partir do id (o banco não tem fotos): cada grupo/pessoa
 * recebe sempre o mesmo trio de cores, usado no avatar, no card e na capa.
 */
import { esc, initials } from '../core/format.js';

const PALETTES = [
  ['#FF5F6D', '#7B2FF7', '#2A1450'],
  ['#FF9A44', '#FC5C7D', '#3B1235'],
  ['#36D1DC', '#5B86E5', '#13224A'],
  ['#F7971E', '#E94057', '#3A0F1E'],
  ['#C471F5', '#FA71CD', '#2B0F3A'],
  ['#11998E', '#38EF7D', '#0B2A22'],
  ['#FC466B', '#3F5EFB', '#151A4A'],
  ['#FDC830', '#F37335', '#3A1A08'],
];

/** Variáveis CSS --c1/--c2/--c3 para um id. */
export function palette(seed) {
  const [c1, c2, c3] = PALETTES[Math.abs(Number(seed) || 0) % PALETTES.length];
  return `--c1:${c1};--c2:${c2};--c3:${c3}`;
}

/**
 * @param {number|null} seed  id usado para a cor (null = cores da marca, para "você")
 * @param {string} name       nome de onde saem as iniciais
 * @param {'sm'|'md'|'lg'} size
 */
export function avatar(seed, name, { size = 'sm', className = '' } = {}) {
  const classes = ['avatar', size !== 'sm' ? `avatar-${size}` : '', seed == null ? 'avatar-me' : '', className].filter(Boolean).join(' ');
  const style = seed == null ? '' : ` style="${palette(seed)}"`;
  return `<span class="${classes}"${style} aria-hidden="true">${esc(initials(name))}</span>`;
}
