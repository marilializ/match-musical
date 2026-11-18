/**
 * Tela Vagas.
 *   Para você → vw_recomendacao_vagas (gênero em comum + nível), com os 3 indicadores
 *   Todas     → todas as vagas abertas, com a compatibilidade de nível de cada uma
 * A mesma busca e os mesmos chips de nível valem para as duas abas.
 */
import { state, available } from '../../core/state.js';
import { on } from '../../core/events.js';
import { LEVELS, levelLabel } from '../../core/domain.js';
import { norm, plural, errorMessage } from '../../core/format.js';
import * as repo from '../../core/repo.js';
import { navBar, bindNavBar } from '../../ui/nav-bar.js';
import { segmented, selectSegment } from '../../ui/segmented.js';
import { searchField } from '../../ui/search-field.js';
import { chips } from '../../ui/chips.js';
import { list, skeletonList } from '../../ui/list.js';
import { emptyState } from '../../ui/empty.js';
import { vacancyRow } from '../../features/rows.js';
import { openGroupSheet } from '../../features/group-sheet.js';

const LEVEL_OPTIONS = [['', 'Qualquer nível'], ...LEVELS.map((level) => [level, levelLabel(level)])];

const local = {
  tab: 'para-voce',
  q: '',
  level: '',
  rec: null,        // { rows, indicator, stale } | { error }
  recKey: '',
  loading: false,
  timer: 0,
};

let view;
let content;
let levels;

export function init(section) {
  view = section;
  view.innerHTML = `
    ${navBar('Vagas')}
    <div class="view-scroll">
      <h1 class="large-title">Vagas</h1>
      ${segmented({ name: 'vagas', label: 'Quais vagas', options: [['para-voce', 'Para você'], ['todas', 'Todas']], selected: local.tab })}
      ${searchField({ id: 'vagas-q', label: 'Buscar vaga', placeholder: 'Instrumento ou função' })}
      <div data-levels></div>
      <div data-content></div>
    </div>`;
  bindNavBar(view);
  content = view.querySelector('[data-content]');
  levels = view.querySelector('[data-levels]');
  renderLevels();
  render();

  view.addEventListener('click', onClick);
  view.querySelector('#vagas-q').addEventListener('input', (event) => {
    local.q = event.target.value;
    ensure({ debounce: 300 });
  });
  on('data', () => {
    if (local.rec && !local.rec.error && !local.rec.unavailable) local.rec.stale = true; // vagas podem ter fechado
    if (state.route === 'vagas') ensure(); else render();
  });
  on('user', () => { local.rec = null; local.recKey = ''; render(); });
}

export function onShow() { ensure(); }

function onClick(event) {
  const target = event.target;
  const tab = target.closest('[data-segmented="vagas"] [data-seg]');
  if (tab) { local.tab = tab.dataset.seg; selectSegment(tab.parentElement, local.tab); ensure(); return; }
  const level = target.closest('[data-level]');
  if (level) { local.level = level.dataset.level; renderLevels(); ensure(); return; }
  const group = target.closest('[data-open-group]');
  if (group) { openGroupSheet(Number(group.dataset.openGroup)); return; }
  if (target.closest('[data-goto-all]')) {
    local.tab = 'todas';
    selectSegment(view.querySelector('[data-segmented="vagas"]'), 'todas');
    ensure();
    return;
  }
  if (target.closest('[data-clear]')) {
    local.q = '';
    local.level = '';
    view.querySelector('#vagas-q').value = '';
    renderLevels();
    ensure();
    return;
  }
  if (target.closest('[data-retry]')) { local.rec = null; local.recKey = ''; ensure(); }
}

const hasFilters = () => Boolean(local.q.trim() || local.level);
const clearButton = () => '<button class="btn btn-secondary" type="button" data-clear>Limpar busca</button>';

function renderLevels() {
  levels.innerHTML = chips({ label: 'Nível mínimo', options: LEVEL_OPTIONS, selected: local.level, attr: 'data-level' });
}

function render() {
  if (!state.data) { content.innerHTML = skeletonList(4); return; }
  content.innerHTML = local.tab === 'todas' ? renderAll() : renderForYou();
}

function renderAll() {
  const query = norm(local.q);
  const rows = state.data.vacancies.filter((v) =>
    (!query || norm(`${v.title} ${v.group} ${v.city}`).includes(query)) && (!local.level || v.level === local.level));
  if (!rows.length) {
    return emptyState({
      icon: 'search', title: 'Nenhuma vaga',
      text: hasFilters() ? 'Tente outro instrumento ou nível.' : 'Quando abrirem vagas, elas aparecem aqui.',
      actions: hasFilters() ? clearButton() : '',
    });
  }
  return `<p class="footnote list-count">${plural(rows.length, 'vaga aberta', 'vagas abertas')}</p>${list(rows.map((v) => vacancyRow(v)))}`;
}

function renderForYou() {
  const rec = local.rec;
  if (!available('vw_recomendacao_vagas') || rec?.unavailable) {
    return emptyState({
      icon: 'sparkles', title: 'Recomendações indisponíveis', text: 'Veja todas as vagas abertas.',
      actions: '<button class="btn btn-secondary" type="button" data-goto-all>Ver todas</button>',
    });
  }
  if (!rec) return `<div class="summary-skeleton" aria-hidden="true"></div>${skeletonList(4)}`;
  if (rec.error) {
    return emptyState({
      icon: 'info', title: 'Não deu para carregar', text: rec.error,
      actions: '<button class="btn btn-secondary" type="button" data-retry>Tentar de novo</button>',
    });
  }
  const { open = 0, compatible = 0, recommended = 0 } = rec.indicator;
  const summary = `
    <div class="summary">
      <p class="summary-main"><strong>${recommended}</strong> ${recommended === 1 ? 'vaga combina' : 'vagas combinam'} com você</p>
      <p class="summary-sub">Seu nível atende ${compatible} de ${plural(open, 'vaga aberta', 'vagas abertas')}</p>
    </div>`;
  if (!rec.rows.length) {
    return summary + emptyState({
      icon: 'sparkles',
      title: hasFilters() ? 'Nada com essa busca' : 'Nenhuma recomendação',
      text: hasFilters() ? 'Mude o instrumento ou o nível.' : 'Veja todas as vagas abertas.',
      actions: hasFilters() ? clearButton() : '<button class="btn btn-secondary" type="button" data-goto-all>Ver todas</button>',
    });
  }
  const count = hasFilters() ? `<p class="footnote list-count">${plural(rec.rows.length, 'resultado', 'resultados')}</p>` : '';
  return summary + count + list(rec.rows.map((v) => vacancyRow(v, { recommended: true })), { className: rec.stale ? 'is-stale' : '' });
}

/** Renderiza já o que tem e busca recomendações se a combinação de filtros mudou. */
function ensure({ debounce = 0 } = {}) {
  render();
  if (local.tab !== 'para-voce' || !state.data || !available('vw_recomendacao_vagas')) return;
  const key = `${state.userId}|${local.q.trim()}|${local.level}`;
  if (local.rec?.unavailable) return;
  if (local.recKey === key && (local.loading || (local.rec && !local.rec.stale))) return;
  clearTimeout(local.timer);
  local.timer = setTimeout(() => fetchRecommended(key), debounce);
}

async function fetchRecommended(key) {
  local.recKey = key;
  local.loading = true;
  if (local.rec?.error) local.rec = null;
  else if (local.rec) local.rec.stale = true; // mostra a lista anterior esmaecida enquanto busca
  render();
  let result;
  try {
    result = await repo.recommended({ funcao: local.q.trim(), nivel: local.level });
  } catch (error) {
    // 501 = view/função não instalada no banco: tentar de novo não adianta
    result = error.status === 501 ? { unavailable: true } : { error: errorMessage(error) };
  }
  if (local.recKey !== key) return; // chegou resposta de uma busca antiga
  local.loading = false;
  local.rec = result;
  render();
}
