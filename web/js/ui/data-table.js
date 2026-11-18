/** Tabela de dados: os mesmos números do gráfico (ou de uma consulta), em texto. */
import { esc } from '../core/format.js';
import { fmtAuto } from './charts/kit.js';

/**
 * @param colunas  nomes das colunas
 * @param linhas   arrays de valores, na ordem das colunas
 * @param opcoes   destaque: nome da coluna realçada; classeDaLinha(linha) => '' | 'is-ok' | 'is-warn' | 'is-bad'
 */
export function tabela(colunas, linhas, { destaque = null, classeDaLinha = () => '' } = {}) {
  const cabecalho = colunas.map((c) => `<th scope="col"${c === destaque ? ' class="is-highlight"' : ''}>${esc(c)}</th>`).join('');
  const corpo = linhas.map((linha) => `<tr class="${classeDaLinha(linha)}">${linha.map((valor, i) => {
    const numero = typeof valor === 'number';
    const classes = [numero ? 'is-number' : '', colunas[i] === destaque ? 'is-highlight' : ''].filter(Boolean).join(' ');
    const texto = valor == null ? '–' : numero ? fmtAuto(valor) : valor;
    return `<td${classes ? ` class="${classes}"` : ''}>${esc(texto)}</td>`;
  }).join('')}</tr>`).join('');
  return `<div class="table-wrap"><table class="data-table"><thead><tr>${cabecalho}</tr></thead><tbody>${corpo}</tbody></table></div>`;
}
