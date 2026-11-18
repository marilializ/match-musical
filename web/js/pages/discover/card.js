/**
 * Card de grupo do deck: markup e pintura.
 *
 * Cada card guarda seu estado de movimento em `card._s`:
 *   x, y  deslocamento do arrasto (px)      r   rotação (graus)
 *   sc    escala da pilha                   oy  deslocamento da pilha (px)
 * Tudo vira um único `transform` — mover com transform não refaz layout.
 */
import { esc, splitList } from '../../core/format.js';
import { clamp } from '../../core/motion.js';
import { icon } from '../../ui/icon.js';
import { palette } from '../../ui/avatar.js';
import { coverArt, typePill, groupMeta } from '../../ui/group-art.js';

/** Quanto arrastar (fração do card) para a decisão valer. */
export const THRESHOLD = { x: 0.3, y: 0.18 };

export function createCard(group) {
  const card = document.createElement('article');
  card.className = 'card';
  card.setAttribute('style', palette(group.id));
  card.setAttribute('aria-label', `${group.name}, ${group.type || 'grupo'} em ${group.city || 'local não informado'}`);
  const tags = splitList(group.genres).slice(0, 3);
  card.innerHTML = `
    ${coverArt(group)}
    <div class="card-top">${typePill(group)}</div>
    <span class="stamp stamp-like" aria-hidden="true">Curtir</span>
    <span class="stamp stamp-nope" aria-hidden="true">Passar</span>
    <span class="stamp stamp-super" aria-hidden="true">Super like</span>
    <div class="card-info">
      <div class="card-title-row">
        <h2 class="card-name">${esc(group.name)}</h2>
        <button class="card-more" type="button" data-details aria-label="Ver mais">${icon('info')}<span class="card-more-label" aria-hidden="true">Ver mais</span></button>
      </div>
      <p class="card-meta">${icon('pin')}${esc(groupMeta(group))}</p>
      ${tags.length ? `<ul class="card-tags">${tags.map((tag) => `<li class="glass-pill">${esc(tag)}</li>`).join('')}</ul>` : ''}
    </div>`;
  card._id = group.id;
  card._s = { x: 0, y: 0, r: 0, sc: 1, oy: 0 };
  card._w = 0;
  card._h = 0;
  card._stamps = {
    like: card.querySelector('.stamp-like'),
    nope: card.querySelector('.stamp-nope'),
    super: card.querySelector('.stamp-super'),
  };
  card.addEventListener('animationend', () => card.classList.remove('is-entering'));
  return card;
}

/** Mede o card (só funciona com a tela visível; senão mantém a última medida). */
export function measure(card) {
  card._w = card.offsetWidth || card._w;
  card._h = card.offsetHeight || card._h;
}

export function paint(card) {
  const { x, y, r, sc, oy } = card._s;
  // escala ancorada na base: o card de trás "aparece" embaixo do da frente
  const anchor = (card._h * (1 - sc)) / 2;
  card.style.transform = `translate3d(${x}px, ${y + oy + anchor}px, 0) rotate(${r}deg) scale(${sc})`;
}

/** Quanto o card "quer" cada decisão agora (0 a 1). 1 = passou do limite. */
export function intent(card) {
  const { x, y } = card._s;
  const width = card._w || 1;
  const height = card._h || 1;
  const vertical = y < 0 && Math.abs(y) > Math.abs(x);
  return {
    like: vertical ? 0 : clamp(x / (width * THRESHOLD.x), 0, 1),
    nope: vertical ? 0 : clamp(-x / (width * THRESHOLD.x), 0, 1),
    super: vertical ? clamp(-y / (height * THRESHOLD.y), 0, 1) : 0,
  };
}

export function paintStamps(card, { like, nope, super: up }) {
  card._stamps.like.style.opacity = like;
  card._stamps.nope.style.opacity = nope;
  card._stamps.super.style.opacity = up;
}
