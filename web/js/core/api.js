/**
 * Cliente HTTP do servidor Java (WebMain). Uma função por endpoint.
 * Erros viram Error com .status (HTTP) e a mensagem que o servidor mandou.
 */

async function request(path, { method = 'GET', body } = {}) {
  const response = await fetch(path, {
    method,
    cache: 'no-store',
    body: body ? new URLSearchParams(body) : undefined,
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(payload.error || 'Algo deu errado. Tente de novo.');
    error.status = response.status;
    throw error;
  }
  return payload;
}

const query = (params) => new URLSearchParams(
  Object.entries(params).filter(([, value]) => value !== undefined && value !== null).map(([key, value]) => [key, String(value)]),
).toString();

export const api = {
  /** Músicos, grupos, vagas, matches e números do perfil */
  bootstrap: (user) => request(`/api/bootstrap?${query({ user })}`),
  /** INSERT em Swipe; o trigger tr_swipe_cria_match pode criar o Match */
  swipe: (user, target, decision) => request('/api/swipes', { method: 'POST', body: { user, target, decision } }),
  /** vw_recomendacao_vagas + fn_compatibilidade_vaga */
  recommended: (user, { funcao = '', nivel = '' } = {}) => request(`/api/vacancies/recommended?${query({ user, funcao, nivel })}`),
  /** vw_integrantes_grupo */
  members: (group) => request(`/api/groups/members?${query({ group })}`),
  /** Log_Match (tr_match_log_situacao), com filtros */
  matchLog: (user, { match = '', situacao = '' } = {}) => request(`/api/matches/log?${query({ user, match, situacao })}`),
  /** sp_converter_match_em_participacao */
  convert: (user, match, role) => request('/api/matches/convert', { method: 'POST', body: { user, match, role } }),
  /** sp_sincronizar_situacao_grupos */
  syncGroups: () => request('/api/groups/sync', { method: 'POST', body: {} }),
  /** Painel: indicadores e os 6 gráficos (DashboardDAO), com filtros opcionais */
  dashboard: (filtros = {}) => request(`/api/dashboard?${query(filtros)}`),
  /** As 12 consultas das Etapas 3 e 4 e as opções dos parâmetros */
  consultas: () => request('/api/consultas'),
  /** Executa a consulta "numero" com o valor do parâmetro (ou sem) */
  executarConsulta: (numero, valor) => request(`/api/consultas/executar?${query({ numero, valor })}`),
};
