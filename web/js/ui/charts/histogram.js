/**
 * Histograma de valor discreto: uma barra por valor, de min a max (os ausentes
 * entram com zero). Guias verticais marcam a média e a mediana.
 */
import { esc } from '../../core/format.js';
import { fmt, fmtAuto, escala, larguraTexto, barraVertical, vazio } from './kit.js';

/** @param frequencias [{ valor, qtd }] ordenadas; unidade: "mensagens"; contagem: "matches" */
export function histograma(frequencias, largura, { media, mediana, unidade, contagem }) {
  if (!frequencias.length) return vazio(60, 'Nenhum match com esse filtro.');
  const altura = 220;
  const minimo = frequencias[0].valor;
  const maximo = frequencias[frequencias.length - 1].valor;
  const classes = Array.from({ length: maximo - minimo + 1 }, (_, i) => minimo + i);
  const qtd = new Map(frequencias.map((f) => [f.valor, f.qtd]));
  const { topo, marcas } = escala(Math.max(...frequencias.map((f) => f.qtd)));
  const x0 = larguraTexto(fmtAuto(topo)) + 10;
  const direita = largura - 4;
  const base = altura - 24;
  const alto = base - 8;
  const faixa = (direita - x0) / classes.length;
  const py = (v) => base - (alto * v) / topo;
  const centro = (v) => x0 + (v - minimo + 0.5) * faixa;

  const grade = marcas.map((m) => `<line class="grid" x1="${x0}" x2="${direita}" y1="${py(m)}" y2="${py(m)}"/>
    <text x="${x0 - 6}" y="${py(m) + 4}" text-anchor="end">${fmtAuto(m)}</text>`).join('');

  const barras = classes.map((v, i) => {
    const q = qtd.get(v) ?? 0;
    const x = x0 + i * faixa + 1;
    const w = Math.max(faixa - 2, 1); // 2px de fundo entre barras
    return `<g class="hover-group"><path class="mark s1" d="${barraVertical(x, base, w, (alto * q) / topo)}"/>
      <text x="${x + w / 2}" y="${altura - 6}" text-anchor="middle">${v}</text>
      <rect class="hit" x="${x - 1}" y="0" width="${faixa}" height="${base}" data-tip="${esc(`${v} ${unidade}: ${q} ${contagem}`)}"/></g>`;
  }).join('');

  const guia = (valor, classe, nome) => (valor == null ? '' : `<g class="hover-group">
    <line class="rule ${classe}" x1="${centro(valor)}" x2="${centro(valor)}" y1="0" y2="${base}"/>
    <rect class="hit" x="${centro(valor) - 5}" y="0" width="10" height="${base}" data-tip="${esc(`${nome}: ${fmt(valor, 2)} ${unidade}`)}"/></g>`);

  return `<svg height="${altura}" role="img" aria-label="Histograma de ${esc(unidade)} por ${esc(contagem)}">
    ${grade}${barras}<line class="axis" x1="${x0}" x2="${direita}" y1="${base}" y2="${base}"/>
    ${guia(media, 's2', 'Média')}${guia(mediana, 's3', 'Mediana')}</svg>`;
}
