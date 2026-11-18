/** Medidores: nível em barras de equalizador e percentual em anel. */
import { esc } from '../core/format.js';

/**
 * @param {number} step   degrau atual (1 a total)
 * @param {string} label  nome do nível, para leitor de tela
 */
export function levelMeter(step, total, label) {
  const bars = Array.from({ length: total }, (_, i) => `<span${i < step ? ' class="is-on"' : ''}></span>`).join('');
  return `<span class="level-meter" role="img" aria-label="${esc(`Nível ${label}, ${step} de ${total}`)}">${bars}</span>`;
}

/** Anel de 0 a 100. Sem valor, mostra o traço vazio e "—". */
export function progressRing(percent, label) {
  const value = percent == null ? null : Math.max(0, Math.min(100, Math.round(Number(percent))));
  return `
    <span class="ring" role="img" aria-label="${esc(value == null ? `${label}: indisponível` : `${label}: ${value}%`)}">
      <svg viewBox="0 0 64 64" aria-hidden="true">
        <circle class="ring-track" cx="32" cy="32" r="28"/>
        ${value == null ? '' : `<circle class="ring-value" cx="32" cy="32" r="28" pathLength="100" style="stroke-dashoffset:${100 - value}"/>`}
      </svg>
      <span class="ring-label" aria-hidden="true">${value == null ? '—' : `${value}%`}</span>
    </span>`;
}
