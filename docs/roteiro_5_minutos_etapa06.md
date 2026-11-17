# Roteiro de 5 minutos — Etapa 06

**Estado inicial:** executar o reset da raiz do projeto antes de cada ensaio e apresentação. O reset apaga o banco e repõe 66 swipes, 35 matches, 38 mensagens e log vazio. Deixar a aplicação Swing aberta em **Músicos** e a interface web aberta em `http://127.0.0.1:8080`, perfil **Pedro Guitar (#2)**. O match novo do passo 2 só aparece se o grupo #64 estiver ativo. Cronometrar uma vez no ambiente da apresentação; os tempos são alvos, não medição já realizada.

| Tempo | Tela e ação | Resultado esperado | Altera banco? |
| --- | --- | --- | --- |
| 0:00–0:40 | Swing → **Dashboard → Visão geral**. Mostrar músicos por nível, instrumentos com média de anos e pizza de decisões. Aplicar período `2026-10-01` a `2026-11-03` e voltar ao período vazio. | Gráfico 3 muda; os dados vêm de `DashboardDAO`. | Não |
| 0:40–1:30 | Swing → **Swipes e Matches**. Mostrar listagens. Inserir swipe `Like` de #2 para #64 (já existe o inverso no seed). | Novo swipe e novo match; atualizar o dashboard para ver a pizza mudar. | Sim |
| 1:30–2:10 | Na aba **Matches e log**, selecionar o match novo e alterar situação de `Ativo` para `Encerrado`. | Linha `Ativo → Encerrado` aparece no histórico logo abaixo. | Sim |
| 2:10–3:00 | Web → **Vagas → Recomendadas para você**, filtrar função e nível; depois **Perfil**. | View e função de compatibilidade mudam recomendações; taxa de retribuição é exibida. | Não |
| 3:00–3:40 | Web → **Vagas → Sincronizar grupos**. | Aviso com quantidades; segundo clique deve retornar zero. | Sim |
| 3:40–4:25 | Swing → **Vagas**. Criar vaga aberta, editar para fechada e excluir. Reabrir dashboard e atualizar. | Persistência CRUD e indicador de vagas abertas variam. | Sim |
| 4:25–5:00 | Swing → **Dashboard → Consultas**. Executar Consulta 2 e Consulta 6 com músicos diferentes; indicar a SQL e o parâmetro `?`. | Resultados dependem do banco e do músico escolhido. | Não |

**Depois do ensaio:** executar `sql/00_reset.sql` novamente e conferir `sql/08_verificacao_etapa06.sql`. Não usar o teste de “like mútuo sem match” enquanto `tr_swipe_cria_match` estiver ativo; o estado inicial já tem casos consistentes e matches sem like mútuo.

**Troca de explicações para a nota individual:** Pessoa 1 explica o `PreparedStatement` e o efeito do trigger no CRUD da Pessoa 2; Pessoa 2 explica a ordem do reset, o complemento do seed e a função de retribuição. Pessoas 3 a 6 devem escolher, cada uma, um trecho de outro integrante e praticar a explicação antes da apresentação individual.
