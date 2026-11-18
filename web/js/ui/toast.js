/**
 * Aviso curto no topo + anúncio para leitor de tela.
 * Usa a Popover API para ficar na top layer, acima de qualquer sheet aberta.
 */

const toastEl = () => document.getElementById('toast');
const announcerEl = () => document.getElementById('announcer');
let hideTimer = 0;
let removeTimer = 0;

/** Só para leitor de tela (sem nada visual). */
export function announce(text) {
  const el = announcerEl();
  el.textContent = '';
  setTimeout(() => { el.textContent = text; }, 40);
}

export function toast(text, { duration = 2800 } = {}) {
  const el = toastEl();
  el.textContent = text;
  announce(text);
  clearTimeout(hideTimer);
  clearTimeout(removeTimer);
  try {
    if (el.matches(':popover-open')) el.hidePopover(); // reabre para ficar acima de uma sheet recém-aberta
    el.showPopover();
  } catch { /* navegador sem popover: o elemento fixo já serve */ }
  el.classList.remove('is-visible');
  void el.offsetWidth; // reinicia a transição
  el.classList.add('is-visible');
  hideTimer = setTimeout(() => {
    el.classList.remove('is-visible');
    removeTimer = setTimeout(() => { try { el.hidePopover(); } catch { /* idem */ } }, 300);
  }, duration);
}
