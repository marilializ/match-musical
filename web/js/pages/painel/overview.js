/**
 * Painel → Visão geral: os indicadores e os 6 gráficos da Etapa 6, com os dados
 * de /api/dashboard (mesmos SELECTs do dashboard Swing).
 *
 * Hierarquia: duas seções, cada uma com uma faixa de 4 números e 3 gráficos
 * (um largo e dois lado a lado).
 *   Atividade  → o que muda com o período (matches, swipes, mensagens). O filtro
 *                de período fica no cabeçalho dela, então não precisa de aviso.
 *   Comunidade → quem está cadastrado (músicos, grupos, instrumentos).
 * Cada gráfico abre com a resposta em uma linha ("Like é 61% dos swipes"); o
 * filtro de cada gráfico fica no próprio cartão; "Ver dados" mostra a tabela.
 */
import { esc, errorMessage, plural } from '../../core/format.js';
import { levelLabel } from '../../core/domain.js';
import * as repo from '../../core/repo.js';
import { emptyState } from '../../ui/empty.js';
import { tabela } from '../../ui/data-table.js';
import { fmt, fmtAuto, legenda, montar } from '../../ui/charts/kit.js';
import { barras } from '../../ui/charts/bars.js';
import { rosca } from '../../ui/charts/donut.js';
import { linhas } from '../../ui/charts/line.js';
import { histograma } from '../../ui/charts/histogram.js';
import { dispersao } from '../../ui/charts/scatter.js';
import '../../ui/dropdown.js';
import '../../ui/date-range.js';

const COR = { s1: 'var(--chart-1)', s2: 'var(--chart-2)', s3: 'var(--chart-3)' };
const SERIE_DECISAO = { Like: 's1', Dislike: 's2', 'Super Like': 's3' }; // a cor segue a decisão
const diaMes = (iso) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}`;
/** ['a','b','c'] → "a, b e c" */
const lista = (itens) => (itens.length < 2 ? itens.join('') : `${itens.slice(0, -1).join(', ')} e ${itens.at(-1)}`);
const forte = (texto) => `<strong>${esc(texto)}</strong>`;

function forcaCorrelacao(r) {
  const m = Math.abs(r);
  if (m < 0.1) return 'praticamente nula';
  return `${m < 0.3 ? 'fraca' : m < 0.7 ? 'moderada' : 'forte'} ${r > 0 ? 'positiva' : 'negativa'}`;
}

/* ---------- As duas seções e o que cada filtro atinge ---------- */

const SECOES = [
  { id: 'atividade', titulo: 'Atividade', cartoes: ['semanas', 'decisoes', 'mensagens'] },
  { id: 'comunidade', titulo: 'Comunidade', cartoes: ['instrumentos', 'niveis', 'dispersao'] },
];

/** Só o que o filtro muda fica esmaecido enquanto o banco responde. */
const ALCANCE = {
  inicio: '[data-secao="atividade"]',
  fim: '[data-secao="atividade"]',
  categoria: '[data-card="niveis"]',
  habilidade: '[data-card="instrumentos"]',
  situacao: '[data-card="mensagens"]',
  instrumento: '[data-card="dispersao"]',
};

const filtroSelect = (chave, rotulo) => `<sync-dropdown data-f="${chave}" label="${esc(rotulo)}"></sync-dropdown>`;

function cartao(id) {
  const c = CARTOES[id];
  return `
    <section class="chart-card${c.largo ? ' is-wide' : ''}" data-card="${id}" aria-labelledby="t-${id}">
      <header class="chart-head">
        <div class="chart-heading">
          <h3 class="chart-title" id="t-${id}">${esc(c.titulo)}</h3>
          <p class="chart-insight" data-insight></p>
        </div>
        ${c.filtro ? filtroSelect(...c.filtro) : ''}
      </header>
      <div data-extra></div>
      <div class="chart-body" data-body></div>
      <div class="chart-foot" data-foot></div>
      <details class="chart-data"><summary>Ver dados</summary><div data-table></div></details>
    </section>`;
}

const cabecalho = (secao) => (secao.id === 'atividade'
  ? '<sync-date-range data-periodo placeholder="Todo o período"></sync-date-range>'
  : '<span class="dash-scope">Todo o histórico</span>');

export function createOverview(root) {
  const filtros = { inicio: '', fim: '', categoria: '', habilidade: '', situacao: '', instrumento: '' };
  let dados = null;
  let pedido = 0;
  let montado = false;
  const graficos = {};

  function estrutura() {
    root.innerHTML = `${SECOES.map((secao) => `
      <section class="dash-section" data-secao="${secao.id}" aria-labelledby="sec-${secao.id}">
        <header class="dash-head">
          <h2 class="dash-title" id="sec-${secao.id}">${secao.titulo}</h2>
          ${cabecalho(secao)}
        </header>
        <div class="kpi-grid" data-kpis="${secao.id}"></div>
        <div class="dash-grid">${secao.cartoes.map(cartao).join('')}</div>
      </section>`).join('')}
      <p class="footnote painel-fonte">Fonte: os mesmos SELECTs do dashboard Swing (DashboardDAO), enviados por PreparedStatement.</p>`;

    for (const id of Object.keys(CARTOES)) {
      const corpo = root.querySelector(`[data-card="${id}"] [data-body]`);
      graficos[id] = montar(corpo, (largura) => (dados ? CARTOES[id].desenho(dados, largura) : '<div class="skeleton-card" style="position:relative;height:180px"></div>'));
    }
    montado = true;
  }

  // Ouvintes por delegação, registrados uma vez (a estrutura pode ser refeita depois de um erro)
  root.addEventListener('change', (event) => {
    const periodo = event.target.closest('[data-periodo]');
    if (periodo) {
      filtros.inicio = periodo.inicio;
      filtros.fim = periodo.fim;
      carregar(ALCANCE.inicio);
      return;
    }
    const campo = event.target.closest('[data-f]');
    if (!campo) return;
    filtros[campo.dataset.f] = campo.value;
    carregar(ALCANCE[campo.dataset.f]);
  });
  root.addEventListener('click', (event) => {
    if (event.target.closest('[data-tentar]')) carregar();
  });

  /** Preenche os combos de filtro com o que existe no banco, mantendo a escolha. */
  function opcoes(campo, itens, todos) {
    const lista = root.querySelector(`sync-dropdown[data-f="${campo}"]`);
    lista.options = [['', todos], ...itens];
    lista.value = filtros[campo];
  }

  /** @param alcance seletor do que o filtro muda (esmaece só isso); sem ele, tudo. */
  async function carregar(alcance) {
    if (!montado) { estrutura(); root.querySelector('[data-periodo]').definir(filtros.inicio, filtros.fim); }

    const meu = ++pedido;
    const esmaecer = alcance && dados ? root.querySelector(alcance) : root;
    esmaecer.classList.add('is-stale');
    try {
      const resposta = await repo.dashboard(Object.fromEntries(Object.entries(filtros).filter(([, v]) => v)));
      if (meu !== pedido) return; // chegou resposta de um filtro antigo
      dados = resposta;
      pintar();
    } catch (error) {
      if (meu !== pedido) return;
      montado = false;
      root.classList.remove('is-stale');
      root.innerHTML = error.semBanco
        ? emptyState({ icon: 'database', title: 'Painel indisponível', text: error.message })
        : emptyState({ icon: 'info', title: 'Não deu para carregar o painel', text: errorMessage(error),
          actions: '<button class="btn btn-secondary" type="button" data-tentar>Tentar de novo</button>' });
    }
  }

  function pintar() {
    root.classList.remove('is-stale');
    root.querySelectorAll('.is-stale').forEach((el) => el.classList.remove('is-stale'));
    const f = dados.filtros;
    opcoes('categoria', f.categorias.map((c) => [c, c]), 'Todas as categorias');
    opcoes('habilidade', f.habilidades.map((h) => [h, levelLabel(h)]), 'Todas as habilidades');
    opcoes('situacao', f.situacoes.map((s) => [s, s]), 'Todas as situações');
    opcoes('instrumento', f.instrumentos.map((i) => [i.id, i.nome]), 'Todos os instrumentos');

    const kpis = indicadores(dados.indicadores, Boolean(filtros.inicio || filtros.fim));
    for (const [secao, itens] of Object.entries(kpis)) {
      root.querySelector(`[data-kpis="${secao}"]`).innerHTML = itens.map(([rotulo, valor, sub]) =>
        `<div class="kpi"><span class="kpi-label">${esc(rotulo)}</span><span class="kpi-value">${valor}</span><span class="kpi-sub">${esc(sub)}</span></div>`).join('');
    }

    for (const [id, c] of Object.entries(CARTOES)) {
      const card = root.querySelector(`[data-card="${id}"]`);
      const r = c.resumo(dados);
      card.querySelector('[data-insight]').innerHTML = r.insight;
      card.querySelector('[data-extra]').innerHTML = r.extra ?? '';
      card.querySelector('[data-foot]').innerHTML = r.rodape ?? '';
      card.querySelector('[data-table]').innerHTML = tabela(r.tabela.colunas, r.tabela.linhas);
    }
    for (const grafico of Object.values(graficos)) grafico.redesenhar();
  }

  return {
    carregar: () => carregar(),
    get carregado() { return dados != null; },
  };
}

/** Os 8 números, 4 por seção: [rótulo, valor, complemento]. */
function indicadores(k, comPeriodo) {
  const doTotal = (parte) => (k.matchesNoPeriodo ? `${fmt((100 * parte) / k.matchesNoPeriodo, 1)}% do total` : 'nenhum match');
  const pct = (valor) => (valor == null ? '–' : `${fmt(valor, 1)}%`);
  return {
    atividade: [
      ['Matches', fmt(k.matchesNoPeriodo), comPeriodo ? 'criados no período' : 'criados até hoje'],
      ['Ativos', fmt(k.matchesAtivos), doTotal(k.matchesAtivos)],
      ['Convertidos', fmt(k.matchesConvertidos), k.percentualConvertidos == null ? '–' : `${pct(k.percentualConvertidos)} viraram participação`],
      ['Mensagens', fmt(k.mediaMensagens, 2), 'por match, em média'],
    ],
    comunidade: [
      ['Músicos', fmt(k.musicos), 'cadastrados'],
      ['Grupos', fmt(k.grupos), 'cadastrados'],
      ['Vagas abertas', fmt(k.vagasAbertas), 'agora'],
      ['Retribuição', pct(k.taxaMediaRetribuicao), 'das curtidas voltaram, em média'],
    ],
  };
}

/* ---------- Cada gráfico: título, filtro, desenho (dados + largura → SVG) e resumo ---------- */

const CARTOES = {
  semanas: {
    titulo: 'Swipes e matches por semana',
    largo: true,
    desenho: (d, w) => linhas(d.semanas.map((s) => ({ rotulo: diaMes(s.inicio), valores: [s.swipes, s.matches] })), w,
      [{ nome: 'swipes', classe: 's1' }, { nome: 'matches', classe: 's2' }]),
    resumo: (d) => {
      const pico = d.semanas.reduce((a, b) => (b.swipes > a.swipes ? b : a), { swipes: 0 });
      return {
        insight: pico.swipes
          ? `Pico na semana de ${forte(diaMes(pico.inicio))}: ${plural(pico.swipes, 'swipe', 'swipes')} e ${plural(pico.matches, 'match', 'matches')}`
          : 'Nenhum swipe no período',
        extra: legenda([{ nome: 'Swipes', cor: COR.s1 }, { nome: 'Matches', cor: COR.s2 }]),
        tabela: { colunas: ['Semana (início)', 'Swipes', 'Matches'], linhas: d.semanas.map((s) => [diaMes(s.inicio), s.swipes, s.matches]) },
      };
    },
  },
  decisoes: {
    titulo: 'Swipes por decisão',
    desenho: (d, w) => rosca(d.decisoes.map((x) => ({ rotulo: x.decisao, valor: x.qtd, serie: SERIE_DECISAO[x.decisao] ?? 's1' })), w, { unidade: 'swipes' }),
    resumo: (d) => {
      const total = d.decisoes.reduce((s, x) => s + x.qtd, 0);
      const maior = d.decisoes.reduce((a, b) => (b.qtd > a.qtd ? b : a), { qtd: 0 });
      return {
        insight: total ? `${forte(maior.decisao)} é ${fmt((100 * maior.qtd) / total, 0)}% dos ${plural(total, 'swipe', 'swipes')}` : 'Nenhum swipe no período',
        tabela: { colunas: ['Decisão', 'Swipes', '%'], linhas: d.decisoes.map((x) => [x.decisao, x.qtd, total ? Math.round((1000 * x.qtd) / total) / 10 : 0]) },
      };
    },
  },
  mensagens: {
    titulo: 'Mensagens por match',
    filtro: ['situacao', 'Situação do match'],
    desenho: (d, w) => histograma(d.mensagens.frequencias, w,
      { media: d.mensagens.media, mediana: d.mensagens.mediana, unidade: 'mensagens', contagem: 'matches' }),
    resumo: (d) => {
      const m = d.mensagens;
      const moda = m.modas.length ? `${m.modas.join(' ou ')} ${m.modas.length === 1 && m.modas[0] === 1 ? 'mensagem' : 'mensagens'}` : '–';
      // As 5 medidas pedidas; média e mediana levam a marca da linha que têm no gráfico (servem de legenda)
      const medida = (nome, valor, marca = '') => `<div><dt>${marca}${nome}</dt><dd>${valor}</dd></div>`;
      return {
        insight: m.n ? `O mais comum é ${forte(moda)} por match${m.modas.length > 1 ? ' (empate)' : ''}` : 'Nenhum match no período',
        extra: `<dl class="stat-strip">
          ${medida('Média', fmt(m.media, 2), `<i class="is-line" style="--swatch:${COR.s2}"></i>`)}
          ${medida('Mediana', fmtAuto(m.mediana), `<i class="is-line is-dotted" style="--swatch:${COR.s3}"></i>`)}
          ${medida('Moda', m.modas.length ? m.modas.join(' e ') : '–')}
          ${medida('Variância', fmt(m.variancia, 2))}
          ${medida('Desvio padrão', fmt(m.desvioPadrao, 2))}
        </dl>`,
        rodape: `<span>Variância e desvio padrão amostrais (n − 1) · ${plural(m.n, 'match', 'matches')}</span>`,
        tabela: { colunas: ['Mensagens', 'Matches'], linhas: m.frequencias.map((f) => [f.valor, f.qtd]) },
      };
    },
  },
  instrumentos: {
    titulo: 'Instrumentos',
    largo: true,
    filtro: ['habilidade', 'Habilidade'],
    desenho: (d, w) => {
      const lado = w >= 520;
      const coluna = lado ? (w - 16) / 2 : w;
      const musicos = barras(d.instrumentos.map((i) => ({ rotulo: i.instrumento, valor: i.musicos })), coluna, { unidade: 'músicos' });
      const anos = barras(d.instrumentos.map((i) => ({ rotulo: i.instrumento, valor: i.mediaAnos ?? 0 })), coluna, { unidade: 'anos em média' });
      // Duas medidas de escala diferente: dois painéis, nunca dois eixos no mesmo gráfico
      return `<div class="chart-multiples" style="grid-template-columns:${lado ? '1fr 1fr' : '1fr'}">
        <div><h4>Músicos</h4>${musicos}</div><div><h4>Média de anos de experiência</h4>${anos}</div></div>`;
    },
    resumo: (d) => {
      const maior = Math.max(0, ...d.instrumentos.map((i) => i.musicos));
      const topo = d.instrumentos.filter((i) => i.musicos === maior).map((i) => i.instrumento);
      let insight = 'Nenhum músico com essa habilidade';
      if (maior && topo.length <= 3) insight = `Mais tocados: ${forte(lista(topo))}, com ${plural(maior, 'músico', 'músicos')}${topo.length > 1 ? ' cada' : ''}`;
      else if (maior) insight = `${topo.length} instrumentos empatados no topo, com ${plural(maior, 'músico', 'músicos')} cada`;
      return {
        insight,
        tabela: { colunas: ['Instrumento', 'Músicos', 'Média (anos)'], linhas: d.instrumentos.map((i) => [i.instrumento, i.musicos, i.mediaAnos]) },
      };
    },
  },
  niveis: {
    titulo: 'Músicos por nível',
    filtro: ['categoria', 'Categoria principal'],
    desenho: (d, w) => {
      const maior = Math.max(0, ...d.niveis.map((n) => n.qtd));
      const modas = new Set(d.niveis.filter((n) => n.qtd === maior && maior > 0).map((n) => levelLabel(n.nivel)));
      return barras(d.niveis.map((n) => ({ rotulo: levelLabel(n.nivel), valor: n.qtd })), w, { unidade: 'músicos', destaque: modas });
    },
    resumo: (d) => {
      const maior = Math.max(0, ...d.niveis.map((n) => n.qtd));
      const modas = d.niveis.filter((n) => n.qtd === maior && maior > 0).map((n) => levelLabel(n.nivel));
      return {
        insight: maior ? `Moda: ${forte(lista(modas))}, com ${plural(maior, 'músico', 'músicos')}${modas.length > 1 ? ' cada' : ''}` : 'Nenhum músico nessa categoria',
        tabela: { colunas: ['Nível', 'Músicos'], linhas: d.niveis.map((n) => [levelLabel(n.nivel), n.qtd]) },
      };
    },
  },
  dispersao: {
    titulo: 'Instrumentos × experiência',
    filtro: ['instrumento', 'Instrumento'],
    desenho: (d, w) => dispersao(d.dispersao.pontos.map((p) => ({ id: p.id, nome: p.nome, x: p.instrumentos, y: p.mediaAnos })), w, {
      eixoX: 'nº de instrumentos que toca', eixoY: 'média de anos de experiência',
      dica: (p) => `${p.nome}: ${p.x} ${p.x === 1 ? 'instrumento' : 'instrumentos'} · ${fmt(p.y, 1)} anos em média`,
    }),
    resumo: (d) => {
      const { pontos, pearson } = d.dispersao;
      return {
        insight: pearson == null
          ? 'Correlação indefinida: o número de instrumentos ou a experiência não varia'
          : `Correlação ${forte(forcaCorrelacao(pearson))} (r de Pearson = ${fmt(pearson, 2)})`,
        extra: legenda([{ nome: 'Músico', cor: COR.s1 }, { nome: 'Tendência', cor: COR.s2, linha: true }]),
        rodape: `<span>Um ponto por músico · ${plural(pontos.length, 'músico', 'músicos')}</span>`,
        tabela: { colunas: ['Músico', 'Instrumentos', 'Média (anos)'], linhas: pontos.map((p) => [p.nome, p.instrumentos, Math.round(p.mediaAnos * 10) / 10]) },
      };
    },
  },
};
