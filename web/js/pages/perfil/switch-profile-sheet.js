/** Escolher o perfil de teste (ainda não há login). */
import { state, me, displayName } from '../../core/state.js';
import { levelLabel } from '../../core/domain.js';
import * as repo from '../../core/repo.js';
import { openSheet } from '../../ui/sheet.js';
import { avatar } from '../../ui/avatar.js';
import { list, row } from '../../ui/list.js';
import { icon } from '../../ui/icon.js';
import { toast } from '../../ui/toast.js';

export function openSwitchProfileSheet() {
  const rows = state.data.musicians.map((musician) => {
    const current = musician.id === state.userId;
    return row({
      leading: avatar(musician.id, displayName(musician)),
      title: displayName(musician),
      subtitle: [musician.category, levelLabel(musician.level)].filter(Boolean).join(' · '),
      trailing: current ? icon('check', 'row-check') : '',
      attrs: `data-pick-user="${musician.id}" aria-pressed="${current}"`,
    });
  });
  const sheet = openSheet({ title: 'Trocar perfil', body: list(rows) });
  sheet.el.addEventListener('click', async (event) => {
    const option = event.target.closest('[data-pick-user]');
    if (!option) return;
    const id = Number(option.dataset.pickUser);
    sheet.close();
    if (id === state.userId) return;
    await repo.switchUser(id);
    toast(`Perfil: ${displayName(me())}`);
  });
}
