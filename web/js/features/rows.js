/** Linhas de lista do domínio, reaproveitadas em Vagas, Matches, Perfil e nas sheets. */
import { COMPATIBLE, BELOW_LEVEL, levelLabel, statusLabel } from '../core/domain.js';
import { esc } from '../core/format.js';
import { avatar } from '../ui/avatar.js';
import { row, rowIcon } from '../ui/list.js';
import { pill, statusPill } from '../ui/pill.js';

/** Selo de compatibilidade de nível (fn_compatibilidade_vaga). */
export function compatibilityPill(compatibility) {
  if (compatibility === COMPATIBLE) return pill('Combina', 'like', 'check');
  if (compatibility === BELOW_LEVEL) return pill('Nível acima');
  return '';
}

/**
 * Vaga.
 *   recommended → mostra os gêneros em comum
 *   inGroup     → dentro dos detalhes do próprio grupo: sem avatar repetido, sem navegação
 */
export function vacancyRow(vacancy, { recommended = false, inGroup = false } = {}) {
  const level = levelLabel(vacancy.level) || 'Qualquer nível';
  if (inGroup) {
    return row({
      leading: rowIcon('briefcase', 'var(--color-label-2)'),
      title: vacancy.title || 'Vaga',
      subtitle: level,
      subtitle2: vacancy.description || '', // o que a vaga pede, em até 2 linhas
      trailing: compatibilityPill(vacancy.compatibility),
    });
  }
  return row({
    leading: avatar(vacancy.groupId, vacancy.group),
    title: vacancy.title || 'Vaga',
    subtitle: [vacancy.group, vacancy.city].filter(Boolean).join(' · '),
    subtitle2: [level, recommended && vacancy.genres ? `${vacancy.genres} em comum` : ''].filter(Boolean).join(' · '),
    trailing: compatibilityPill(vacancy.compatibility),
    chevron: true,
    attrs: `data-open-group="${vacancy.groupId}"`,
  });
}

/** Match. "Novo" vence a situação; "Ativo" é o normal e não precisa de etiqueta. */
export function matchRow(match, { isNew = false } = {}) {
  const badge = isNew ? pill('Novo', 'accent') : match.status !== 'Ativo' ? statusPill(match.status) : '';
  return row({
    leading: avatar(match.otherId, match.name),
    title: match.name,
    subtitle: [match.otherIsGroup ? 'Grupo' : 'Músico', match.created ? `desde ${match.created}` : ''].filter(Boolean).join(' · '),
    trailing: badge,
    chevron: true,
    attrs: `data-open-match="${match.id}"`,
  });
}

/** Integrante ativo de um grupo (vw_integrantes_grupo). */
export const memberRow = (member) => row({
  leading: avatar(member.id ?? member.name, member.name),
  title: member.name,
  subtitle: [member.role || 'Função não informada', member.since ? `desde ${member.since}` : ''].filter(Boolean).join(' · '),
});

const transition = (entry) =>
  `${esc(statusLabel(entry.from))} <span aria-hidden="true">→</span><span class="sr-only">para</span> ${esc(statusLabel(entry.to))}`;

/**
 * Mudança de situação (Log_Match).
 *   com `match` → lista geral: avatar e nome do match
 *   sem         → dentro da sheet do próprio match: só a mudança e a hora
 */
export function logRow(entry, match = null) {
  if (match) {
    return row({ leading: avatar(match.otherId, match.name), title: match.name, subtitleHtml: `${transition(entry)} · ${esc(entry.when)}` });
  }
  return `<div class="row">${rowIcon('refresh', 'var(--color-super)')}<span class="row-main"><span class="row-title">${transition(entry)}</span><span class="row-sub">${esc(entry.when)}</span></span></div>`;
}
