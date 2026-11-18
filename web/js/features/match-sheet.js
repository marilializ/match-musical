/**
 * Sheet de um match: quem é, entrar no grupo (sp_converter_match_em_participacao)
 * e o histórico de situação desse match (Log_Match).
 */
import { matchById, groupById, me } from '../core/state.js';
import { esc, errorMessage } from '../core/format.js';
import { haptic } from '../core/motion.js';
import * as repo from '../core/repo.js';
import { openSheet } from '../ui/sheet.js';
import { icon } from '../ui/icon.js';
import { avatar } from '../ui/avatar.js';
import { statusPill } from '../ui/pill.js';
import { list, row, rowIcon, skeletonList } from '../ui/list.js';
import { announce } from '../ui/toast.js';
import { setBusy } from '../ui/busy.js';
import { logRow } from './rows.js';
import { openGroupSheet } from './group-sheet.js';

const STATUS_NOTE = {
  Convertido: 'Você faz parte deste grupo',
  Encerrado: 'Este match foi encerrado',
  Pendente: 'Aguardando o grupo',
};

function convertBlock(match) {
  if (!match.otherIsGroup) return '';
  if (match.status !== 'Ativo') {
    const check = match.status === 'Convertido' ? icon('check') : '';
    return `<p class="detail-note">${check}${esc(STATUS_NOTE[match.status] ?? match.status)}</p>`;
  }
  // A função vem preenchida com a categoria do perfil: o sistema já sabe, a pessoa só confirma.
  return `
    <form class="convert" data-convert-form novalidate>
      <div>
        <label class="field-label" for="role-${match.id}">Sua função no grupo</label>
        <input class="field" id="role-${match.id}" name="role" maxlength="100" autocomplete="off" enterkeyhint="go"
               placeholder="Ex.: Guitarrista" value="${esc(me()?.category ?? '')}" aria-describedby="role-error-${match.id}">
        <p class="field-error" id="role-error-${match.id}" hidden></p>
      </div>
      <button class="btn btn-primary btn-block" type="submit"><span>Entrar no grupo</span></button>
    </form>`;
}

export function openMatchSheet(matchId) {
  const match = matchById(matchId);
  if (!match) return null;
  const group = match.otherIsGroup ? groupById(match.otherId) : null;
  const body = `
    <div class="match-head">
      ${avatar(match.otherId, match.name, { size: 'md' })}
      <h2>${esc(match.name)}</h2>
      <p>${esc(match.created ? `Match desde ${match.created}` : 'Match')}</p>
      <div data-status>${statusPill(match.status)}</div>
    </div>
    <div data-convert>${convertBlock(match)}</div>
    ${group ? `<h3 class="section-title">Grupo</h3>${list([row({ leading: rowIcon('users', 'var(--color-super)'), title: 'Ver perfil do grupo', chevron: true, attrs: `data-open-group="${group.id}"` })], { compact: true })}` : ''}
    <h3 class="section-title">Histórico</h3>
    <div data-log>${skeletonList(2)}</div>`;

  const sheet = openSheet({ label: `Match com ${match.name}`, body, className: 'sheet-match' });
  sheet.el.addEventListener('submit', (event) => {
    if (!event.target.matches('[data-convert-form]')) return;
    event.preventDefault();
    convert(sheet, match, event.target);
  });
  sheet.el.addEventListener('click', (event) => {
    const target = event.target.closest('[data-open-group]');
    if (target) openGroupSheet(Number(target.dataset.openGroup));
  });
  fillLog(sheet, match);
  return sheet;
}

async function convert(sheet, match, form) {
  const input = form.elements.role;
  const error = form.querySelector('.field-error');
  const button = form.querySelector('[type="submit"]');
  const showError = (message) => {
    input.setAttribute('aria-invalid', 'true');
    error.textContent = message;
    error.hidden = false;
  };
  const role = input.value.trim();
  if (!role) { showError('Escreva sua função. Ex.: Guitarrista'); input.focus(); return; }

  input.removeAttribute('aria-invalid');
  error.hidden = true;
  setBusy(button, true);
  try {
    await repo.convert(match.id, role);
    if (!sheet.isOpen) return;
    sheet.el.querySelector('[data-convert]').innerHTML =
      `<div class="convert-done"><span class="check-badge is-new">${icon('check')}</span><p>Você entrou no grupo</p></div>`;
    sheet.el.querySelector('[data-status]').innerHTML = statusPill('Convertido');
    haptic(12);
    announce(`Você entrou no grupo ${match.name}`);
    fillLog(sheet, match);
  } catch (err) {
    setBusy(button, false);
    showError(errorMessage(err));
  }
}

async function fillLog(sheet, match) {
  const box = sheet.el.querySelector('[data-log]');
  try {
    const rows = await repo.matchLog({ match: match.id });
    if (!sheet.isOpen) return;
    if (rows === null) box.innerHTML = '<p class="footnote">Histórico indisponível agora.</p>';
    else box.innerHTML = rows.length ? list(rows.map((entry) => logRow(entry)), { compact: true }) : '<p class="footnote">Nenhuma mudança ainda.</p>';
  } catch (error) {
    if (sheet.isOpen) box.innerHTML = `<p class="footnote">${esc(errorMessage(error))}</p>`;
  }
}
