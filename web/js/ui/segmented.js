/**
 * Segmented control. O "thumb" desliza até a opção escolhida (mola em CSS):
 * o movimento mostra de onde para onde a seleção foi.
 */
import { esc } from '../core/format.js';

/** @param options lista de [valor, rótulo] */
export function segmented({ name, label, options, selected }) {
  const index = Math.max(0, options.findIndex(([value]) => value === selected));
  return `
    <div class="segmented" data-segmented="${esc(name)}" role="tablist" aria-label="${esc(label)}" style="--seg-count:${options.length};--seg:${index}">
      <span class="seg-thumb" aria-hidden="true"></span>
      ${options.map(([value, text]) => `<button type="button" role="tab" data-seg="${esc(value)}" aria-selected="${value === selected}">${esc(text)}</button>`).join('')}
    </div>`;
}

/** Marca a opção escolhida num segmented já renderizado. */
export function selectSegment(root, value) {
  const buttons = [...root.querySelectorAll('[data-seg]')];
  buttons.forEach((button, index) => {
    const on = button.dataset.seg === value;
    button.setAttribute('aria-selected', String(on));
    if (on) root.style.setProperty('--seg', index);
  });
}
