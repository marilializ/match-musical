/**
 * <sync-dropdown>: lista de escolha única (substitui o <select> nativo).
 *
 *   <sync-dropdown label="Categoria" data-f="categoria"></sync-dropdown>
 *   el.options = [['', 'Todas'], ['Rock', 'Rock']]          // [valor, texto]
 *   el.options = [{ grupo: 'Etapa 3', opcoes: [[1, '1. …']] }] // com grupos
 *   el.value = 'Rock';                                      // sem evento
 *   el.addEventListener('change', …)                         // quando a pessoa escolhe
 *
 * Atributos: label (nome acessível), variant="field" (altura de campo).
 * Teclado como no seletor do sistema: ↑ ↓ Home End, Enter/Espaço escolhe, Esc
 * fecha, e digitar as primeiras letras pula para a opção.
 */
import { esc } from '../core/format.js';
import { icon } from './icon.js';
import { flutuante } from './popover.js';

let sequencia = 0;
const normalizar = (texto) => String(texto).normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
const item = (opcao) => (Array.isArray(opcao)
  ? { value: String(opcao[0] ?? ''), label: String(opcao[1] ?? opcao[0] ?? '') }
  : { value: String(opcao.value ?? ''), label: String(opcao.label ?? opcao.value ?? '') });

class SyncDropdown extends HTMLElement {
  static observedAttributes = ['label'];

  #grupos = [];  // [{ grupo, opcoes: [{ value, label, i }] }]
  #opcoes = [];  // lista achatada, na ordem em que aparece
  #valor = '';
  #ativo = -1;
  #busca = '';
  #buscaAte = 0;
  #pronto = false;
  #painel;

  connectedCallback() {
    if (this.#pronto) return;
    this.#pronto = true;
    const id = `dd-${++sequencia}`;
    this.innerHTML = `
      <button type="button" class="dd-trigger" aria-haspopup="listbox" aria-expanded="false" aria-controls="${id}">
        <span class="dd-text"></span>${icon('chevron', 'dd-chevron')}
      </button>
      <div class="floating dd-menu" id="${id}" popover role="listbox" tabindex="-1"></div>`;
    this.gatilho = this.querySelector('.dd-trigger');
    this.menu = this.querySelector('.dd-menu');
    this.#painel = flutuante(this.menu, this.gatilho, {
      aoAbrir: () => {
        this.#ativo = Math.max(0, this.#indice(this.#valor));
        this.#marcarAtivo();
        this.menu.focus({ preventScroll: true });
      },
      aoFechar: () => {
        // Devolve o foco ao gatilho se ele estava na lista (Esc, escolha)
        if (this.menu.contains(document.activeElement) || document.activeElement === document.body) this.gatilho.focus({ preventScroll: true });
      },
    });

    this.gatilho.addEventListener('click', () => this.#painel.alternar());
    this.gatilho.addEventListener('keydown', (event) => {
      if (event.key === 'ArrowDown' || event.key === 'ArrowUp') { event.preventDefault(); this.#painel.abrir(); }
    });
    this.menu.addEventListener('keydown', (event) => this.#teclado(event));
    this.menu.addEventListener('pointermove', (event) => {
      const opcao = event.target.closest('[data-i]');
      if (opcao && Number(opcao.dataset.i) !== this.#ativo) { this.#ativo = Number(opcao.dataset.i); this.#marcarAtivo(false); }
    });
    this.menu.addEventListener('click', (event) => {
      const opcao = event.target.closest('[data-i]');
      if (opcao) this.#escolher(Number(opcao.dataset.i));
    });
    this.#desenhar();
    this.#rotular();
  }

  attributeChangedCallback() { if (this.#pronto) this.#rotular(); }

  get value() { return this.#valor; }
  set value(valor) {
    this.#valor = String(valor ?? '');
    if (this.#pronto) this.#atualizar();
  }

  /** Texto da opção escolhida (o que aparece no gatilho). */
  get selectedLabel() { return this.#opcoes[this.#indice(this.#valor)]?.label ?? ''; }

  get options() { return this.#grupos; }
  set options(lista) {
    let i = 0;
    const comGrupo = (lista ?? []).some((x) => x && !Array.isArray(x) && 'grupo' in x);
    this.#grupos = (comGrupo ? lista : [{ grupo: '', opcoes: lista ?? [] }]).map((g) => ({
      grupo: g.grupo ?? '',
      opcoes: (g.opcoes ?? []).map((o) => ({ ...item(o), i: i++ })),
    }));
    this.#opcoes = this.#grupos.flatMap((g) => g.opcoes);
    // Como no <select>: se o valor atual não existe mais, fica a primeira opção
    if (this.#indice(this.#valor) < 0) this.#valor = this.#opcoes[0]?.value ?? '';
    if (this.#pronto) this.#desenhar();
  }

  #indice(valor) { return this.#opcoes.findIndex((o) => o.value === String(valor)); }

  #desenhar() {
    const base = this.menu.id;
    this.menu.innerHTML = this.#grupos.map((g, k) => {
      const opcoes = g.opcoes.map((o) => `
        <div class="dd-option" role="option" id="${base}-${o.i}" data-i="${o.i}" aria-selected="false">
          <span class="dd-label">${esc(o.label)}</span>${icon('check', 'dd-check')}
        </div>`).join('');
      return g.grupo
        ? `<div role="group" aria-labelledby="${base}-g${k}"><div class="dd-group-label" id="${base}-g${k}">${esc(g.grupo)}</div>${opcoes}</div>`
        : opcoes;
    }).join('');
    this.#atualizar();
  }

  #atualizar() {
    const atual = this.#indice(this.#valor);
    this.menu.querySelectorAll('[data-i]').forEach((el) => el.setAttribute('aria-selected', String(Number(el.dataset.i) === atual)));
    this.querySelector('.dd-text').textContent = this.selectedLabel;
    this.#rotular();
  }

  #rotular() {
    const rotulo = this.getAttribute('label') ?? '';
    this.menu.setAttribute('aria-label', rotulo);
    this.gatilho.setAttribute('aria-label', rotulo ? `${rotulo}: ${this.selectedLabel}` : this.selectedLabel);
  }

  #marcarAtivo(rolar = true) {
    this.menu.querySelectorAll('.dd-option.is-active').forEach((el) => el.classList.remove('is-active'));
    const el = this.menu.querySelector(`[data-i="${this.#ativo}"]`);
    if (!el) { this.menu.removeAttribute('aria-activedescendant'); return; }
    el.classList.add('is-active');
    this.menu.setAttribute('aria-activedescendant', el.id);
    if (rolar) el.scrollIntoView({ block: 'nearest' });
  }

  #escolher(i) {
    const opcao = this.#opcoes[i];
    this.#painel.fechar();
    if (!opcao || opcao.value === this.#valor) return;
    this.#valor = opcao.value;
    this.#atualizar();
    this.dispatchEvent(new Event('change', { bubbles: true }));
  }

  #teclado(event) {
    const ultimo = this.#opcoes.length - 1;
    const mover = (i) => { event.preventDefault(); this.#ativo = Math.min(Math.max(0, i), ultimo); this.#marcarAtivo(); };
    switch (event.key) {
      case 'ArrowDown': return mover(this.#ativo + 1);
      case 'ArrowUp': return mover(this.#ativo - 1);
      case 'Home': return mover(0);
      case 'End': return mover(ultimo);
      case 'PageDown': return mover(this.#ativo + 8);
      case 'PageUp': return mover(this.#ativo - 8);
      case 'Enter':
      case ' ':
        if (event.key === ' ' && performance.now() <= this.#buscaAte) break; // espaço no meio da busca ("Super Like")
        event.preventDefault();
        return this.#escolher(this.#ativo);
      case 'Tab': return this.#painel.fechar();
      default: break;
    }
    if (event.key.length !== 1 || event.metaKey || event.ctrlKey || event.altKey) return;
    // Digitar pula para a primeira opção que começa com o que foi digitado
    const agora = performance.now();
    this.#busca = (agora > this.#buscaAte ? '' : this.#busca) + normalizar(event.key);
    this.#buscaAte = agora + 600;
    const achou = this.#opcoes.findIndex((o) => normalizar(o.label).startsWith(this.#busca));
    if (achou >= 0) mover(achou);
  }
}

if (!customElements.get('sync-dropdown')) customElements.define('sync-dropdown', SyncDropdown);
