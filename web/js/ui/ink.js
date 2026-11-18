/**
 * Tinta direcional nos botões redondos: grava em --ink-x/--ink-y o ponto onde o mouse
 * entrou (a cor nasce ali) e, ao sair, o ponto de saída (a cor recolhe para lá).
 * O desenho e o tempo ficam no CSS (.action::before); aqui é só a posição.
 * Toque não tem hover: só reage a mouse/caneta.
 */
const clamp = (value) => Math.min(100, Math.max(0, value));

function place(element, event) {
  const box = element.getBoundingClientRect();
  element.style.setProperty('--ink-x', `${clamp(((event.clientX - box.left) / box.width) * 100)}%`);
  element.style.setProperty('--ink-y', `${clamp(((event.clientY - box.top) / box.height) * 100)}%`);
}

export function initInk(selector = '.action') {
  const handle = (event) => {
    if (event.pointerType === 'touch') return;
    const element = event.target.closest?.(selector);
    if (element && !element.contains(event.relatedTarget)) place(element, event); // só ao cruzar a borda
  };
  document.addEventListener('pointerover', handle);
  document.addEventListener('pointerout', handle);
}
