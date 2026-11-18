/**
 * Calendário mensal: datas sem fuso (AAAA-MM-DD) e a grade de um mês.
 * Puro: devolve HTML e marca classes; quem usa (ex.: <sync-date-range>) cuida
 * do estado e do teclado. Semana começa no domingo, como no calendário brasileiro.
 */
import { esc } from '../core/format.js';

const dois = (n) => String(n).padStart(2, '0');
/** Date local → "AAAA-MM-DD" (sem toISOString, que converte para UTC e pode mudar o dia). */
export const iso = (data) => `${data.getFullYear()}-${dois(data.getMonth() + 1)}-${dois(data.getDate())}`;
export const deIso = (texto) => { const [a, m, d] = texto.split('-').map(Number); return new Date(a, m - 1, d); };
export const hoje = () => iso(new Date());
export const somarDias = (texto, dias) => { const d = deIso(texto); return iso(new Date(d.getFullYear(), d.getMonth(), d.getDate() + dias)); };
/** Mesmo dia em outro mês (31/01 + 1 mês → 28/02). */
export function somarMeses(texto, meses) {
  const d = deIso(texto);
  const alvo = new Date(d.getFullYear(), d.getMonth() + meses, 1);
  const ultimo = new Date(alvo.getFullYear(), alvo.getMonth() + 1, 0).getDate();
  return iso(new Date(alvo.getFullYear(), alvo.getMonth(), Math.min(d.getDate(), ultimo)));
}
export const inicioDoMes = (texto) => `${texto.slice(0, 7)}-01`;
export const fimDoMes = (texto) => somarDias(inicioDoMes(somarMeses(inicioDoMes(texto), 1)), -1);
export const mesmoMes = (a, b) => a.slice(0, 7) === b.slice(0, 7);
/** "AAAA-MM-DD" → "DD/MM/AAAA" */
export const dataBR = (texto) => (texto ? `${texto.slice(8, 10)}/${texto.slice(5, 7)}/${texto.slice(0, 4)}` : '');

const NOME_MES = new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric' });
const NOME_DIA = new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
const SEMANA = [['D', 'domingo'], ['S', 'segunda'], ['T', 'terça'], ['Q', 'quarta'], ['Q', 'quinta'], ['S', 'sexta'], ['S', 'sábado']];
const maiuscula = (texto) => texto.charAt(0).toUpperCase() + texto.slice(1);

export const tituloDoMes = (mes) => maiuscula(NOME_MES.format(deIso(inicioDoMes(mes))));

/**
 * Grade de 6 semanas do mês de "mes". O dia "foco" é o único com tabindex=0
 * (as setas movem o foco entre os dias, Tab sai da grade).
 */
export function grade({ mes, foco }) {
  const primeiro = deIso(inicioDoMes(mes));
  const inicio = somarDias(inicioDoMes(mes), -primeiro.getDay());
  const hojeIso = hoje();
  const semanas = Array.from({ length: 6 }, (_, s) => `<tr>${Array.from({ length: 7 }, (__, d) => {
    const dia = somarDias(inicio, s * 7 + d);
    const classes = ['cal-cell', mesmoMes(dia, mes) ? '' : 'is-outside', dia === hojeIso ? 'is-today' : ''].filter(Boolean).join(' ');
    return `<td class="${classes}" role="gridcell" aria-selected="false">
      <button type="button" class="cal-day" data-dia="${dia}" tabindex="${dia === foco ? 0 : -1}"
        aria-label="${esc(NOME_DIA.format(deIso(dia)))}${dia === hojeIso ? ', hoje' : ''}">${Number(dia.slice(8))}</button></td>`;
  }).join('')}</tr>`).join('');
  return `<table class="cal-grid" role="grid" aria-label="${esc(tituloDoMes(mes))}">
    <thead><tr>${SEMANA.map(([letra, nome]) => `<th scope="col" abbr="${nome}"><span aria-hidden="true">${letra}</span></th>`).join('')}</tr></thead>
    <tbody>${semanas}</tbody></table>`;
}

/**
 * Pinta o intervalo [inicio, fim] numa grade já desenhada (sem redesenhar):
 * usado para a escolha e para a prévia enquanto o mouse passa pelos dias.
 */
export function marcarIntervalo(raiz, inicio, fim) {
  const [a, b] = inicio && fim && fim < inicio ? [fim, inicio] : [inicio, fim];
  raiz.querySelectorAll('.cal-cell').forEach((celula) => {
    const dia = celula.firstElementChild.dataset.dia;
    const ponta = dia === a || dia === b;
    celula.classList.toggle('is-start', Boolean(a) && dia === a);
    celula.classList.toggle('is-end', Boolean(b) && dia === b);
    celula.classList.toggle('in-range', Boolean(a && b) && dia > a && dia < b);
    celula.classList.toggle('has-range', Boolean(a && b && a !== b) && ponta);
    celula.setAttribute('aria-selected', String(Boolean(a) && ((b && dia >= a && dia <= b) || dia === a)));
  });
}
