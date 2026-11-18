/**
 * Tela Matches.
 *   Conexões  → os matches do perfil; tocar abre a sheet (entrar no grupo, histórico)
 *   Histórico → mudanças de situação (Log_Match), filtráveis pela nova situação
 */
import { state } from '../../core/state.js';
import { on } from '../../core/events.js';
import { MATCH_STATUS } from '../../core/domain.js';
import { errorMessage } from '../../core/format.js';
import * as repo from '../../core/repo.js';
import { navBar, bindNavBar } from '../../ui/nav-bar.js';
import { segmented, selectSegment } from '../../ui/segmented.js';
import { chips } from '../../ui/chips.js';
import { list, skeletonList } from '../../ui/list.js';
import { emptyState } from '../../ui/empty.js';
import { matchRow, logRow } from '../../features/rows.js';
import { openMatchSheet } from '../../features/match-sheet.js';

const STATUS_OPTIONS = [['', 'Todos'], ...Object.entries(MATCH_STATUS).map(([value, { label }]) => [value, label])];

const local = {
  tab: 'conexoes',
  status: '',
  log: null,     // { rows } | { error } | { unavailable }
  logKey: '',
};

let view;
let content;

export function init(section) {
  view = section;
  view.innerHTML = `
    ${navBar('Matches')}
    <div class="view-scroll">
      <h1 class="large-title">Matches</h1>
      ${segmented({ name: 'matches', label: 'O que mostrar', options: [['conexoes', 'Conexões'], ['historico', 'Histórico']], selected: local.tab })}
      <div data-content></div>
    </div>`;
  bindNavBar(view);
  content = view.querySelector('[data-content]');
  render();

  view.addEventListener('click', onClick);
  on('data', () => { local.logKey = ''; if (state.route === 'matches') ensure(); else render(); });
  on('matches', () => { local.logKey = ''; if (state.route === 'matches') ensure(); else render(); });
  on('user', () => { local.log = null; local.logKey = ''; render(); });
}

export function onShow() { ensure(); }

function onClick(event) {
  const target = event.target;
  const tab = target.closest('[data-segmented="matches"] [data-seg]');
  if (tab) { local.tab = tab.dataset.seg; selectSegment(tab.parentElement, local.tab); ensure(); return; }
  const status = target.closest('[data-log-status]');
  if (status) { local.status = status.dataset.logStatus; ensure(); return; }
  const match = target.closest('[data-open-match]');
  if (match) { openMatchSheet(Number(match.dataset.openMatch)); return; }
  if (target.closest('[data-retry]')) { local.log = null; local.logKey = ''; ensure(); }
}

function render() {
  if (!state.data) { content.innerHTML = skeletonList(3); return; }
  content.innerHTML = local.tab === 'conexoes' ? renderConnections() : renderHistory();
}

function renderConnections() {
  const matches = state.data.matches;
  if (!matches.length) {
    return emptyState({
      icon: 'heart', title: 'Nenhum match ainda', text: 'Quando um grupo curtir você de volta, ele aparece aqui.',
      actions: '<a class="btn btn-primary" href="#descobrir">Descobrir grupos</a>',
    });
  }
  return list(matches.map((match) => matchRow(match, { isNew: state.newMatches.has(match.id) })));
}

function renderHistory() {
  const log = local.log;
  if (log?.unavailable) return emptyState({ icon: 'info', title: 'Histórico indisponível', text: 'O banco ainda não registra as mudanças.' });
  const filter = chips({ label: 'Nova situação', options: STATUS_OPTIONS, selected: local.status, attr: 'data-log-status' });
  let body;
  if (!log) body = skeletonList(3);
  else if (log.error) body = emptyState({ icon: 'info', title: 'Não deu para carregar', text: log.error, actions: '<button class="btn btn-secondary" type="button" data-retry>Tentar de novo</button>' });
  else if (!log.rows.length) body = emptyState({ icon: 'refresh', title: local.status ? 'Nada com esse filtro' : 'Nada mudou ainda', text: 'Quando um match mudar de situação, aparece aqui.' });
  else {
    const byId = new Map(state.data.matches.map((match) => [match.id, match]));
    const unknown = (id) => ({ otherId: id, name: 'Match' });
    body = list(log.rows.map((entry) => logRow(entry, byId.get(entry.match) ?? unknown(entry.match))), { className: log.stale ? 'is-stale' : '' });
  }
  return filter + body;
}

function ensure() {
  render();
  if (local.tab !== 'historico' || !state.data) return;
  const key = `${state.userId}|${local.status}`;
  if (local.logKey === key) return;
  fetchLog(key);
}

async function fetchLog(key) {
  local.logKey = key;
  if (local.log?.rows) local.log.stale = true; else local.log = null;
  render();
  let result;
  try {
    const rows = await repo.matchLog({ situacao: local.status });
    result = rows === null ? { unavailable: true } : { rows };
  } catch (error) {
    result = { error: errorMessage(error) };
  }
  if (local.logKey !== key) return;
  local.log = result;
  render();
}
