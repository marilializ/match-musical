/**
 * Painel flutuante preso a um gatilho: a base do dropdown e do calendário.
 *
 * Usa a Popover API (popover="auto"): o painel vai para a camada de cima (nunca
 * é cortado por um cartão ou por uma área com rolagem), fecha ao clicar fora ou
 * com Esc, e só um fica aberto por vez. Aqui fica só o que a API não faz:
 *   - posição: abaixo do gatilho, ou acima se não couber, sem sair da tela;
 *   - acompanhar o gatilho enquanto a página rola ou a janela muda;
 *   - não reabrir quando o clique no gatilho foi o que fechou o painel.
 */
const MARGEM = 8; // distância mínima da borda da tela
const DISTANCIA = 6; // espaço entre o gatilho e o painel

/** Coloca o painel junto ao gatilho. Chamado logo depois de abrir (antes de pintar). */
export function posicionar(painel, gatilho, { larguraMinima = true } = {}) {
  const r = gatilho.getBoundingClientRect();
  const larguraTela = document.documentElement.clientWidth;
  const alturaTela = window.innerHeight;
  if (larguraMinima) painel.style.minWidth = `${Math.round(r.width)}px`;
  painel.style.maxWidth = `${larguraTela - MARGEM * 2}px`;
  painel.style.maxHeight = '';

  const natural = painel.getBoundingClientRect();
  const abaixo = alturaTela - r.bottom - DISTANCIA - MARGEM;
  const acima = r.top - DISTANCIA - MARGEM;
  const paraCima = natural.height > abaixo && acima > abaixo;
  const espaco = Math.max(120, paraCima ? acima : abaixo);
  if (natural.height > espaco) painel.style.maxHeight = `${Math.floor(espaco)}px`;
  const altura = Math.min(natural.height, espaco);

  // Alinha pela esquerda do gatilho; se estourar a tela, pela direita
  let esquerda = r.left;
  if (esquerda + natural.width > larguraTela - MARGEM) esquerda = r.right - natural.width;
  esquerda = Math.min(Math.max(MARGEM, esquerda), larguraTela - MARGEM - natural.width);

  painel.style.top = `${Math.round(paraCima ? r.top - DISTANCIA - altura : r.bottom + DISTANCIA)}px`;
  painel.style.left = `${Math.round(Math.max(MARGEM, esquerda))}px`;
  painel.dataset.lado = paraCima ? 'cima' : 'baixo'; // a animação nasce do lado do gatilho
}

/**
 * Liga um painel [popover] a um gatilho.
 * @returns {{ abrir, fechar, alternar, aberto }}
 */
export function flutuante(painel, gatilho, { aoAbrir, aoFechar, larguraMinima } = {}) {
  const reposicionar = () => posicionar(painel, gatilho, { larguraMinima });
  const aberto = () => painel.matches(':popover-open');
  let estavaAberto = false;

  painel.addEventListener('toggle', (event) => {
    const abriu = event.newState === 'open';
    gatilho.setAttribute('aria-expanded', String(abriu));
    if (abriu) {
      window.addEventListener('scroll', reposicionar, true);
      window.addEventListener('resize', reposicionar);
      aoAbrir?.();
    } else {
      window.removeEventListener('scroll', reposicionar, true);
      window.removeEventListener('resize', reposicionar);
      aoFechar?.();
    }
  });
  // O "clicar fora" fecha no pointerdown; se esse clique foi no próprio gatilho,
  // o click que vem depois não deve reabrir.
  gatilho.addEventListener('pointerdown', () => { estavaAberto = aberto(); });

  const abrir = () => {
    if (aberto()) return;
    try { painel.showPopover({ source: gatilho }); } catch { painel.showPopover(); }
    reposicionar();
  };
  const fechar = () => { if (aberto()) painel.hidePopover(); };
  return {
    abrir,
    fechar,
    aberto,
    alternar() {
      const fecharAgora = aberto() || estavaAberto;
      estavaAberto = false;
      if (fecharAgora) fechar(); else abrir();
    },
  };
}
