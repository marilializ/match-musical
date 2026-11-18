/** Pequenos utilitários de DOM e de layout responsivo. */

export const $ = (selector, root = document) => root.querySelector(selector);
export const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

/** Size classes (as mesmas de base.css). */
export const media = {
  regular: window.matchMedia('(min-width: 768px)'),
  wide: window.matchMedia('(min-width: 1200px)'),
};

export const isTyping = (target) => Boolean(target?.closest?.('input, textarea, select, [contenteditable="true"]'));
