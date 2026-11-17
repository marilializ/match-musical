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
- Gráficos 1 a 3: `DashboardDAO`, `DashboardFrame` e `GraficoPizzaPanel`. Gráficos 4 a 6 e seis cartões de indicadores não estão neste repositório; falta a frente da Pessoa 6.
- A interface web já mostra efeitos de funções, procedimentos, triggers e log. A nova janela Swing também mostra o efeito dos dois triggers.
- A auditoria e as consultas avançadas da Etapa 4 precisam de integração à interface para cumprir toda a entrega coletiva. O script 05 atual publica apenas duas consultas e dois índices; confirmar com a Pessoa 3 as quatro consultas previstas.
- `GRANT EXECUTE ON match_musical.* TO 'sync_app'@'localhost'` está no reset. Rotinas com `DELIMITER` são carregadas pelo cliente `mysql`, não pelo JDBC.

## Verificação executada

O `00_reset.sql` concluiu sem erro na instância temporária. `08_verificacao_etapa06.sql` retornou 66 swipes, 35 matches, 38 mensagens, 0 logs, 15 matches com like mútuo, 2 matches sem mensagem, 2 views, 2 funções, 2 procedimentos e 2 triggers. As séries semanais de swipes e matches cobrem setembro, outubro e novembro de 2026. Antes dos testes de escrita, todas as tabelas originais do esquema tinham pelo menos 30 linhas; `Log_Match` começa em zero para que o trigger possa demonstrar a primeira mudança.

Passaram `Etapa6IntegrationCheck`, `SwipeMatchDAOIntegrationCheck`, `MusicoDAOIntegrationCheck` e `VagaDAOIntegrationCheck`. O teste novo confirmou filtros dos gráficos 1 a 3, alteração da contagem de decisões após inserir/excluir swipe, CRUD de Swipe/Match e registro do log. O reset foi repetido após ajustar o seed. Ainda falta ensaio visual e cronometrado da interface Swing/web no ambiente da apresentação.
