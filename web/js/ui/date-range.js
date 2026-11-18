/**
 * <sync-date-range>: escolha de período (substitui os dois <input type="date">).
 *
 *   <sync-date-range placeholder="Todo o período"></sync-date-range>
 *   el.inicio / el.fim   → "AAAA-MM-DD" ou "" (sem limite)
 *   el.addEventListener('change', …)   quando a pessoa aplica um período
 *
 * Um botão só, que mostra o período em palavras ("Últimos 30 dias",
 * "01/10 – 03/11/2026"). Abre um calendário com atalhos: um clique marca o
 * primeiro dia, o segundo marca o último e aplica (a ordem não importa). O "×"
 * volta para todo o período. Teclado na grade: setas mudam o dia, PageUp/PageDown
 * o mês, Home/End o começo e o fim da semana, Enter/Espaço escolhe.
 */
import { esc } from '../core/format.js';
import { icon } from './icon.js';
import { flutuante } from './popover.js';
import { dataBR, fimDoMes, grade, hoje, inicioDoMes, marcarIntervalo, somarDias, somarMeses, tituloDoMes } from './calendar.js';

let sequencia = 0;

/** Atalhos relativos a hoje: [rótulo, () => [inicio, fim]] */
const ATALHOS = [
  ['Últimos 7 dias', () => [somarDias(hoje(), -6), hoje()]],
  ['Últimos 30 dias', () => [somarDias(hoje(), -29), hoje()]],
  ['Últimos 90 dias', () => [somarDias(hoje(), -89), hoje()]],
  ['Este mês', () => [inicioDoMes(hoje()), hoje()]],
  ['Mês passado', () => { const m = somarMeses(inicioDoMes(hoje()), -1); return [m, fimDoMes(m)]; }],
  ['Todo o período', () => ['', '']],
];

class SyncDateRange extends HTMLElement {
  #inicio = '';
  #fim = '';
  #rascunho = { inicio: '', fim: '' }; // enquanto escolhe
  #etapa = 'inicio';                   // qual dia o próximo clique marca
  #mes = '';
  #foco = '';
  #pronto = false;
  #painel;

  connectedCallback() {
    if (this.#pronto) return;
    this.#pronto = true;
    const id = `dr-${++sequencia}`;
    this.innerHTML = `
      <button type="button" class="dd-trigger dr-trigger" aria-haspopup="dialog" aria-expanded="false" aria-controls="${id}">
        ${icon('calendar', 'dr-icon')}<span class="dd-text"></span>${icon('chevron', 'dd-chevron')}
      </button>
      <button type="button" class="dr-clear" aria-label="Limpar período" hidden>${icon('x')}</button>
      <div class="floating dr-panel" id="${id}" popover role="dialog" aria-label="Escolher período">
        <div class="dr-presets" role="group" aria-label="Atalhos">
          ${ATALHOS.map(([rotulo], i) => `<button type="button" class="dr-preset" data-atalho="${i}" aria-pressed="false">${esc(rotulo)}</button>`).join('')}
        </div>
        <div class="dr-cal">
          <div class="cal-head">
            <button type="button" class="cal-nav" data-mes="-1" aria-label="Mês anterior">${icon('chevron', 'is-back')}</button>
            <h3 class="cal-title" aria-live="polite"></h3>
            <button type="button" class="cal-nav" data-mes="1" aria-label="Próximo mês">${icon('chevron')}</button>
          </div>
          <div data-grade></div>
          <p class="dr-hint" aria-live="polite"></p>
        </div>
      </div>`;
    this.gatilho = this.querySelector('.dr-trigger');
    this.painelEl = this.querySelector('.dr-panel');
    this.#painel = flutuante(this.painelEl, this.gatilho, {
      larguraMinima: false,
      aoAbrir: () => this.#focarDia(),
      aoFechar: () => {
        if (this.painelEl.contains(document.activeElement) || document.activeElement === document.body) this.gatilho.focus({ preventScroll: true });
      },
    });

    this.gatilho.addEventListener('click', () => {
      if (!this.#painel.aberto()) this.#prepararAbertura();
      this.#painel.alternar();
    });
    this.querySelector('.dr-clear').addEventListener('click', () => { this.#aplicar('', ''); this.gatilho.focus(); });
    this.painelEl.addEventListener('click', (event) => this.#clique(event));
    this.painelEl.addEventListener('keydown', (event) => this.#teclado(event));
    this.painelEl.addEventListener('pointerover', (event) => {
      // Prévia: com o primeiro dia marcado, o intervalo acompanha o mouse
      const dia = event.target.closest('[data-dia]')?.dataset.dia;
      if (dia && this.#etapa === 'fim') marcarIntervalo(this.#grade(), this.#rascunho.inicio, dia);
    });
    this.painelEl.addEventListener('pointerleave', () => this.#pintar());
    this.#atualizarGatilho();
  }

  get inicio() { return this.#inicio; }
  get fim() { return this.#fim; }
  /** Define o período sem disparar "change". */
  definir(inicio = '', fim = '') {
    this.#inicio = inicio || '';
    this.#fim = fim || '';
    if (this.#pronto) this.#atualizarGatilho();
  }

  #grade() { return this.painelEl.querySelector('[data-grade]'); }

  #prepararAbertura() {
    this.#rascunho = { inicio: this.#inicio, fim: this.#fim };
    this.#etapa = 'inicio';
    this.#foco = this.#fim || this.#inicio || hoje();
    this.#mes = inicioDoMes(this.#foco);
    this.#desenhar();
  }

  #desenhar() {
    this.painelEl.querySelector('.cal-title').textContent = tituloDoMes(this.#mes);
    this.#grade().innerHTML = grade({ mes: this.#mes, foco: this.#foco });
    this.#pintar();
  }

  #pintar() {
    marcarIntervalo(this.#grade(), this.#rascunho.inicio, this.#etapa === 'fim' ? '' : this.#rascunho.fim);
    this.painelEl.querySelector('.dr-hint').textContent = this.#etapa === 'fim' ? 'Agora o último dia' : 'Escolha o primeiro dia';
    const atual = this.#atalhoAtual(this.#inicio, this.#fim);
    this.painelEl.querySelectorAll('[data-atalho]').forEach((b) => b.setAttribute('aria-pressed', String(Number(b.dataset.atalho) === atual)));
  }

  #focarDia() {
    this.painelEl.querySelector(`[data-dia="${this.#foco}"]`)?.focus({ preventScroll: true });
  }

  /** Muda o dia em foco; se ele cair em outro mês, o calendário vai junto. */
  #moverFoco(dia) {
    this.#foco = dia;
    if (dia.slice(0, 7) !== this.#mes.slice(0, 7)) { this.#mes = inicioDoMes(dia); this.#desenhar(); }
    this.#grade().querySelectorAll('.cal-day').forEach((b) => { b.tabIndex = b.dataset.dia === dia ? 0 : -1; });
    this.#focarDia();
  }

  #clique(event) {
    const nav = event.target.closest('[data-mes]');
    if (nav) {
      this.#mes = somarMeses(this.#mes, Number(nav.dataset.mes));
      this.#foco = this.#mes;
      this.#desenhar();
      return;
    }
    const atalho = event.target.closest('[data-atalho]');
    if (atalho) { this.#aplicar(...ATALHOS[Number(atalho.dataset.atalho)][1]()); return; }
    const dia = event.target.closest('[data-dia]')?.dataset.dia;
    if (dia) this.#escolherDia(dia);
  }

  #escolherDia(dia) {
    this.#foco = dia;
    if (this.#etapa === 'inicio') {
      this.#rascunho = { inicio: dia, fim: '' };
      this.#etapa = 'fim';
      if (dia.slice(0, 7) !== this.#mes.slice(0, 7)) { this.#mes = inicioDoMes(dia); this.#desenhar(); this.#focarDia(); } else this.#pintar();
      return;
    }
    const [a, b] = dia < this.#rascunho.inicio ? [dia, this.#rascunho.inicio] : [this.#rascunho.inicio, dia];
    this.#aplicar(a, b);
  }

  #aplicar(inicio, fim) {
    this.#painel.fechar();
    if (inicio === this.#inicio && fim === this.#fim) return;
    this.definir(inicio, fim);
    this.dispatchEvent(new Event('change', { bubbles: true }));
  }

  #atalhoAtual(inicio, fim) {
    if (!inicio && !fim) return ATALHOS.length - 1;
    return ATALHOS.findIndex(([, intervalo]) => { const [a, b] = intervalo(); return a === inicio && b === fim; });
  }

  #atualizarGatilho() {
    const { 0: a, 1: b } = [this.#inicio, this.#fim];
    const atalho = this.#atalhoAtual(a, b);
    let texto;
    if (atalho >= 0) texto = a || b ? ATALHOS[atalho][0] : (this.getAttribute('placeholder') || 'Todo o período');
    else if (a && b) texto = a.slice(0, 4) === b.slice(0, 4) ? `${dataBR(a).slice(0, 5)} – ${dataBR(b)}` : `${dataBR(a)} – ${dataBR(b)}`;
    else texto = a ? `Desde ${dataBR(a)}` : `Até ${dataBR(b)}`;
    this.querySelector('.dd-text').textContent = texto;
    this.gatilho.setAttribute('aria-label', `Período: ${texto}`);
    const temValor = Boolean(a || b);
    this.classList.toggle('has-value', temValor);
    this.querySelector('.dr-clear').hidden = !temValor;
  }

  #teclado(event) {
    const dia = event.target.closest('[data-dia]')?.dataset.dia;
    if (!dia) return;
    const semana = new Date(`${dia}T12:00`).getDay();
    const destino = {
      ArrowLeft: () => somarDias(dia, -1),
      ArrowRight: () => somarDias(dia, 1),
      ArrowUp: () => somarDias(dia, -7),
      ArrowDown: () => somarDias(dia, 7),
      PageUp: () => somarMeses(dia, event.shiftKey ? -12 : -1),
      PageDown: () => somarMeses(dia, event.shiftKey ? 12 : 1),
      Home: () => somarDias(dia, -semana),
      End: () => somarDias(dia, 6 - semana),
    }[event.key];
    if (!destino) return;
    event.preventDefault();
    this.#moverFoco(destino());
    if (this.#etapa === 'fim') marcarIntervalo(this.#grade(), this.#rascunho.inicio, this.#foco);
  }
}

if (!customElements.get('sync-date-range')) customElements.define('sync-date-range', SyncDateRange);
