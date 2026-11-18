/** localStorage com proteção: em aba anônima ou com dados bloqueados, o app segue funcionando. */
export const KEYS = {
  user: 'sync-user',  // perfil escolhido
  hint: 'sync-hint',  // a dica de deslizar já foi vista
};

export const store = {
  get(key) {
    try { return localStorage.getItem(key); } catch { return null; }
  },
  set(key, value) {
    try { localStorage.setItem(key, value); } catch { /* armazenamento indisponível */ }
  },
};
