/** Vocabulário do domínio: os valores do banco e como cada um aparece na tela. */

export const GROUP_TYPES = ['Todos', 'Banda', 'Orquestra', 'Coral'];

export const LEVELS = ['Iniciante', 'Basico', 'Intermediario', 'Avancado', 'Profissional'];
const LEVEL_LABEL = {
  Iniciante: 'Iniciante', Basico: 'Básico', Intermediario: 'Intermediário',
  Avancado: 'Avançado', Profissional: 'Profissional',
};
export const levelLabel = (level) => LEVEL_LABEL[level] ?? level ?? '';

/** Resultado de fn_compatibilidade_vaga */
export const COMPATIBLE = 'Compatível';
export const BELOW_LEVEL = 'Abaixo do nível';

/** Situação do Match. "Convertido" vira "No grupo": é o que a pessoa entende. */
export const MATCH_STATUS = {
  Ativo: { label: 'Ativo', tone: 'like' },
  Convertido: { label: 'No grupo', tone: 'super' },
  Encerrado: { label: 'Encerrado', tone: '' },
  Pendente: { label: 'Pendente', tone: 'warning' },
};
export const statusLabel = (status) => MATCH_STATUS[status]?.label ?? status ?? '';

/** Decisões do swipe (valores de Swipe.tipo_decisao). */
export const DECISIONS = {
  Dislike: { label: 'Passar', icon: 'x-bold', tone: 'nope', key: 'ArrowLeft', keyLabel: '←', done: 'Você pulou este grupo', said: (name) => `Você pulou ${name}` },
  'Super Like': { label: 'Super like', icon: 'star-fill', tone: 'super', key: 'ArrowUp', keyLabel: '↑', done: 'Você deu super like', said: (name) => `Super like para ${name}` },
  Like: { label: 'Curtir', icon: 'heart-fill', tone: 'like', key: 'ArrowRight', keyLabel: '→', done: 'Você curtiu este grupo', said: (name) => `Você curtiu ${name}` },
};
export const DECISION_ORDER = ['Dislike', 'Super Like', 'Like'];

/** Direção do gesto de cada decisão: x (−1 esquerda, 1 direita), y (−1 para cima). */
export const DECISION_DIRECTION = {
  Dislike: { x: -1, y: 0 },
  'Super Like': { x: 0, y: -1 },
  Like: { x: 1, y: 0 },
};
