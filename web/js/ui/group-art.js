/** Peças visuais de um grupo, usadas no card do deck e na capa dos detalhes. */
import { esc, initials, plural } from '../core/format.js';

export const coverArt = (group) => `<div class="cover vinyl" aria-hidden="true"><span class="monogram">${esc(initials(group.name))}</span></div>`;

export const typePill = (group) => `<span class="glass-pill">${esc(group.type || 'Grupo')}</span>`;

/** "Recife · 2 vagas" */
export const groupMeta = (group) =>
  [group.city || 'Local não informado', group.openVacancies ? plural(group.openVacancies, 'vaga', 'vagas') : ''].filter(Boolean).join(' · ');
