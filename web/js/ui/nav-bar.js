/**
 * Título grande que vira barra compacta ao rolar (padrão iOS): o nome da tela
 * continua visível mesmo depois que o título grande sai de cena.
 */
import { esc } from '../core/format.js';

export const navBar = (title) => `<div class="nav-bar" aria-hidden="true"><span class="nav-title">${esc(title)}</span></div>`;

export function bindNavBar(view) {
  const bar = view.querySelector('.nav-bar');
  const scroller = view.querySelector('.view-scroll');
  let compact = false;
  const update = () => {
    const next = scroller.scrollTop > 40;
    if (next !== compact) bar.classList.toggle('is-compact', (compact = next));
  };
  scroller.addEventListener('scroll', update, { passive: true });
  return {
    setTitle(text) { bar.querySelector('.nav-title').textContent = text; },
  };
}
