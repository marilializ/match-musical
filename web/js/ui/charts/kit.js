/**
 * Kit comum dos gráficos SVG: números em pt-BR, escala "redonda", medida de
 * texto, dica que segue o mouse e redesenho quando o cartão muda de largura.
 * Os gráficos são funções puras: recebem dados + largura e devolvem SVG.
 */
import { esc } from '../../core/format.js';

const formatos = new Map();
/** Número em pt-BR com "casas" decimais; null vira "–". */
export function fmt(valor, casas = 0) {
  if (valor == null || Number.isNaN(Number(valor))) return '–';
  if (!formatos.has(casas)) formatos.set(casas, new Intl.NumberFormat('pt-BR', { minimumFractionDigits: casas, maximumFractionDigits: casas }));
  return formatos.get(casas).format(Number(valor));
}
/** Inteiro sem casas; fracionário com 1 casa. */
export const fmtAuto = (valor) => fmt(valor, Number.isInteger(Number(valor)) ? 0 : 1);

/** Topo da escala arredondado (1, 2, 2,5, 5 × 10^k) e as marcas do eixo. */
export function escala(maximo) {
  let topo = 1;
  if (maximo > 0) {
    const potencia = 10 ** Math.floor(Math.log10(maximo));
    topo = [1, 2, 2.5, 5, 10].map((p) => p * potencia).find((v) => v >= maximo) ?? 10 * potencia;
  }
  const partes = topo <= 5 && Number.isInteger(topo) ? topo : Number.isInteger(topo / 4) ? 4 : 5;
  return { topo, marcas: Array.from({ length: partes + 1 }, (_, i) => (topo * i) / partes) };
}

let tela;
/** Largura aproximada de um texto (canvas), para reservar espaço a rótulos. */
export function larguraTexto(texto, tamanho = 12) {
  tela ??= document.createElement('canvas').getContext('2d');
  tela.font = `${tamanho}px Inter, system-ui, sans-serif`;
  return tela.measureText(String(texto)).width;
}

/** Barra horizontal presa no eixo à esquerda, com a ponta direita arredondada (4px). */
export function barraHorizontal(x, y, w, h, r = 4) {
  if (w <= 0) return '';
  const raio = Math.min(r, w, h / 2);
  return `M${x},${y}H${x + w - raio}Q${x + w},${y} ${x + w},${y + raio}V${y + h - raio}Q${x + w},${y + h} ${x + w - raio},${y + h}H${x}Z`;
}
/** Barra vertical presa na base, com o topo arredondado (4px). */
export function barraVertical(x, base, w, h, r = 4) {
  if (h <= 0) return '';
  const raio = Math.min(r, h, w / 2);
  const y = base - h;
  return `M${x},${base}V${y + raio}Q${x},${y} ${x + raio},${y}H${x + w - raio}Q${x + w},${y} ${x + w},${y + raio}V${base}Z`;
}

export const vazio = (altura, texto) =>
  `<svg height="${altura}" role="img" aria-label="${esc(texto)}"><text x="0" y="24">${esc(texto)}</text></svg>`;

/** Legenda HTML (quadradinho ou traço + nome). itens: [{ nome, cor, linha? }] */
export const legenda = (itens) =>
  `<div class="chart-legend">${itens.map((i) => `<span><i class="${i.linha ? 'is-line' : ''}" style="--swatch:${i.cor}"></i>${esc(i.nome)}</span>`).join('')}</div>`;

/**
 * Liga um cartão a um desenho: chama desenhar(largura) agora e de novo sempre
 * que a largura mudar. A dica mostra o data-tip do elemento sob o mouse.
 */
export function montar(corpo, desenhar) {
  const dica = document.createElement('div');
  dica.className = 'chart-tip';
  dica.setAttribute('aria-hidden', 'true');
  let largura = 0;
  const redesenhar = () => {
    largura = corpo.clientWidth;
    if (largura <= 0) return;
    corpo.innerHTML = desenhar(largura);
    corpo.append(dica);
  };
  new ResizeObserver(() => { if (corpo.clientWidth !== largura) redesenhar(); }).observe(corpo);
  corpo.addEventListener('pointermove', (event) => {
    const alvo = event.target.closest?.('[data-tip]');
    if (!alvo) { dica.classList.remove('is-visible'); return; }
    dica.textContent = alvo.dataset.tip;
    const caixa = corpo.getBoundingClientRect();
    const x = Math.min(event.clientX - caixa.left + 12, caixa.width - dica.offsetWidth);
    dica.style.left = `${Math.max(0, x)}px`;
    dica.style.top = `${event.clientY - caixa.top - dica.offsetHeight - 10}px`;
    dica.classList.add('is-visible');
  });
  corpo.addEventListener('pointerleave', () => dica.classList.remove('is-visible'));
  redesenhar();
  return { redesenhar };
}
