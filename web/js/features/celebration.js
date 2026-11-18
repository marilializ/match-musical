/**
 * Tela "Deu match!". Escuta o evento `match` (criado pelo trigger no banco) e celebra.
 * É o pico emocional do app: aqui, e só aqui, a mola tem bounce.
 */
import { on } from '../core/events.js';
import { me, displayName } from '../core/state.js';
import { esc } from '../core/format.js';
import { haptic } from '../core/motion.js';
import { avatar } from '../ui/avatar.js';
import { icon } from '../ui/icon.js';
import { openMatchSheet } from './match-sheet.js';

export function initCelebration() {
  on('match', ({ group, match }) => celebrate(group, match));
}

function celebrate(group, match) {
  haptic([10, 40, 16]);
  const dialog = document.createElement('dialog');
  dialog.className = 'celebration';
  dialog.setAttribute('aria-labelledby', 'celebration-title');
  dialog.innerHTML = `
    <div class="celebration-bg"></div>
    <div class="celebration-content">
      <div class="celebration-avatars" aria-hidden="true">
        ${avatar(null, displayName(me()))}
        <span class="celebration-heart">${icon('heart-fill')}</span>
        ${avatar(group.id, group.name)}
      </div>
      <h2 class="celebration-title" id="celebration-title">Deu match!</h2>
      <p class="celebration-text">Você e ${esc(group.name)} se curtiram.</p>
      <div class="celebration-buttons">
        <button class="btn btn-light btn-block" type="button" data-see-match>Ver match</button>
        <button class="btn btn-ghost-light btn-block" type="button" data-keep-going>Continuar</button>
      </div>
    </div>`;
  document.body.append(dialog);
  dialog.showModal();
  requestAnimationFrame(() => requestAnimationFrame(() => dialog.classList.add('is-open')));

  let closing = false;
  const close = (then) => {
    if (closing) return;
    closing = true;
    dialog.classList.add('is-closing');
    setTimeout(() => { dialog.close(); dialog.remove(); then?.(); }, 200);
  };
  dialog.addEventListener('cancel', (event) => { event.preventDefault(); close(); });
  dialog.addEventListener('click', (event) => {
    if (event.target.closest('[data-keep-going]')) close();
    if (event.target.closest('[data-see-match]')) {
      close(() => {
        location.hash = 'matches';
        openMatchSheet(match.id);
      });
    }
  });
}
