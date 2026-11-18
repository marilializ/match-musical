import { esc } from '../core/format.js';

/**
 * Grupo de chips de escolha única (filtro rápido).
 * @param options  lista de [valor, rótulo]
 * @param attr     atributo data-* que carrega o valor (ex.: 'data-level')
 */
export const chips = ({ label, options, selected, attr }) => `
  <div class="chips" role="radiogroup" aria-label="${esc(label)}">
    ${options.map(([value, text]) => `<button type="button" class="chip" role="radio" aria-checked="${value === selected}" ${attr}="${esc(value)}">${esc(text)}</button>`).join('')}
  </div>`;
