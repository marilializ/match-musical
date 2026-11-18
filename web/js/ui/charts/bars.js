/**
 * Barras horizontais de uma série (nomes longos ficam legíveis no eixo vertical).
 * Uma barra por categoria, valor escrito na ponta; a linha inteira é a área da dica.
 */
import { esc } from '../../core/format.js';
import { fmtAuto, escala, larguraTexto, barraHorizontal, vazio } from './kit.js';

const BARRA = 14;
const LINHA = 28;

/**
 * @param linhas [{ rotulo, valor }]
 * @param opcoes { unidade: "músicos", serie: "s1", destaque: Set de rótulos (ex.: moda) }
 */
export function barras(linhas, largura, { unidade = '', serie = 's1', destaque = new Set() } = {}) {
  if (!linhas.length) return vazio(60, 'Sem dados com esse filtro.');
  const rotulos = Math.min(Math.max(...linhas.map((l) => larguraTexto(l.rotulo))), largura * 0.4);
  const x0 = rotulos + 12;
  const plot = Math.max(largura - x0 - 36, 40);
  const { topo, marcas } = escala(Math.max(...linhas.map((l) => l.valor)));
  const altura = linhas.length * LINHA + 24;
  const px = (v) => (plot * v) / topo;

  const grade = marcas.map((m) => `
    <line class="grid" x1="${x0 + px(m)}" x2="${x0 + px(m)}" y1="0" y2="${altura - 22}"/>
    <text x="${x0 + px(m)}" y="${altura - 6}" text-anchor="middle">${fmtAuto(m)}</text>`).join('');

  const barrasSvg = linhas.map((l, i) => {
    const y = i * LINHA + (LINHA - BARRA) / 2;
    const w = px(l.valor);
    const forte = destaque.has(l.rotulo);
    return `<g class="hover-group">
      <text x="${x0 - 8}" y="${y + BARRA - 2}" text-anchor="end" class="${forte ? 't-strong' : ''}">${esc(l.rotulo)}</text>
      <path class="mark ${serie}" d="${barraHorizontal(x0, y, w, BARRA)}"/>
      <text x="${x0 + w + 6}" y="${y + BARRA - 2}">${fmtAuto(l.valor)}</text>
      <rect class="hit" x="0" y="${i * LINHA}" width="${largura}" height="${LINHA}" data-tip="${esc(`${l.rotulo}: ${fmtAuto(l.valor)} ${unidade}`.trim())}"/>
    </g>`;
  }).join('');

  return `<svg height="${altura}" role="img" aria-label="Barras: ${esc(linhas.map((l) => `${l.rotulo} ${fmtAuto(l.valor)}`).join(', '))}">
    ${grade}<line class="axis" x1="${x0}" x2="${x0}" y1="0" y2="${altura - 22}"/>${barrasSvg}</svg>`;
}
