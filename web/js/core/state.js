/**
 * Estado global do app e seletores derivados.
 * Só o repositório (repo.js) e as ações de domínio escrevem aqui; as telas leem.
 */
import { store, KEYS } from './store.js';
import { norm } from './format.js';

export const state = {
  data: null,            // resposta do bootstrap (normalizada)
  demo: false,           // true quando o banco não respondeu
  userId: Number(store.get(KEYS.user)) || 0,
  route: 'descobrir',
  filter: { type: 'Todos', q: '' },
  decisions: new Map(),  // grupo → decisão tomada nesta sessão (antes do banco confirmar)
  newMatches: new Set(), // matches criados nesta sessão (selo "Novo")
  unseenMatches: 0,      // contador do badge da aba Matches
};

export const groupById = (id) => state.data?.groups.find((group) => group.id === Number(id)) ?? null;
export const matchById = (id) => state.data?.matches.find((match) => match.id === Number(id)) ?? null;
export const me = () => state.data?.musicians.find((m) => m.id === state.userId) ?? state.data?.musicians[0] ?? null;
export const displayName = (musician) => musician?.artist || musician?.name || 'Você';

export const filterActive = () => state.filter.type !== 'Todos' || Boolean(state.filter.q.trim());

/** Grupos que ainda podem aparecer no deck, na ordem do servidor (mais vagas primeiro). */
export function deckGroups() {
  if (!state.data) return [];
  const { type, q } = state.filter;
  const query = norm(q);
  return state.data.groups.filter((group) =>
    !group.swiped
    && !state.decisions.has(group.id)
    && group.status !== 'Inativo'
    && (type === 'Todos' || group.type === type)
    && (!query || norm(`${group.name} ${group.city} ${group.genres}`).includes(query)));
}

/** Um objeto do banco (view/função da Etapa 4/5) está instalado? Na demonstração, sempre. */
export const available = (objectName) => state.demo || state.data?.objects?.[objectName] !== false;
