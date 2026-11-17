# SYNC

Aplicativo de pareamento (estilo *swipe*) entre musicos e grupos musicais — projeto da disciplina de Banco de Dados, CESAR School.

Etapa 06: interface Swing com CRUD e dashboard, interface web para rotinas e views, back-end em Java + JDBC + MySQL. Todo acesso ao banco e feito com **SQL explicito** enviado por `PreparedStatement`, sem ORM ou framework de abstracao.

## Caminho rapido para demonstracao da Etapa 06

1. Instale JDK 17+, Maven 3.9+ e MySQL 8+. Inicie o servidor MySQL. Crie o usuario `sync_app` conforme a secao 1 abaixo.
2. Exporte `SYNC_DB_URL`, `SYNC_DB_USER` e `SYNC_DB_PASSWORD` conforme a secao 2. A senha deve ficar fora do repositorio.
3. Na raiz do projeto, execute o reset **destrutivo**, antes de cada ensaio:

   ```sh
   mysql --commands --default-character-set=utf8mb4 -u root -p -e "source sql/00_reset.sql"
   mysql -u sync_app -p match_musical < sql/08_verificacao_etapa06.sql
   ```

   `00_reset.sql` executa `01_criacao_tabelas`, `02_insercao_dados`, o complemento de demonstracao, `05_consultas_avancadas`, as duas views e `06_funcoes_procedimentos_triggers`, nesta ordem. `DELIMITER` e tratado pelo cliente `mysql`, nunca pelo JDBC. A verificacao deve mostrar **66 swipes, 35 matches, 38 mensagens, 0 linhas de log, 15 matches com like mutuo e 2 matches sem mensagem**, alem de 2 views, 2 funcoes, 2 procedimentos e 2 triggers. Se um objeto falhar, pare e corrija antes de apresentar.
4. Execute `mvn package` e `mvn exec:java` para abrir o Swing. Para a interface web, em outro terminal, use `mvn compile exec:java "-Dexec.mainClass=WebMain"` e abra `http://127.0.0.1:8080`.
5. Siga [`docs/roteiro_5_minutos_etapa06.md`](docs/roteiro_5_minutos_etapa06.md). O contrato real de objetos e pendencias coletivas esta em [`docs/contrato_etapa06.md`](docs/contrato_etapa06.md).

Se o MySQL nao aceitar conexao, confira se o servidor esta ativo, a porta do `SYNC_DB_URL` e as credenciais. Se a aplicacao receber erro 1370 ao chamar rotina, confira `GRANT EXECUTE` para `sync_app`. O reset exige usuario com permissoes de criacao, trigger, rotina e `GRANT`.

## Requisitos

- JDK 17 ou superior
- Maven 3.9+ (baixa o driver `mysql-connector-j` automaticamente)
- MySQL 8 ou superior (Workbench ou cliente `mysql`)

## 1. Preparar o banco

**Base nova (recomendado):** execute, nesta ordem, no MySQL Workbench ou no terminal:

```sh
mysql -u root -p < sql/01_criacao_tabelas.sql   # cria o banco match_musical e as 21 tabelas
mysql -u root -p < sql/02_insercao_dados.sql    # carga de exemplo (execute uma unica vez por banco)
```

**Base antiga da Entrega 02:** faca backup e execute somente `sql/03_migracao_entrega03.sql`. Ele preserva os dados, move o genero que estava direto em `Grupo_Musical` para a tabela `Grupo_Genero` e acrescenta nomes, `ON DELETE/UPDATE`, `CHECK` e `DEFAULT` nas constraints. Nao rode a carga `02` de novo nessa base.

Crie um usuario so para a aplicacao (evita usar o `root` no Java):

```sql
CREATE USER 'sync_app'@'localhost' IDENTIFIED BY 'sua_senha_local';
GRANT SELECT, INSERT, UPDATE, DELETE ON match_musical.* TO 'sync_app'@'localhost';
```

## 2. Configurar a conexao

A classe `ConnectionFactory` le variaveis de ambiente, entao nenhuma senha fica no codigo:

```sh
export SYNC_DB_URL='jdbc:mysql://localhost:3306/match_musical'
export SYNC_DB_USER='sync_app'
export SYNC_DB_PASSWORD='sua_senha_local'
```

`SYNC_DB_URL` e `SYNC_DB_USER` assumem os valores acima se nao forem definidas; a senha padrao e vazia. `.env.example` e apenas um modelo — o projeto nao carrega `.env` sozinho. Um login salvo com `mysql_config_editor` vale para o cliente `mysql`, nao para o JDBC. Nao salve credenciais reais no repositorio.

Na IntelliJ, configure as mesmas variaveis em *Run > Edit Configurations > Environment variables*.

## 3. Compilar e executar

```sh
mvn package        # compila src/ e baixa o driver JDBC
mvn exec:java      # abre a tela de Musicos (classe Main)
```

Tambem da para executar `Main` direto pela IDE.

## Interface web responsiva (em desenvolvimento)

A nova interface mobile first esta em [`web/`](web/README.md), com Inicio, Descobrir, Vagas, Matches e Perfil. O servidor local le o MySQL pelo mesmo `ConnectionFactory` do app Swing. Inicie com `mvn compile exec:java "-Dexec.mainClass=WebMain"` (as aspas sao necessarias no PowerShell) e abra `http://127.0.0.1:8080`. Sem banco, a tela mostra dados de demonstracao.

A selecao de perfil ainda nao e login; por seguranca, o servidor atende apenas em `127.0.0.1`. Consulte [`web/README.md`](web/README.md) para detalhes.

### Views, funcoes, procedimentos e triggers na interface (Etapa 6)

Para recriar o banco com todos os objetos das Etapas 4 e 5, rode na raiz do repositorio, como `root`:

```sh
mysql --commands --default-character-set=utf8mb4 -u root -p -e "source sql/00_reset.sql"
```

O `00_reset.sql` apaga o `match_musical`, roda `01`, `02`, `02_complemento_demonstracao`, `05`, as duas views e o `06_funcoes_procedimentos_triggers.sql`, e da ao `sync_app` a permissao `EXECUTE`. Use antes de cada ensaio, porque a demonstracao grava swipes, matches e participacoes.

| Onde na interface | O que mostra | Objeto do banco |
|---|---|---|
| Vagas → *Recomendadas para voce* | Vagas com generos em comum, filtros por funcao e nivel, e 3 indicadores | `vw_recomendacao_vagas`, `fn_compatibilidade_vaga` |
| Descobrir → detalhes do grupo | Integrantes ativos do grupo | `vw_integrantes_grupo` |
| Perfil | Percentual de likes retribuidos | `fn_taxa_retribuicao_usuario` |
| Descobrir → Like | Aviso "Deu match" quando o like e mutuo | `tr_swipe_cria_match` |
| Matches → *Entrar no grupo* | Converte o match em participacao; recusas aparecem como aviso | `sp_converter_match_em_participacao` |
| Matches → *Historico das conexoes* | Mudancas de situacao das conexoes do perfil, com filtros | `tr_match_log_situacao` (tabela `Log_Match`) |
| Vagas → *Sincronizar grupos* | Grupos atualizados e vagas fechadas | `sp_sincronizar_situacao_grupos` |

O acesso fica em `ViewsFuncoesDAO` e `ProcedimentosTriggersDAO` (SQL explicito com `?`). Funcoes sao chamadas com `SELECT fn(?)` e procedimentos com `CallableStatement`. Se faltar um objeto no banco, so a secao dele mostra um aviso.

## Telas

| Tela | Como abrir | O que faz | Tabelas |
|---|---|---|---|
| **Musicos** | inicial | Cadastrar, alterar e excluir musicos. Insere `Usuario` + `Musico` numa unica transacao. Ao alterar, senha vazia mantem a atual. A exclusao pede confirmacao e remove dependentes pelas FKs `ON DELETE CASCADE`. | `Usuario`, `Musico` |
| **Vagas** | botao **Vagas** | Cadastrar, alterar e excluir vagas de um grupo, com requisitos (um por linha). Os requisitos sao numerados dentro de cada vaga (entidade fraca, PK composta `id_vaga, id_requisito`) e saem em cascata quando a vaga e excluida. | `Vaga`, `Requisito_Vaga` |
| **Swipes e Matches** | botao **Swipes e Matches** | CRUD completo nas duas tabelas; a aba de matches mostra o log do match selecionado. Inserir swipe pode criar match por trigger; alterar situacao gera log. | `Swipe`, `Match`, `Log_Match` (leitura) |
| **Dashboard** | botao **Dashboard** | Aba *Visao geral*: 4 indicadores, graficos 1 a 3 com filtros de categoria, habilidade e periodo, mais os 2 graficos antigos. Aba *Consultas*: escolhe uma das 8 consultas, executa e mostra o resultado e o SQL enviado ao banco. | todas (leitura) |

Os graficos sao desenhados com Java2D (`GraficoBarrasPanel`), sem biblioteca externa.

## Consultas SQL

As 8 consultas estao comentadas em `sql/04_consultas.sql`. O mesmo SQL e usado no Java (`ConsultaSQL.java`), e o dashboard executa cada uma pelo `DashboardDAO`.

| # | Pergunta que responde | Tecnicas |
|---|---|---|
| 1 | Quais vagas estao abertas, de qual grupo e cidade? | `INNER JOIN` + `WHERE` |
| 2 | Quantos musicos tocam cada instrumento e com que experiencia media? | `LEFT JOIN` + `GROUP BY` + `COUNT/AVG` |
| 3 | Quais grupos nao tem nenhuma vaga aberta? | subconsulta correlacionada `NOT EXISTS` + subconsulta escalar |
| 4 | Quais matches estao ativos e quantas mensagens cada um tem? | mesma tabela com 2 aliases + `LEFT JOIN` + `GROUP BY` |
| 5 | Quais musicos tocam mais de um instrumento? | JOIN de 3 tabelas (N:N) + `HAVING` + `GROUP_CONCAT` |
| 6 | Para quais vagas abertas um musico ja tem nivel suficiente? | JOIN + subconsulta escalar no `WHERE` + parametro `?` |
| 7 | Em quais generos ha muitos grupos e poucos musicos interessados? | 2 subconsultas escalares sobre tabelas associativas |
| 8 | Quais musicos ja tocaram juntos e em quantos grupos em comum? | auto-relacionamento + self-join em `Participa` |

Por que `LEFT JOIN` na 2 e na 4: com `JOIN` simples, instrumentos sem musico e matches sem mensagem sumiriam do resultado. Por que `NOT EXISTS` na 3: diferente de `NOT IN`, nao quebra se a subconsulta devolver `NULL`.

### Consulta de auditoria Swipe x Match (Etapa 4, item 5)

Arquivo: `sql/consulta_full_outer_join_swipe_match.sql`. Responde: "quais pares de usuarios tem interesse mutuo nos swipes mas nao possuem `Match`, e quais `Match`es existem sem interesse mutuo registrado?". O MySQL nao tem `FULL OUTER JOIN`, entao ele e emulado com `LEFT JOIN` + `UNION ALL` + `RIGHT JOIN ... WHERE ... IS NULL`. A consulta tambem usa CTE (exige MySQL 8 ou superior), auto join em `Swipe` e `LEAST`/`GREATEST` para normalizar o par, ja que `Match` nao tem ordem fixa entre `id_usuario_a` e `id_usuario_b`.

| diagnostico | Significado |
|---|---|
| `Consistente` | interesse mutuo nos swipes e `Match` registrado |
| `Like mutuo sem match` | os dois usuarios demonstraram interesse, mas nao ha `Match` |
| `Match sem like mutuo` | existe `Match` sem interesse mutuo registrado nos swipes |

Decisoes: `Like` e `Super Like` contam como interesse (`Dislike` nao); o resultado tem uma linha por `Match`; swipes repetidos na mesma direcao sao absorvidos por `DISTINCT`, ja que `Swipe` nao tem `UNIQUE`.

Com apenas o `02_insercao_dados.sql`, o resultado e de 30 linhas (2 `Consistente`, 28 `Match sem like mutuo`). O reset da Etapa 06 tambem aplica `02_complemento_demonstracao.sql`, elevando o resultado para **35 linhas** (15 `Consistente`, 20 `Match sem like mutuo`). O teste antigo `sql/teste_like_mutuo_sem_match_rollback.sql` foi escrito para o seed sem trigger e nao deve ser usado na base resetada com `tr_swipe_cria_match` ativo. Execute a auditoria a partir da pasta `sql/`:

```sh
mysql -u sync_app -p match_musical < consulta_full_outer_join_swipe_match.sql
```

Esta consulta ainda nao aparece no dashboard: `ConsultaSQL.java` guarda so as 8 consultas da Etapa 3.

## Estrutura de pastas

```
match-musical/
├── pom.xml                         dependencia mysql-connector-j e plugins de build/execucao
├── .env.example                    modelo das variaveis SYNC_DB_*
├── sql/
│   ├── 01_criacao_tabelas.sql      DDL final (fonte de verdade: Esquema Relacional)
│   ├── 02_insercao_dados.sql       carga de exemplo (~30 linhas por tabela)
│   ├── 02_complemento_demonstracao.sql   reciprocidade e series temporais da Etapa 06
│   ├── 00_reset.sql                reset destrutivo e reproducivel
│   ├── 08_verificacao_etapa06.sql  conferencias apos o reset
│   ├── 03_migracao_entrega03.sql   converte uma base da Entrega 02 para o esquema atual
│   ├── 04_consultas.sql            as 8 consultas comentadas
│   ├── consulta_full_outer_join_swipe_match.sql   auditoria Swipe x Match (Etapa 4): FULL OUTER JOIN emulado
│   └── teste_like_mutuo_sem_match_rollback.sql    teste da auditoria, dentro de transacao com ROLLBACK
├── src/
│   ├── Main.java                   ponto de entrada (abre MusicoFrame)
│   ├── ConnectionFactory.java      abre conexoes JDBC a partir das variaveis de ambiente
│   ├── BaseDAO.java                helpers com PreparedStatement e transacao (commit/rollback)
│   ├── UsuarioDAO.java, MusicoDAO.java    CRUD Usuario + Musico
│   ├── VagaDAO.java                CRUD Vaga + Requisito_Vaga
│   ├── SwipeMatchDAO.java          CRUD Swipe + Match
│   ├── DashboardDAO.java           indicadores e execucao generica das consultas
│   ├── ConsultaSQL.java            titulo, pergunta e SQL de cada consulta
│   ├── Usuario.java, Musico.java, GrupoMusical.java, Vaga.java, RequisitoVaga.java   modelos
│   ├── MusicoFrame.java, VagaFrame.java, SwipeMatchFrame.java, DashboardFrame.java   telas Swing
│   └── GraficoBarrasPanel.java, GraficoPizzaPanel.java   graficos Java2D
├── tests/
│   ├── MusicoDAOIntegrationCheck.java
│   └── VagaDAOIntegrationCheck.java
└── docs/
    ├── roteiro_teste.md            roteiro historico da Entrega 03
    ├── contrato_etapa06.md         objetos reais e pendencias de integracao
    └── roteiro_5_minutos_etapa06.md   demonstracao cronometrada
```

## Decisoes de modelagem

- **O Esquema Relacional e a fonte de verdade** para o `CREATE TABLE`. O Modelo Logico da Entrega 02 esta desatualizado (faltam `Participa` e `Interesse`, e `Links` tinha referencia e tipo errados) e nao deve ser usado como referencia do SQL.
- **Grupo Musical x Genero Musical e N:N**, pela tabela associativa `Grupo_Genero`, como o Minimundo descreve. Na Entrega 02 o grupo tinha um unico genero por FK direta; o script `03` faz a conversao.
- **Especializacoes:** `Musico` e `Grupo_Musical` sao subtipos disjuntos de `Usuario` (PK = FK). `Banda`, `Orquestra` e `Coral` particionam `Grupo_Musical`. O MySQL nao tem constraint nativa para disjuncao entre tabelas, entao ela e garantida pela carga e pela aplicacao.
- **Regras de FK:** `CASCADE` quando a linha depende existencialmente da outra (mensagem → match, requisito → vaga). `RESTRICT` em tabelas de dominio (`Instrumento`, `Genero_Musical`). `SET NULL` na localizacao opcional do usuario.

## Testes

Com as variaveis `SYNC_DB_*` configuradas e apontando para um **banco de teste**:

```sh
mvn test-compile exec:java -Dexec.mainClass=MusicoDAOIntegrationCheck -Dexec.classpathScope=test
mvn test-compile exec:java -Dexec.mainClass=VagaDAOIntegrationCheck  -Dexec.classpathScope=test
mvn test-compile exec:java -Dexec.mainClass=Etapa6IntegrationCheck   -Dexec.classpathScope=test
mvn test-compile exec:java -Dexec.mainClass=SwipeMatchDAOIntegrationCheck -Dexec.classpathScope=test
```

Os testes de musico e vaga cobrem insercao, listagem, alteracao, rollback e exclusao; o de vagas cobre tambem FK. `Etapa6IntegrationCheck` exige o banco do `00_reset.sql` e confere views, funcoes, erros do procedimento e triggers com `ROLLBACK`. `SwipeMatchDAOIntegrationCheck` verifica o novo CRUD e os filtros dos graficos; ele deixa uma linha de auditoria no `Log_Match`, portanto **execute o reset novamente antes de apresentar**. No PowerShell, ponha aspas em cada `-D...`.

O roteiro manual, tela por tela, esta em [`docs/roteiro_teste.md`](docs/roteiro_teste.md).

## Uso de IA

A disciplina pede que o grupo informe onde usou IA e como validou o que ela produziu.

| Onde | Ferramenta | O que a IA fez | Como o grupo validou |
|---|---|---|---|
| Verificacao da Entrega 03 e documentacao | Claude Code | Subiu um MySQL local, rodou `01` + `02`, conferiu integridade (disjuncao Musico/Grupo, particao Banda/Orquestra/Coral, autor de mensagem pertencente ao match), executou as 8 consultas pelo `DashboardDAO`, rodou os dois testes de integracao, testou o script `03` sobre o esquema da Entrega 02 e escreveu este README e o roteiro de teste. Tambem alinhou o `ORDER BY` da consulta 6 entre `04_consultas.sql` e `ConsultaSQL.java`. | *(Validamos manualmente todos os requisitos e consultas)* |
| Construcao do codigo (as 3 etapas que fiz) | Claude | Guiou a construcao do codigo, tirou duvidas e ajudou a entender os erros que apareciam | *(Validamos manualmente todos os requisitos e consultas)* |
| Validacao da Fase 6 | Claude | Ajudou a validar a fase 6 | *(Validamos manualmente todos os requisitos e consultas)* |
| Consultas SQL (as 8 consultas) | Claude | Apoio para elaborar as 8 consultas e para corrigir erros no codigo | *(Validamos manualmente todos os requisitos e consultas)* |
| Classes ConsultaSQL, DashboardDAO, DashboardFrame e GraficoBarrasPanel | Claude | Ajudou a criar essas classes | *(Validamos manualmente todos os requisitos e consultas)* |
| Consulta de auditoria Swipe x Match (Etapa 4, item 5) | Claude e Claude Code | O Claude gerou a consulta (FULL OUTER JOIN emulado) e o script de teste com rollback. O Claude Code levantou antes o estado do projeto (DDL de `Swipe` e `Match`, carga, versao do MySQL, consultas existentes), rodou a consulta e o teste no MySQL local e interpretou o `EXPLAIN`. | Consulta executada no MySQL local: 30 linhas (2 `Consistente`, 28 `Match sem like mutuo`), cada `Match` aparece uma vez (30 ids distintos = `COUNT(*)` de `Match`). Teste com `ROLLBACK`: 31 linhas, com 1 `Like mutuo sem match` para o par (1,3), e `Swipe` voltou a 40 linhas. |
| Etapa 6, tarefas 4 e 5 (interface web) | Claude Code | Consolidou o `06` e criou o `00_reset.sql`; escreveu `ViewsFuncoesDAO`, `ProcedimentosTriggersDAO`, as telas em `web/` e o `Etapa6IntegrationCheck`; revisou textos e layout da interface. | Testes manuais da secao 7 do roteiro no `localhost` (match pelo trigger, conversao, historico, sincronizacao e aviso de erro do procedimento) e `Etapa6IntegrationCheck` passando com `ROLLBACK`. |
| Etapa 06, frentes das Pessoas 1 e 2 | Codex | Acrescentou o complemento do seed, verificacao do reset, contrato e roteiro; implementou CRUD Swing de Swipe/Match e graficos 1 a 3 com consultas JDBC. | Reset em MySQL 8.4 isolado: 66 swipes, 35 matches, 38 mensagens, 15 pares reciprocos e 2 matches sem mensagem. Passaram `Etapa6IntegrationCheck`, `SwipeMatchDAOIntegrationCheck`, `MusicoDAOIntegrationCheck` e `VagaDAOIntegrationCheck`. Falta ensaio visual e cronometrado. |

*(preencher: a regra que o grupo seguiu para aceitar o que a IA gerou — por exemplo, "nenhum SQL entrou sem ser executado no MySQL e revisado por um integrante que soubesse explica-lo")*
