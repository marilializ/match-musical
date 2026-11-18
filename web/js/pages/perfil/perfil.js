/**
 * Tela Perfil — quem você é na cena: capa da marca, instrumento, nível em equalizador,
 * números (fn_taxa_retribuicao_usuario), matches recentes e, no fim, o banco
 * (sp_sincronizar_situacao_grupos) e a troca de perfil de teste.
 */
import { state, me, displayName } from '../../core/state.js';
import { on } from '../../core/events.js';
import { LEVELS, levelLabel } from '../../core/domain.js';
import { esc, plural, errorMessage } from '../../core/format.js';
import * as repo from '../../core/repo.js';
import { navBar, bindNavBar } from '../../ui/nav-bar.js';
import { avatar } from '../../ui/avatar.js';
import { icon } from '../../ui/icon.js';
import { pill } from '../../ui/pill.js';
import { levelMeter, progressRing } from '../../ui/meter.js';
import { setBusy } from '../../ui/busy.js';
import { toast } from '../../ui/toast.js';
import { openMatchSheet } from '../../features/match-sheet.js';
import { openSwitchProfileSheet } from './switch-profile-sheet.js';

const RECENT_MATCHES = 8;

let view;
let content;
let nav;
let lastHtml = '';

export function init(section) {
  view = section;
  view.innerHTML = `${navBar('Perfil')}<div class="view-scroll" data-content></div>`;
  nav = bindNavBar(view);
  content = view.querySelector('[data-content]');
  render();
  view.addEventListener('click', onClick);
  on('data', render);
  on('matches', render);
}

function onClick(event) {
  const target = event.target;
  if (target.closest('[data-switch-profile]')) { openSwitchProfileSheet(); return; }
  const match = target.closest('[data-open-match]');
  if (match) { openMatchSheet(Number(match.dataset.openMatch)); return; }
  const sync = target.closest('[data-sync-groups]');
  if (sync) runSync(sync);
}

function render() {
  if (!state.data) { content.innerHTML = ''; return; }
  const musician = me() ?? {};
  const name = displayName(musician);
  const step = LEVELS.indexOf(musician.level) + 1;
  const matches = state.data.matches;
  nav.setTitle(name);

  const identity = [
    musician.category ? pill(musician.category, 'brand', 'music') : '',
    step > 0 ? `<span class="profile-level">${levelMeter(step, LEVELS.length, levelLabel(musician.level))}${esc(levelLabel(musician.level))}</span>` : '',
  ].join('');

  const recent = matches.length
    ? `<ul class="match-strip">${matches.slice(0, RECENT_MATCHES).map((m) => `
        <li><button class="match-strip-item" type="button" data-open-match="${m.id}">
          ${avatar(m.otherId, m.name, { size: 'md' })}<span>${esc(m.name)}</span>
        </button></li>`).join('')}</ul>`
    : `<p class="profile-empty">Seus matches aparecem aqui. <a href="#descobrir">Comece a deslizar</a></p>`;

  const html = `
    <header class="profile-hero">
      <div class="profile-cover vinyl vinyl-wide" aria-hidden="true"></div>
      <button class="profile-switch" type="button" data-switch-profile>${icon('swap')}Trocar perfil</button>
      <div class="profile-id">
        ${avatar(null, name, { size: 'lg' })}
        <h1 class="profile-name">${esc(name)}</h1>
        ${identity ? `<div class="profile-identity">${identity}</div>` : ''}
      </div>
    </header>

    ${musician.bio ? `<blockquote class="profile-bio">${esc(musician.bio)}</blockquote>` : ''}

    <div class="profile-stats">
      <a class="stat-card is-brand" href="#matches">
        <span class="stat-number">${matches.length}</span>
        <span class="stat-caption">${matches.length === 1 ? 'match' : 'matches'}</span>
      </a>
      <div class="stat-card">
        ${progressRing(state.data.stats.rate, 'Curtidas retribuídas')}
        <span class="stat-caption">das suas curtidas voltaram</span>
      </div>
    </div>

    <section class="profile-section">
      <div class="profile-section-head"><h2 class="section-title">Matches recentes</h2>${matches.length > RECENT_MATCHES ? '<a class="link" href="#matches">Ver todos</a>' : ''}</div>
      ${recent}
    </section>

    <section class="account-card">
      <p class="account-status"><span class="status-dot${state.demo ? '' : ' is-live'}"></span>${state.demo ? 'Modo demonstração' : 'Banco de dados conectado'}</p>
      <button class="btn btn-secondary" type="button" data-sync-groups${state.demo ? ' disabled' : ''}><span>${icon('refresh')}Sincronizar grupos</span></button>
      <p class="footnote">${state.demo ? 'Sincronizar precisa do banco conectado.' : 'Fecha as vagas de grupos que já estão completos.'} Perfis de teste: ainda não há login.</p>
    </section>`;

  if (html === lastHtml) return; // nada mudou: não reinicia as animações à toa
  lastHtml = html;
  content.innerHTML = html;
}

async function runSync(button) {
  if (button.disabled) return;
  setBusy(button, true);
  try {
    const result = await repo.syncGroups();
    toast(`${plural(result.groups, 'grupo atualizado', 'grupos atualizados')} · ${plural(result.vacancies, 'vaga fechada', 'vagas fechadas')}`);
  } catch (error) {
    toast(errorMessage(error));
  } finally {
    if (button.isConnected) setBusy(button, false);
  }
}
