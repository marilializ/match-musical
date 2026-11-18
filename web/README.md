# Interface web do SYNC

App de swipe entre músicos e grupos: deslize o card para a **direita para curtir**, para a **esquerda para passar** e para **cima para super like**. Os botões ✕ ★ ♥ e as setas do teclado fazem o mesmo.

As telas usam o MySQL do projeto quando o servidor Java está ativo; sem conexão, entram em **modo demonstração** com dados fictícios (nada é gravado).

## Executar

Na raiz do repositório, com as mesmas variáveis de banco do aplicativo Swing:

```sh
export SYNC_DB_URL='jdbc:mysql://localhost:3306/match_musical'
export SYNC_DB_USER='sync_app'
export SYNC_DB_PASSWORD='sua_senha_local'
mvn compile exec:java -Dexec.mainClass=WebMain
```

Abra http://127.0.0.1:8080. A porta muda com `SYNC_WEB_PORT`. O servidor escuta só em `127.0.0.1`.

**Acesso:** ainda não existe login. O perfil é escolhido em **Perfil → Trocar perfil**. Não publique o servidor na rede nem use perfis de pessoas reais até existir autenticação.

## Telas

| Tela | O que faz | Objetos do banco |
|---|---|---|
| **Descobrir** | A primeira tela: só o deck de grupos e os botões. **Ver mais** (ou tocar no card) mostra os detalhes — descrição, o que cada vaga pede e os integrantes. | `Swipe`, `tr_swipe_cria_match`, `vw_integrantes_grupo` |
| **Vagas** | *Para você*: vagas com gênero em comum e nível compatível, com resumo. *Todas*: todas as vagas abertas. Busca e nível valem para as duas. | `vw_recomendacao_vagas`, `fn_compatibilidade_vaga` |
| **Matches** | *Conexões*: tocar num match permite entrar no grupo. *Histórico*: mudanças de situação, filtráveis. | `sp_converter_match_em_participacao`, `Log_Match` |
| **Painel** | O dashboard da Etapa 6. *Visão geral*, em duas seções: **Atividade** (o que muda com o período, com o filtro de datas no cabeçalho dela: matches, ativos, convertidos, mensagens; swipes e matches por semana, decisões, mensagens por match com média/mediana/moda/variância/desvio) e **Comunidade** (todo o histórico: músicos, grupos, vagas, retribuição; instrumentos, níveis com a moda, dispersão com Pearson). Cada gráfico abre com a resposta em uma linha, traz o próprio filtro e tem "Ver dados" com a tabela. *Consultas*: as 12 consultas das Etapas 3 e 4 (lista ao lado no desktop, seletor no celular), com parâmetro e destaque (auditoria colorida por diagnóstico). Só funciona com o banco conectado. | os mesmos SELECTs do `DashboardDAO` (Swing) |
| **Perfil** | Seus números, trocar perfil e sincronizar grupos. | `fn_taxa_retribuicao_usuario`, `sp_sincronizar_situacao_grupos` |

Quando um objeto não está instalado no banco, só a seção dele mostra "indisponível"; o resto funciona.

## Layout responsivo

| Largura | Navegação | "Ver mais" no Descobrir | Outras sheets |
|---|---|---|---|
| até 767 px (celular) | tab bar flutuante embaixo | sheet de baixo (arraste a alça para fechar) | sobem de baixo |
| 768 a 1199 px | barra estreita (ícone + rótulo) | card de detalhes sai de dentro do card principal, colado à direita | modal centralizado |
| 1200 px ou mais | sidebar completa | idem | modal centralizado |

O card de detalhes recolhe com o ✕, com **Ver menos**, com Esc ou quando o card começa a ser arrastado. No desktop com mouse (1024 px ou mais) a escala de fonte e os controles ficam menores, como no macOS. Atalhos: ← passar, ↑ super like, → curtir (aparecem no hover dos botões). Tema claro e escuro seguem o sistema. Com *Reduzir movimento* ligado, os deslocamentos viram fades curtos.

**Tipografia:** Inter no texto da interface e Montserrat nos títulos e na marca, carregadas do Google Fonts (`index.html`). Sem internet, o app usa a fonte do sistema e continua funcionando. As famílias são os tokens `--font-sans` e `--font-display` em `css/tokens.css`.

## Organização do código

```
web/
├── index.html              casca: sprite de ícones, 4 <section> vazias, navegação
├── css/
│   ├── tokens.css          design tokens (espaço e raio em múltiplos de 4, escala de fonte, cores, movimento)
│   ├── base.css            reset, moldura do app, size classes, título grande
│   ├── motion.css          keyframes compartilhados
│   ├── components/         um arquivo por componente (button, card, sheet, list, controls, tabbar…)
│   ├── features/           detalhes de grupo e de match (usados por várias telas)
│   └── pages/              o que é só de uma tela
└── js/
    ├── main.js             boot e roteamento por hash (#descobrir, #vagas, #matches, #perfil)
    ├── core/               estado, eventos, API, demonstração, repositório, física de mola
    ├── ui/                 componentes genéricos: sheet, toast, lista, segmented, chips, avatar, tabela…
    │   └── charts/         gráficos SVG (barras, rosca, linha, histograma, dispersão) + kit comum
    ├── features/           domínio compartilhado: swipe, sheet do grupo, sheet do match, celebração
    └── pages/              uma pasta por tela; cada uma exporta init(section) e onShow()
```

Regras que o código segue:

- **Uma porta para os dados.** Só `core/repo.js` fala com o servidor (`core/api.js`) ou com a demonstração (`core/demo.js`, mesma interface).
- **Telas não se importam.** Elas reagem a eventos (`core/events.js`): `data`, `deck`, `matches`, `match`, `swipe:reverted`.
- **Componentes devolvem HTML; quem os usa liga os eventos.** Texto vindo do banco passa sempre por `esc()`.
- **Nada de valor mágico no CSS.** Componentes usam os tokens de `tokens.css`.
- **Movimento com propósito.** Mola com velocidade no gesto (`core/motion.js`), transform em vez de layout, interrompível, e fallback para *Reduzir movimento*.
