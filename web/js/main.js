/**
 * Ponto de entrada: monta as telas, liga a navegação e carrega os dados.
 *
 * Organização:
 *   core/      estado, eventos, API, demonstração, repositório, física de mola (sem DOM de tela)
 *   ui/        componentes genéricos (sheet, toast, lista, segmented, chips…)
 *   features/  peças do domínio usadas por várias telas (sheets de grupo e de match, swipe, celebração)
 *   pages/     uma pasta por tela; cada uma exporta init(section) e, se precisar, onShow()/onResize()
 */
import { state } from './core/state.js';
import { on } from './core/events.js';
import { reducedMotion } from './core/motion.js';
import * as repo from './core/repo.js';
import { initCelebration } from './features/celebration.js';
import { initInk } from './ui/ink.js';
import * as discover from './pages/discover/discover.js';
import * as vagas from './pages/vagas/vagas.js';
import * as matches from './pages/matches/matches.js';
import * as perfil from './pages/perfil/perfil.js';
import * as painel from './pages/painel/painel.js';

const PAGES = { descobrir: discover, vagas, matches, painel, perfil };
const TITLES = { descobrir: 'SYNC', vagas: 'Vagas · SYNC', matches: 'Matches · SYNC', painel: 'Painel · SYNC', perfil: 'Perfil · SYNC' };

const tabbar = document.querySelector('.tabbar');
const tabs = [...tabbar.querySelectorAll('.tab')];
const badge = document.getElementById('matches-badge');
const connection = tabbar.querySelector('[data-connection]');

const routeFromHash = () => {
  const name = location.hash.slice(1);
  return name in PAGES ? name : 'descobrir';
};

function show(name) {
  state.route = name;
  for (const key of Object.keys(PAGES)) document.getElementById(`view-${key}`).hidden = key !== name;
  tabs.forEach((tab, index) => {
    if (tab.dataset.tab === name) {
      tab.setAttribute('aria-current', 'page');
      tabbar.style.setProperty('--tab', index);
    } else {
      tab.removeAttribute('aria-current');
    }
  });
  document.title = TITLES[name];
  if (name === 'matches') state.unseenMatches = 0;
  updateBadge();
  PAGES[name].onShow?.();
}

/** Badge só para o que importa: matches novos que a pessoa ainda não viu. */
function updateBadge() {
  const count = state.unseenMatches;
  badge.hidden = count === 0;
  badge.innerHTML = `${count}<span class="sr-only"> ${count === 1 ? 'novo' : 'novos'}</span>`;
}

function updateConnection() {
  const label = state.demo ? 'Demonstração' : 'Banco conectado';
  connection.title = label; // na barra estreita só o ponto aparece
  connection.innerHTML = `<span class="status-dot${state.demo ? '' : ' is-live'}"></span><span class="tabbar-status-text">${label}</span>`;
}

// Tocar de novo na aba atual volta ao topo (como no iOS).
tabbar.addEventListener('click', (event) => {
  const tab = event.target.closest('.tab');
  if (!tab || tab.dataset.tab !== state.route) return;
  event.preventDefault();
  document.querySelector(`#view-${state.route} .view-scroll`)?.scrollTo({ top: 0, behavior: reducedMotion() ? 'auto' : 'smooth' });
});

for (const [name, page] of Object.entries(PAGES)) page.init(document.getElementById(`view-${name}`));
initCelebration();
initInk();
on('matches', updateBadge);
on('data', updateConnection);
window.addEventListener('hashchange', () => show(routeFromHash()));
window.addEventListener('resize', () => PAGES[state.route].onResize?.());

show(routeFromHash());
// Primeiro contato: nenhum aviso na frente dos cards. Se o banco não respondeu,
// o status aparece discretamente na sidebar e em Perfil → Banco de dados.
repo.load();
