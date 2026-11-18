/**
 * Conteúdo de detalhes de um grupo: gêneros, descrição, vagas (com o que cada uma pede)
 * e integrantes. O mesmo bloco aparece na sheet (celular) e no card de detalhes ao lado
 * do deck (tablet/desktop) — ali sem os gêneros, que o card principal já mostra.
 */
import { state, available } from '../core/state.js';
import { esc, plural, splitList, errorMessage } from '../core/format.js';
import * as repo from '../core/repo.js';
import { list, skeletonList } from '../ui/list.js';
import { pill } from '../ui/pill.js';
import { vacancyRow, memberRow } from './rows.js';

export function groupDetails(group, { tags: showTags = true } = {}) {
  const vacancies = state.data.vacancies.filter((v) => v.groupId === group.id);
  const tags = showTags ? splitList(group.genres) : [];
  return `
    ${tags.length ? `<div class="tag-row">${tags.map((tag) => pill(tag)).join('')}</div>` : ''}
    ${group.description ? `<p class="detail-desc">${esc(group.description)}</p>` : '<p class="footnote">Este grupo ainda não escreveu uma descrição.</p>'}
    <h3 class="section-title">${vacancies.length ? plural(vacancies.length, 'vaga aberta', 'vagas abertas') : 'Vagas'}</h3>
    ${vacancies.length
      ? list(vacancies.map((v) => vacancyRow(v, { inGroup: true })), { compact: true })
      : '<p class="footnote">Nenhuma vaga aberta agora.</p>'}
    <h3 class="section-title">Integrantes</h3>
    <div data-members>${skeletonList(2)}</div>`;
}

/**
 * Preenche os integrantes (carregados sob demanda, com cache).
 * @param isCurrent  função que diz se o bloco ainda está na tela (evita escrever em sheet fechada)
 */
export async function fillMembers(root, groupId, isCurrent = () => true) {
  const box = root.querySelector('[data-members]');
  if (!box) return;
  if (!available('vw_integrantes_grupo')) {
    box.innerHTML = '<p class="footnote">Integrantes indisponíveis agora.</p>';
    return;
  }
  try {
    const members = await repo.members(groupId);
    if (!isCurrent()) return;
    box.innerHTML = members.length ? list(members.map(memberRow)) : '<p class="footnote">Nenhum integrante ativo ainda.</p>';
  } catch (error) {
    if (!isCurrent()) return;
    // 404/501: o servidor ou o banco ainda não têm esse recurso (ex.: servidor antigo rodando)
    const message = [404, 501].includes(error.status) ? 'Integrantes indisponíveis agora.' : errorMessage(error);
    box.innerHTML = `<p class="footnote">${esc(message)}</p>`;
  }
}
