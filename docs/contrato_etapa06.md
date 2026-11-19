# Contrato real de integração — Etapa 06

Conferido nos arquivos SQL e Java deste repositório em 17/11/2026. O reset e os testes JDBC foram executados em uma instância MySQL 8.4 temporária isolada, sem alterar o banco local existente.

## Objetos e assinaturas publicados

| Tipo | Objeto real | Origem | Uso atual |
| --- | --- | --- | --- |
| View | `vw_integrantes_grupo` | `sql/06_View_integrante_grupo.sql` | `ViewsFuncoesDAO`, interface web |
| View | `vw_recomendacao_vagas` | `sql/07_recomendacao_vagas.sql` | `ViewsFuncoesDAO`, interface web |
| Função | `fn_taxa_retribuicao_usuario(p_id_usuario INT)` | `sql/06_funcoes_procedimentos_triggers.sql` | perfil web |
| Função | `fn_compatibilidade_vaga(p_id_musico INT, p_id_vaga INT)` | mesmo arquivo | vagas web |
| Procedimento | `sp_converter_match_em_participacao(IN p_id_match INT, IN p_funcao VARCHAR(100))` | mesmo arquivo | matches web |
| Procedimento | `sp_sincronizar_situacao_grupos(OUT p_grupos_atualizados INT, OUT p_vagas_fechadas INT)` | mesmo arquivo | vagas web |
| Trigger | `tr_match_log_situacao`, `AFTER UPDATE ON Match` | mesmo arquivo | log de mudanças de situação |
| Trigger | `tr_swipe_cria_match`, `AFTER INSERT ON Swipe` | mesmo arquivo | criação por interesse mútuo |
| Tabela | `Log_Match` | mesmo arquivo | histórico web e Swing |

O plano citava `vw_vagas_abertas_detalhadas` e `vw_perfil_musico`, mas esses objetos **não existem** nos scripts publicados. A interface web usa as duas views reais acima. `05_consultas_avancadas.sql` contém consultas de anti join e subconsulta, além de índices; a auditoria full outer join emulado está em `consulta_full_outer_join_swipe_match.sql` e ainda não está na interface. As oito consultas da Etapa 3 estão em `04_consultas.sql` e `ConsultaSQL.java`.

## Colunas e contratos da Pessoa 2

- `Swipe(id_swipe, data_hora, tipo_decisao, id_usuario_realizou, id_usuario_encontrado)`. A data e o ID são gerados pelo banco. Decisões: `Like`, `Dislike`, `Super Like`.
- ``Match(id_match, data_criacao, situacao, id_usuario_a, id_usuario_b)``. Data e ID são gerados pelo banco. Situações: `Ativo`, `Encerrado`, `Convertido`, `Pendente`.
- `Musico.nivel_experiencia` e `Musico.categoria_principal` alimentam o gráfico 1.
- `Toca.nivel_habilidade` e `Toca.tempo_experiencia` alimentam o gráfico 2; **tempo_experiencia é `INT` em anos**, não `TIME`.
- `Swipe.tipo_decisao` e `Swipe.data_hora` alimentam o gráfico 3.
- `Match.data_criacao` e `Mensagem.data_hora_envio` existem para as séries e estatísticas coletivas.

O dashboard Swing existente possui indicadores e dois gráficos antigos, sem componente de filtros global publicado pela Pessoa 6. Os gráficos 1 a 3 foram ligados à aba **Visão geral** desse dashboard, com categoria, habilidade e período aplicados às consultas pertinentes. A interface web ainda não oferece esse dashboard. O gráfico de gêneros da Consulta 7 permanece como gráfico adicional, sem substituir o gráfico 2 planejado.

## Responsabilidades e pendências coletivas

- CRUD completo já existente: `Musico` e `Vaga`. Novo CRUD completo: `Swipe` e `Match`, na janela Swing **Swipes e Matches**. A meta de quatro tabelas está coberta no código, pendente de ensaio com MySQL.
- Gráficos 1 a 3: `DashboardDAO`, `DashboardFrame` e `GraficoPizzaPanel`. ~~Gráficos 4 a 6 e seis cartões de indicadores não estão neste repositório; falta a frente da Pessoa 6.~~ Feito em 18/11 (ver a atualização abaixo).
- A interface web já mostra efeitos de funções, procedimentos, triggers e log. A nova janela Swing também mostra o efeito dos dois triggers.
- ~~A auditoria e as consultas avançadas da Etapa 4 precisam de integração à interface para cumprir toda a entrega coletiva. O script 05 atual publica apenas duas consultas e dois índices; confirmar com a Pessoa 3 as quatro consultas previstas.~~ Feito em 18/11 (ver a atualização abaixo).
- `GRANT EXECUTE ON match_musical.* TO 'sync_app'@'localhost'` está no reset. Rotinas com `DELIMITER` são carregadas pelo cliente `mysql`, não pelo JDBC.

## Verificação executada

O `00_reset.sql` concluiu sem erro na instância temporária. `08_verificacao_etapa06.sql` retornou 66 swipes, 35 matches, 38 mensagens, 0 logs, 15 matches com like mútuo, 2 matches sem mensagem, 2 views, 2 funções, 2 procedimentos e 2 triggers. As séries semanais de swipes e matches cobrem setembro, outubro e novembro de 2026. Antes dos testes de escrita, todas as tabelas originais do esquema tinham pelo menos 30 linhas; `Log_Match` começa em zero para que o trigger possa demonstrar a primeira mudança.

Passaram `Etapa6IntegrationCheck`, `SwipeMatchDAOIntegrationCheck`, `MusicoDAOIntegrationCheck` e `VagaDAOIntegrationCheck`. O teste novo confirmou filtros dos gráficos 1 a 3, alteração da contagem de decisões após inserir/excluir swipe, CRUD de Swipe/Match e registro do log. O reset foi repetido após ajustar o seed. Ainda falta ensaio visual e cronometrado da interface Swing/web no ambiente da apresentação.

## Atualização de 18/11/2026 — frentes 3 e 6 fechadas

**Pessoa 3 (consultas).** A aba *Consultas* do dashboard Swing tem as **12 consultas**: 1 a 8 da Etapa 3 e 9 a 12 da Etapa 4.
- A consulta 2 da Etapa 4 (**subconsulta com `IN`**: músicos que deram Like em grupos com vaga aberta, como pede o PDF da Etapa 4) foi escrita no branch `etapa4-subconsulta-in` (`sql/subconsulta_a_in_swipe_vaga.sql`), que não tinha entrado na `main`. Ela foi levada para o `05_consultas_avancadas.sql`, que já roda no reset, no lugar de uma subconsulta correlacionada provisória. Com ela, as 4 da Etapa 4 ficam: anti join (9), subconsulta com IN (10), subconsulta no FROM (11) e full outer join emulado (12).
- Parâmetros por `PreparedStatement`: músico (6), categoria principal (9 e 10) e diagnóstico (12), estes três opcionais com `(? IS NULL OR coluna = ?)`. Na consulta 1 da Etapa 4 entrou a coluna `categoria_principal`, para o filtro ser visível.
- Destaque visual: a coluna que responde a pergunta fica em negrito azul; na auditoria, cada linha tem a cor do diagnóstico. `ConsultaSQL.java` e o README falam em 12 consultas.

**Pessoa 6 (dashboard).** A aba *Visão geral* tem:
- 6 cartões (músicos, grupos, vagas abertas, matches ativos, % convertidos, média de mensagens por match) e a taxa média de retribuição opcional (`fn_taxa_retribuicao_usuario`; sem a função, o cartão mostra "-").
- Filtro global de período (cartões de match e gráficos 3 a 5) e filtros por categoria: categoria principal (1), habilidade (2), situação do match (5) e instrumento (6).
- Gráfico 4: linha semanal de swipes e matches, com as semanas sem evento preenchidas com zero.
- Gráfico 5: histograma de mensagens por match, com média, mediana, moda (empates listados), variância e desvio padrão **amostrais** (n − 1, dito na tela). Média, variância e desvio vêm do SQL (`AVG`, `VAR_SAMP`, `STDDEV_SAMP`); a mediana e a moda saem das frequências do `GROUP BY` (classe `Estatistica`).
- Gráfico 6: dispersão nº de instrumentos × média de anos por músico, com reta de tendência e Pearson pela fórmula com `AVG` e `STDDEV_POP` no SQL. Atenção: `Toca.tempo_experiencia` é `INT` (anos), não `TIME`, então não há `TIME_TO_SEC`.
- O gráfico antigo "Músicos por instrumento (consulta 2)" saiu, porque repetia o gráfico 2 sem filtro; o de gêneros da Consulta 7 continua como extra.

**Verificação.** Reset completo (`00_reset.sql`) em uma instância MySQL 26.7 temporária e isolada; o banco local não foi tocado. Passaram `DashboardEtapa6Check` (novo), `Etapa6IntegrationCheck`, `SwipeMatchDAOIntegrationCheck`, `MusicoDAOIntegrationCheck` e `VagaDAOIntegrationCheck`.
- Com o seed: 8 semanas de atividade; mensagens por match com média 1,09, mediana 1, moda 1 e desvio padrão 0,66 (n = 35); Pearson r = −0,246 (correlação fraca negativa, n = 30 músicos); taxa média de retribuição de 53,1%; 0% de matches convertidos até alguém converter na demonstração.
- O teste de aceitação do enunciado (inserir um swipe pelo CRUD e ver o gráfico mudar) passou.

**Interface web.** Foi refeita como app de swipe (Descobrir, Vagas, Matches, Perfil) e muda os caminhos usados no roteiro: *Vagas → Para você*, *Descobrir → Ver mais*, *Perfil → Sincronizar grupos*, *Matches → Histórico*. O `roteiro_5_minutos_etapa06.md` e o `roteiro_teste.md` foram ajustados.

**Dashboard também na web (18/11).** A aba **Painel** da interface web mostra o mesmo dashboard: indicadores, os 6 gráficos e as 12 consultas, pelos endpoints `/api/dashboard`, `/api/consultas` e `/api/consultas/executar` do `WebMain`, que chamam o mesmo `DashboardDAO`, sem SQL novo. Os gráficos são SVG próprios (`web/js/ui/charts/`), com paleta validada para daltonismo nos dois temas. As duas medidas do gráfico 2 ficam em painéis lado a lado, nunca em dois eixos. Cada gráfico tem "Ver dados" com a tabela dos números. Sem banco (modo demonstração), o Painel avisa que precisa do MySQL, porque o dashboard só mostra dados do banco.

**Ainda pendente (coletivo).** Ensaio cronometrado da apresentação de 24/11 com o banco recriado, e a troca de explicações: cada pessoa explicando uma parte que não escreveu.

