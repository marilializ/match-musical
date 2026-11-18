/**
 * Rosca (distribuição percentual). Fatias separadas por 2px da cor do fundo,
 * rótulo direto ao lado (nome, quantidade e %), total no centro.
 */
import { esc } from '../../core/format.js';
import { fmt, larguraTexto, vazio } from './kit.js';

/** @param fatias [{ rotulo, valor, serie: "s1" | "s2" | "s3" }] — a série segue a entidade, não a posição */
export function rosca(fatias, largura, { unidade = '' } = {}) {
  const total = fatias.reduce((s, f) => s + f.valor, 0);
  if (!total) return vazio(60, 'Sem dados no período.');
  const altura = 200;
  const raio = 84;
  const espessura = 26;
  const cx = raio + 4;
  const cy = altura / 2;
  let angulo = -Math.PI / 2;
  const ponto = (a, r) => `${cx + r * Math.cos(a)},${cy + r * Math.sin(a)}`;

  const arcos = fatias.map((f) => {
    const fatia = (f.valor / total) * Math.PI * 2;
    const inicio = angulo;
    const fim = angulo + fatia;
    angulo = fim;
    const grande = fatia > Math.PI ? 1 : 0;
    const externo = raio, interno = raio - espessura;
    const d = fatia >= Math.PI * 2 - 1e-6
      ? `M${cx - externo},${cy}a${externo},${externo} 0 1,0 ${externo * 2},0a${externo},${externo} 0 1,0 ${-externo * 2},0Z`
      : `M${ponto(inicio, externo)}A${externo},${externo} 0 ${grande} 1 ${ponto(fim, externo)}L${ponto(fim, interno)}A${interno},${interno} 0 ${grande} 0 ${ponto(inicio, interno)}Z`;
    const pct = fmt((100 * f.valor) / total, 1);
    return `<g class="hover-group"><path class="slice ${f.serie}" d="${d}" stroke="var(--color-surface)" stroke-width="2"
      data-tip="${esc(`${f.rotulo}: ${fmt(f.valor)} ${unidade} (${pct}%)`)}"/></g>`;
  }).join('');

  const xRotulo = cx + raio + 24;
  const rotulos = fatias.map((f, i) => {
    const y = cy - ((fatias.length - 1) * 36) / 2 + i * 36;
    return `<g><rect class="${f.serie}" x="${xRotulo}" y="${y - 10}" width="12" height="12" rx="3"/>
      <text x="${xRotulo + 20}" y="${y}" class="t-strong">${esc(f.rotulo)}</text>
      <text x="${xRotulo + 20}" y="${y + 16}">${fmt(f.valor)} · ${fmt((100 * f.valor) / total, 1)}%</text></g>`;
  }).join('');

  // Largura do conjunto (rosca + rótulos): o SVG fica desse tamanho e centralizado no cartão
  const maiorRotulo = Math.max(...fatias.map((f) => Math.max(larguraTexto(f.rotulo, 12) + 8, larguraTexto(`${fmt(f.valor)} · 00,0%`, 12))));
  const conteudo = Math.min(largura, Math.ceil(xRotulo + 20 + maiorRotulo + 4));
  return `<svg class="is-centered" width="${conteudo}" height="${altura}" role="img" aria-label="${esc(fatias.map((f) => `${f.rotulo} ${f.valor}`).join(', '))}">
    ${arcos}
    <text x="${cx}" y="${cy + 2}" text-anchor="middle" class="t-strong" style="font-size:20px">${fmt(total)}</text>
    <text x="${cx}" y="${cy + 20}" text-anchor="middle">${esc(unidade)}</text>
    ${rotulos}</svg>`;
}
