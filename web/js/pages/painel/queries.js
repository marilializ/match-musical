/**
 * Painel → Consultas: as 12 consultas (8 da Etapa 3, 4 da Etapa 4).
 * Desktop: lista das 12 à esquerda (todas à vista, como uma barra lateral) e o
 * resultado à direita. Celular: a mesma escolha num seletor no topo.
 * Escolher a consulta ou o parâmetro já executa (sem botão a mais). A coluna que
 * responde a pergunta fica em destaque; na auditoria, cada linha tem a cor do
 * diagnóstico (e o diagnóstico escrito, para não depender só da cor).
 */
import { state, displayName } from '../../core/state.js';
import { esc, errorMessage, plural } from '../../core/format.js';
import * as repo from '../../core/repo.js';
import { emptyState } from '../../ui/empty.js';
import { tabela } from '../../ui/data-table.js';
import { pill } from '../../ui/pill.js';

const CLASSE_DIAGNOSTICO = { Consistente: 'is-ok', 'Match sem like mutuo': 'is-warn', 'Like mutuo sem match': 'is-bad' };
const ROTULO = { MUSICO: 'Músico', CATEGORIA: 'Categoria', INSTRUMENTO: 'Instrumento', DIAGNOSTICO: 'Diagnóstico' };
const ORIGENS = ['Etapa 3', 'Etapa 4'];

export function createQueries(root) {
  let catalogo = null;
  let numero = 1;
  let pedido = 0;

  const atual = () => catalogo.consultas.find((c) => c.numero === numero);
  const daOrigem = (origem) => catalogo.consultas.filter((c) => c.origem === origem);

  function estrutura() {
    root.innerHTML = `
      <div class="consultas">
        <nav class="query-nav" aria-label="Consultas">
          ${ORIGENS.map((origem) => `
            <h2 class="query-nav-title">${origem}</h2>
            ${daOrigem(origem).map((c) => `
              <button type="button" class="query-item" data-numero="${c.numero}">
                <span class="query-num">${c.numero}</span><span>${esc(c.titulo)}</span>
              </button>`).join('')}`).join('')}
        </nav>
        <div class="query-main">
          <select class="select query-picker" data-query aria-label="Consulta">
            ${ORIGENS.map((origem) => `<optgroup label="${origem}">${daOrigem(origem)
              .map((c) => `<option value="${c.numero}">${c.numero}. ${esc(c.titulo)}</option>`).join('')}</optgroup>`).join('')}
          </select>
          <header class="query-head">
            <p class="query-eyebrow" data-eyebrow></p>
            <h2 class="query-title" data-title></h2>
            <p class="query-question" data-question></p>
          </header>
          <div class="query-controls">
            <label class="query-param" data-param-wrap><span data-param-label></span><select class="select" data-param></select></label>
            <span class="query-count" data-status aria-live="polite"></span>
          </div>
          <div class="audit-legend" data-legend hidden>
            ${pill('Consistente', 'like')}${pill('Match sem like mútuo', 'warning')}${pill('Like mútuo sem match', 'nope')}
          </div>
          <div class="query-table" data-result></div>
          <details class="chart-data sql-box"><summary>Ver o SQL enviado ao banco</summary><pre><code data-sql></code></pre></details>
        </div>
      </div>`;
    root.querySelector('.query-nav').addEventListener('click', (event) => {
      const item = event.target.closest('[data-numero]');
      if (item) escolher(Number(item.dataset.numero));
    });
    root.querySelector('[data-query]').addEventListener('change', (event) => escolher(Number(event.target.value)));
    root.querySelector('[data-param]').addEventListener('change', executar);
  }

  function escolher(n) {
    if (n === numero && root.querySelector('[data-result]').childElementCount) return;
    numero = n;
    parametro();
    executar();
  }

  /** Cabeçalho e opções do parâmetro conforme a consulta: músico, categoria, instrumento ou diagnóstico. */
  function parametro() {
    const consulta = atual();
    root.querySelectorAll('[data-numero]').forEach((item) => {
      if (Number(item.dataset.numero) === numero) item.setAttribute('aria-current', 'true');
      else item.removeAttribute('aria-current');
    });
    root.querySelector('[data-query]').value = String(numero);
    root.querySelector('[data-eyebrow]').textContent = `Consulta ${consulta.numero} · ${consulta.origem}`;
    root.querySelector('[data-title]').textContent = consulta.titulo;
    root.querySelector('[data-question]').textContent = consulta.pergunta;
    root.querySelector('[data-legend]').hidden = consulta.parametro !== 'DIAGNOSTICO';
    root.querySelector('[data-sql]').textContent = consulta.sql;

    const { opcoes } = catalogo;
    const lista = {
      MUSICO: (state.data?.musicians ?? []).map((m) => [m.id, displayName(m)]),
      CATEGORIA: [['', 'Todas'], ...opcoes.categorias.map((c) => [c, c])],
      INSTRUMENTO: [['', 'Todos'], ...opcoes.instrumentos.map((i) => [i.id, i.nome])],
      DIAGNOSTICO: [['', 'Todos'], ...opcoes.diagnosticos.map((d) => [d, d])],
    }[consulta.parametro] ?? [];
    const select = root.querySelector('[data-param]');
    select.innerHTML = lista.map(([valor, texto]) => `<option value="${esc(valor)}">${esc(texto)}</option>`).join('');
    if (consulta.parametro === 'MUSICO' && state.userId) select.value = String(state.userId);
    root.querySelector('[data-param-wrap]').hidden = !lista.length;
    root.querySelector('[data-param-label]').textContent = ROTULO[consulta.parametro] ?? '';
  }

  async function executar() {
    const consulta = atual();
    const comParametro = !root.querySelector('[data-param-wrap]').hidden;
    const valor = comParametro ? root.querySelector('[data-param]').value : '';
    const meu = ++pedido;
    const area = root.querySelector('[data-result]');
    const status = root.querySelector('[data-status]');
    area.classList.add('is-stale');
    try {
      const resultado = await repo.executarConsulta(consulta.numero, valor);
      if (meu !== pedido) return;
      const colunaDiagnostico = resultado.colunas.indexOf('diagnostico');
      area.classList.remove('is-stale');
      area.innerHTML = resultado.linhas.length
        ? tabela(resultado.colunas, resultado.linhas, {
          destaque: consulta.destaque,
          classeDaLinha: (linha) => (colunaDiagnostico >= 0 ? CLASSE_DIAGNOSTICO[linha[colunaDiagnostico]] ?? '' : ''),
        })
        : emptyState({ icon: 'search', title: 'Nenhuma linha', text: 'Essa consulta não retornou resultados com esse parâmetro.' });
      status.innerHTML = `${plural(resultado.linhas.length, 'linha', 'linhas')}${consulta.destaque
        ? ` · em destaque: <span class="query-col">${esc(consulta.destaque)}</span>` : ''}`;
    } catch (error) {
      if (meu !== pedido) return;
      area.classList.remove('is-stale');
      status.textContent = '';
      area.innerHTML = emptyState({ icon: 'info', title: 'Não deu para executar', text: errorMessage(error) });
    }
  }

  async function carregar() {
    try {
      catalogo = await repo.consultas();
    } catch (error) {
      root.innerHTML = emptyState({
        icon: error.semBanco ? 'database' : 'info',
        title: error.semBanco ? 'Consultas indisponíveis' : 'Não deu para carregar as consultas',
        text: error.semBanco ? error.message : errorMessage(error),
      });
      return;
    }
    numero = catalogo.consultas[0]?.numero ?? 1;
    estrutura();
    parametro();
    executar();
  }

  return {
    carregar,
    get carregado() { return catalogo != null; },
  };
}
