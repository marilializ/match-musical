/**
 * Tela Painel: o dashboard da Etapa 6 na web.
 *   Visão geral → indicadores e os 6 gráficos (com filtros)
 *   Consultas   → as 12 consultas das Etapas 3 e 4, com parâmetros
 * Tudo vem do banco pelos mesmos SELECTs do dashboard Swing (DashboardDAO).
 */
import { state } from '../../core/state.js';
import { on } from '../../core/events.js';
import { navBar, bindNavBar } from '../../ui/nav-bar.js';
import { segmented, selectSegment } from '../../ui/segmented.js';
import { createOverview } from './overview.js';
import { createQueries } from './queries.js';

let view;
let aba = 'geral';
let overview;
let queries;
let desatualizado = false; // um swipe/conversão mudou o banco desde o último carregamento

export function init(section) {
  view = section;
  view.innerHTML = `
    ${navBar('Painel')}
    <div class="view-scroll is-wide">
      <div class="painel-head">
        <h1 class="large-title">Painel</h1>
        ${segmented({ name: 'painel', label: 'O que ver', options: [['geral', 'Visão geral'], ['consultas', 'Consultas']], selected: aba })}
      </div>
      <div data-geral></div>
      <div data-consultas hidden></div>
    </div>`;
  bindNavBar(view);
  overview = createOverview(view.querySelector('[data-geral]'));
  queries = createQueries(view.querySelector('[data-consultas]'));

  view.querySelector('[data-segmented="painel"]').addEventListener('click', (event) => {
    const botao = event.target.closest('[data-seg]');
    if (!botao) return;
    aba = botao.dataset.seg;
    selectSegment(botao.parentElement, aba);
    view.querySelector('[data-geral]').hidden = aba !== 'geral';
    view.querySelector('[data-consultas]').hidden = aba !== 'consultas';
    mostrar();
  });
  on('data', () => {
    desatualizado = true;
    if (state.route === 'painel') mostrar();
  });
}

export function onShow() { mostrar(); }

/** Carrega a aba visível; recarrega se o banco mudou desde a última vez. */
function mostrar() {
  if (!state.data) return; // espera o primeiro carregamento (sabe se há banco)
  if (aba === 'geral' && (!overview.carregado || desatualizado)) { desatualizado = false; overview.carregar(); }
  if (aba === 'consultas' && !queries.carregado) queries.carregar();
}
