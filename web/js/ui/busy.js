/** Botão em andamento: troca o texto por um indicador girando e bloqueia novo toque. */
export function setBusy(button, busy) {
  button.classList.toggle('is-loading', busy);
  button.disabled = busy;
  const spinner = button.querySelector('.spinner');
  if (busy && !spinner) button.insertAdjacentHTML('beforeend', '<span class="spinner" aria-hidden="true"></span>');
  if (!busy) spinner?.remove();
}
