/**
 * Linhas no tempo (mesmo eixo, mesma unidade). Passando o mouse, a coluna
 * ganha a guia vertical e a dica mostra todas as séries daquele ponto.
 */
import { esc } from '../../core/format.js';
import { fmtAuto, escala, larguraTexto, vazio } from './kit.js';

/**
 * @param pontos [{ rotulo, valores: [n, n] }]
 * @param series [{ nome, classe: "s1" }]
 */
export function linhas(pontos, largura, series) {
  if (!pontos.length) return vazio(60, 'Sem dados no período.');
  const altura = 220;
  const { topo, marcas } = escala(Math.max(...pontos.flatMap((p) => p.valores)));
  const x0 = larguraTexto(fmtAuto(topo)) + 10;
  const direita = largura - 8;
  const base = altura - 24;
  const alto = base - 8;
  const n = pontos.length;
  const passo = n > 1 ? (direita - x0 - 8) / (n - 1) : 0;
  const px = (i) => (n > 1 ? x0 + 4 + i * passo : (x0 + direita) / 2);
  const py = (v) => base - (alto * v) / topo;

  const grade = marcas.map((m) => `<line class="grid" x1="${x0}" x2="${direita}" y1="${py(m)}" y2="${py(m)}"/>
    <text x="${x0 - 6}" y="${py(m) + 4}" text-anchor="end">${fmtAuto(m)}</text>`).join('');

  const salto = Math.max(1, Math.ceil((larguraTexto('00/00') + 12) / Math.max(passo, 1)));
  const eixoX = pontos.map((p, i) => (i % salto === 0
    ? `<text x="${px(i)}" y="${altura - 6}" text-anchor="middle">${esc(p.rotulo)}</text>` : '')).join('');

  const tracos = series.map((s, k) => {
    const d = pontos.map((p, i) => `${i ? 'L' : 'M'}${px(i)},${py(p.valores[k])}`).join('');
    return `<path class="line ${s.classe}" d="${d}"/>`;
  }).join('');

  const colunas = pontos.map((p, i) => {
    const meia = Math.max(passo / 2, 8);
    const dica = `Semana de ${p.rotulo}: ${series.map((s, k) => `${fmtAuto(p.valores[k])} ${s.nome}`).join(' · ')}`;
    const marcadores = series.map((s, k) => `<circle class="dot ${s.classe}" cx="${px(i)}" cy="${py(p.valores[k])}" r="4"/>`).join('');
    return `<g class="hover-group">
      <line class="crosshair" x1="${px(i)}" x2="${px(i)}" y1="0" y2="${base}"/>${marcadores}
      <rect class="hit" x="${px(i) - meia}" y="0" width="${meia * 2}" height="${base}" data-tip="${esc(dica)}"/></g>`;
  }).join('');

  return `<svg height="${altura}" role="img" aria-label="Linhas por semana">
    ${grade}<line class="axis" x1="${x0}" x2="${direita}" y1="${base}" y2="${base}"/>${eixoX}${tracos}${colunas}</svg>`;
}
