/** Funções puras de texto. Nada aqui conhece o DOM ou o estado. */

const ESCAPES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

/** Escapa texto vindo do banco antes de entrar em HTML. */
export const esc = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ESCAPES[char]);

/** Normaliza para busca: sem acento, minúsculo ("Música" encontra "musica"). */
export const norm = (value) => String(value ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();

/** Iniciais das palavras com maiúscula: "Rock das Antigas" → "RA". */
export function initials(name) {
  const words = String(name ?? '').trim().split(/\s+/).filter(Boolean);
  const strong = words.filter((word) => /^\p{Lu}/u.test(word));
  const picked = (strong.length ? strong : words).slice(0, 2);
  return picked.map((word) => word[0]).join('').toUpperCase() || '?';
}

export const plural = (count, one, many) => `${count} ${Number(count) === 1 ? one : many}`;

export const splitList = (text) => String(text ?? '').split(',').map((item) => item.trim()).filter(Boolean);

export const todayBR = () => new Date().toLocaleDateString('pt-BR');

export const nowBR = () => new Date().toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).replace(',', '');

/** Mensagem para a pessoa: a do servidor quando existe, senão uma de conexão. */
export const errorMessage = (error) => (error?.status ? error.message : 'Sem conexão. Tente de novo.');
