/**
 * Barramento de eventos mínimo. As telas reagem a mudanças sem importar umas às outras.
 *
 * Tópicos usados no app:
 *   data            dados recarregados (servidor ou demonstração)
 *   user            o perfil escolhido mudou (antes de recarregar)
 *   deck            mudou o que entra no deck (decisão tomada, filtro)
 *   matches         a lista de matches mudou
 *   match           { group, match } — um swipe acabou de criar um match
 *   swipe:reverted  { groupId, decision } — não deu para salvar; o card volta
 */
const listeners = new Map();

export function on(topic, handler) {
  if (!listeners.has(topic)) listeners.set(topic, new Set());
  listeners.get(topic).add(handler);
  return () => listeners.get(topic)?.delete(handler);
}

export function emit(topic, payload) {
  for (const handler of listeners.get(topic) ?? []) handler(payload);
}
