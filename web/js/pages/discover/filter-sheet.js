/** Filtros do Descobrir: tipo de grupo + busca. Aplicam na hora; o botão mostra quantos sobram. */
import { state, deckGroups, filterActive } from '../../core/state.js';
import { GROUP_TYPES } from '../../core/domain.js';
import { emit } from '../../core/events.js';
import { plural } from '../../core/format.js';
import { openSheet } from '../../ui/sheet.js';
import { segmented, selectSegment } from '../../ui/segmented.js';
import { searchField } from '../../ui/search-field.js';

export function openFilterSheet() {
  const body = `
    ${segmented({ name: 'tipo', label: 'Tipo de grupo', options: GROUP_TYPES.map((type) => [type, type]), selected: state.filter.type })}
    ${searchField({ id: 'filter-q', label: 'Buscar grupo', placeholder: 'Cidade, gênero ou nome', value: state.filter.q, enterkeyhint: 'done' })}`;
  const foot = `
    <button class="btn btn-primary btn-block" type="button" data-filter-done></button>
    <button class="btn btn-plain btn-block" type="button" data-filter-clear>Limpar filtros</button>`;
  const sheet = openSheet({ title: 'Filtros', body, foot });

  const input = sheet.el.querySelector('#filter-q');
  const types = sheet.el.querySelector('[data-segmented]');
  const done = sheet.el.querySelector('[data-filter-done]');
  const clear = sheet.el.querySelector('[data-filter-clear]');

  const apply = () => {
    emit('deck');
    const count = deckGroups().length;
    done.textContent = count ? `Ver ${plural(count, 'grupo', 'grupos')}` : 'Nenhum grupo';
    clear.hidden = !filterActive();
  };
  apply();

  sheet.el.addEventListener('click', (event) => {
    const option = event.target.closest('[data-seg]');
    if (option) { state.filter.type = option.dataset.seg; selectSegment(types, state.filter.type); apply(); }
    if (event.target.closest('[data-filter-done]')) sheet.close();
    if (event.target.closest('[data-filter-clear]')) {
      state.filter = { type: 'Todos', q: '' };
      input.value = '';
      selectSegment(types, 'Todos');
      apply();
    }
  });
  input.addEventListener('input', () => { state.filter.q = input.value; apply(); });
  input.addEventListener('keydown', (event) => { if (event.key === 'Enter') { event.preventDefault(); sheet.close(); } });
}
