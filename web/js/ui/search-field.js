import { esc } from '../core/format.js';
import { icon } from './icon.js';

/** Campo de busca com lupa. O rótulo existe para leitor de tela; o placeholder é o exemplo. */
export const searchField = ({ id, label, placeholder, value = '', enterkeyhint = 'search' }) => `
  <label class="search">
    ${icon('search')}
    <span class="sr-only">${esc(label)}</span>
    <input id="${esc(id)}" type="search" placeholder="${esc(placeholder)}" value="${esc(value)}" autocomplete="off" enterkeyhint="${enterkeyhint}" maxlength="100">
  </label>`;
