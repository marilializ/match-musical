/** Ícone do sprite SVG de index.html (#i-<nome>). Decorativo: o texto/aria-label ao lado dá o sentido. */
export const icon = (name, className = '') =>
  `<svg class="icon${className ? ` ${className}` : ''}" aria-hidden="true"><use href="#i-${name}"/></svg>`;
