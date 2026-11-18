/**
 * Dispersão: um ponto por item (x inteiro). Pontos de mesmo x ganham um
 * deslocamento fixo para não se esconderem; anel na cor do fundo separa os
 * sobrepostos. A reta tracejada é a tendência (mínimos quadrados).
 */
import { esc } from '../../core/format.js';
import { fmtAuto, escala, larguraTexto, vazio } from './kit.js';

function tendencia(pontos) {
  const n = pontos.length;
  if (n < 2) return null;
  const mx = pontos.reduce((s, p) => s + p.x, 0) / n;
  const my = pontos.reduce((s, p) => s + p.y, 0) / n;
  let sxy = 0, sxx = 0;
  for (const p of pontos) { sxy += (p.x - mx) * (p.y - my); sxx += (p.x - mx) ** 2; }
  if (!sxx) return null;
  const b = sxy / sxx;
  return { a: my - b * mx, b };
}

/** @param pontos [{ id, nome, x, y }]; eixos: { x: "instrumentos", y: "anos" } */
export function dispersao(pontos, largura, { eixoX, eixoY, dica }) {
  if (!pontos.length) return vazio(60, 'Nenhum músico com esse filtro.');
  const altura = 240;
  const maxX = Math.max(1, ...pontos.map((p) => p.x));
  const { topo, marcas } = escala(Math.max(...pontos.map((p) => p.y)));
  const x0 = larguraTexto(fmtAuto(topo)) + 10;
  const direita = largura - 8;
  const base = altura - 40;
  const alto = base - 8;
  const px = (x) => x0 + ((x - 0.5) / maxX) * (direita - x0);
  const py = (y) => base - (alto * y) / topo;

  const grade = marcas.map((m) => `<line class="grid" x1="${x0}" x2="${direita}" y1="${py(m)}" y2="${py(m)}"/>
    <text x="${x0 - 6}" y="${py(m) + 4}" text-anchor="end">${fmtAuto(m)}</text>`).join('');
  const eixo = Array.from({ length: maxX }, (_, i) => `<text x="${px(i + 1)}" y="${base + 16}" text-anchor="middle">${i + 1}</text>`).join('');

  const reta = tendencia(pontos);
  const tracejado = reta ? `<line class="rule s2" x1="${px(0.5)}" y1="${py(reta.a + reta.b * 0.5)}" x2="${px(maxX + 0.5)}" y2="${py(reta.a + reta.b * (maxX + 0.5))}"/>` : '';

  const marcadores = pontos.map((p) => {
    const deslocamento = (((p.id * 37) % 11) - 5) / 5 * 0.18;
    const cx = px(p.x + deslocamento), cy = py(p.y);
    return `<g class="hover-group"><circle class="dot s1" cx="${cx}" cy="${cy}" r="5"/>
      <circle class="hit" cx="${cx}" cy="${cy}" r="11" data-tip="${esc(dica(p))}"/></g>`;
  }).join('');

  return `<svg height="${altura}" role="img" aria-label="Dispersão de ${esc(eixoX)} por ${esc(eixoY)}">
    <defs><clipPath id="area-dispersao"><rect x="${x0}" y="0" width="${direita - x0}" height="${base}"/></clipPath></defs>
    ${grade}<line class="axis" x1="${x0}" x2="${direita}" y1="${base}" y2="${base}"/>${eixo}
    <text x="${(x0 + direita) / 2}" y="${altura - 4}" text-anchor="middle">${esc(eixoX)}</text>
    <g clip-path="url(#area-dispersao)">${tracejado}</g>${marcadores}</svg>`;
}
