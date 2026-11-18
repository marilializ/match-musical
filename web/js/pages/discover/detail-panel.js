/**
 * Card de detalhes ao lado do card do deck (tablet e desktop). Mora escondido dentro
 * do card principal e desliza para fora, colado nele, quando a pessoa pede "Ver mais".
 * O movimento é todo em CSS (classe .is-expanded no container); aqui entram o conteúdo
 * e o estado. Mostra só o que o card NÃO mostra: descrição, vagas em detalhe, integrantes.
 */
import { esc } from '../../core/format.js';
import { icon } from '../../ui/icon.js';
import { groupDetails, fillMembers } from '../../features/group-details.js';

const ABOUT = { Banda: 'Sobre a banda', Orquestra: 'Sobre a orquestra', Coral: 'Sobre o coral' };

/**
 * @param {HTMLElement} container  elemento que recebe .is-expanded (o card se desloca junto)
 * @param {HTMLElement} panel      o card lateral
 * @param {object} hooks           onClose() — chamado quando fecha pelo próprio botão
 */
export function createDetailPanel(container, panel, { onClose } = {}) {
  let openId = null;
  panel.inert = true;
  panel.setAttribute('aria-labelledby', 'detail-panel-title');

  function open(group) {
    openId = group.id;
    panel.innerHTML = `
      <div class="detail-panel-head">
        <h2 class="detail-panel-title" id="detail-panel-title">${esc(ABOUT[group.type] ?? 'Sobre o grupo')}</h2>
        <button class="close-btn" type="button" data-close-panel aria-label="Fechar detalhes"><span>${icon('x')}</span></button>
      </div>
      ${groupDetails(group, { tags: false })}`;
    panel.scrollTop = 0;
    panel.inert = false;
    container.classList.add('is-expanded');
    fillMembers(panel, group.id, () => openId === group.id);
  }

  function close() {
    if (openId == null) return;
    openId = null;
    panel.inert = true;
    container.classList.remove('is-expanded'); // o conteúdo fica até o fade terminar
  }

  panel.addEventListener('click', (event) => {
    if (event.target.closest('[data-close-panel]')) { close(); onClose?.(); }
  });

  return {
    open,
    close,
    get openId() { return openId; },
  };
}
