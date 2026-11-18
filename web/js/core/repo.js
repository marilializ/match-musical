/**
 * Repositório: a única porta para os dados. Escolhe entre o servidor e a demonstração,
 * normaliza as respostas, atualiza o estado e avisa as telas por eventos.
 */
import { api } from './api.js';
import { createDemoBackend } from './demo.js';
import { state, groupById, matchById } from './state.js';
import { emit } from './events.js';
import { store, KEYS } from './store.js';
import { todayBR } from './format.js';

let demo = null;
const backend = () => (state.demo ? demo : api);

/* ---------- Normalização (ids sempre número, listas sempre array) ---------- */
const toVacancy = (v) => ({ ...v, id: Number(v.id), groupId: Number(v.groupId) });
function normalize(raw) {
  return {
    ...raw,
    musicians: (raw.musicians ?? []).map((m) => ({ ...m, id: Number(m.id) })),
    groups: (raw.groups ?? []).map((g) => ({ ...g, id: Number(g.id), openVacancies: Number(g.openVacancies) || 0, swiped: Boolean(g.swiped) })),
    vacancies: (raw.vacancies ?? []).map(toVacancy),
    matches: (raw.matches ?? []).map((m) => ({ ...m, id: Number(m.id), otherId: Number(m.otherId), otherIsGroup: Boolean(m.otherIsGroup) })),
    stats: raw.stats ?? {},
    objects: raw.objects ?? {},
    matchLog: raw.matchLog === undefined ? [] : raw.matchLog, // null = tabela Log_Match ausente
  };
}

function apply(raw, { isDemo }) {
  state.demo = isDemo;
  state.data = normalize(raw);
  if (!state.data.musicians.some((m) => m.id === state.userId)) state.userId = state.data.musicians[0]?.id ?? 0;
  // decisões que o banco já confirmou deixam de ser "locais"
  for (const id of [...state.decisions.keys()]) if (groupById(id)?.swiped) state.decisions.delete(id);
  emit('data');
}

/* ---------- Carregar ---------- */

/** Carrega tudo. Sem banco na primeira vez, entra em demonstração; depois, mantém o que já está na tela. */
export async function load({ reset = false } = {}) {
  if (state.demo && !reset) return apply(await demo.bootstrap(state.userId), { isDemo: true });
  try {
    apply(await api.bootstrap(state.userId), { isDemo: false });
  } catch (error) {
    if (state.data && !state.demo && !reset) return; // falha momentânea
    demo = createDemoBackend();
    apply(await demo.bootstrap(state.userId), { isDemo: true });
  }
}

let refreshTimer = 0;
/** Junta várias mudanças seguidas numa recarga só (o toque nunca espera o banco). */
export function scheduleRefresh(delay = 1500) {
  clearTimeout(refreshTimer);
  refreshTimer = setTimeout(() => load(), delay);
}

export async function switchUser(id) {
  state.userId = Number(id);
  store.set(KEYS.user, String(id));
  state.decisions.clear();
  state.newMatches.clear();
  state.unseenMatches = 0;
  emit('user');
  await load({ reset: true });
}

export async function resetDemo() {
  demo = createDemoBackend();
  state.decisions.clear();
  apply(await demo.bootstrap(state.userId), { isDemo: true });
}

/* ---------- Ações ---------- */

export async function swipe(groupId, decision) {
  const result = await backend().swipe(state.userId, groupId, decision);
  const group = groupById(groupId);
  if (group) group.swiped = true;
  if (result.matched && group) addMatch(group, Number(result.matchId));
  scheduleRefresh();
  return result;
}

function addMatch(group, id) {
  let match = matchById(id);
  if (!match) {
    match = { id, name: group.name, otherId: group.id, otherIsGroup: true, status: 'Ativo', created: todayBR() };
    state.data.matches.unshift(match);
  }
  state.newMatches.add(id);
  if (state.route !== 'matches') state.unseenMatches += 1;
  emit('matches');
  emit('match', { group, match });
}

export async function convert(matchId, role) {
  await backend().convert(state.userId, matchId, role);
  const match = matchById(matchId);
  if (match) match.status = 'Convertido';
  emit('matches');
  scheduleRefresh(600);
}

export async function syncGroups() {
  const result = await backend().syncGroups();
  await load();
  return result;
}

/* ---------- Consultas sob demanda ---------- */

export async function recommended(filters) {
  const result = await backend().recommended(state.userId, filters);
  return { rows: (result.rows ?? []).map(toVacancy), indicator: result.indicator ?? {} };
}

const membersCache = new Map();
export async function members(groupId) {
  if (membersCache.has(groupId)) return membersCache.get(groupId);
  const result = await backend().members(groupId);
  const rows = result.rows ?? [];
  membersCache.set(groupId, rows);
  return rows;
}

/* ---------- Painel (dashboard) ---------- */
// O painel só mostra dados do banco: na demonstração ele fica indisponível.
const semBanco = () => Object.assign(new Error('O painel lê o banco de dados. Conecte o MySQL para ver os números.'), { status: 503, semBanco: true });

export async function dashboard(filtros) {
  if (state.demo) throw semBanco();
  return api.dashboard(filtros);
}
export async function consultas() {
  if (state.demo) throw semBanco();
  return api.consultas();
}
export async function executarConsulta(numero, valor) {
  if (state.demo) throw semBanco();
  return api.executarConsulta(numero, valor);
}

/** Histórico de situação. Devolve null se a tabela de log não existe no banco. */
export async function matchLog(filters) {
  if (!state.demo && state.data?.matchLog === null) return null;
  const result = await backend().matchLog(state.userId, filters);
  return (result.rows ?? []).map((row) => ({ ...row, match: Number(row.match) }));
}
