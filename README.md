# SYNC

Aplicativo de pareamento (estilo *swipe*) entre musicos e grupos musicais — projeto da disciplina de Banco de Dados, CESAR School.

Entrega 03: interface Swing com CRUD e dashboard, back-end em Java + JDBC + MySQL. Todo acesso ao banco e feito com **SQL explicito** enviado por `PreparedStatement`, sem ORM ou framework de abstracao.

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

## Telas

| Tela | Como abrir | O que faz | Tabelas |
|---|---|---|---|
| **Musicos** | inicial | Cadastrar, alterar e excluir musicos. Insere `Usuario` + `Musico` numa unica transacao. Ao alterar, senha vazia mantem a atual. A exclusao pede confirmacao e remove dependentes pelas FKs `ON DELETE CASCADE`. | `Usuario`, `Musico` |
| **Vagas** | botao **Vagas** | Cadastrar, alterar e excluir vagas de um grupo, com requisitos (um por linha). Os requisitos sao numerados dentro de cada vaga (entidade fraca, PK composta `id_vaga, id_requisito`) e saem em cascata quando a vaga e excluida. | `Vaga`, `Requisito_Vaga` |
| **Dashboard** | botao **Dashboard** | Aba *Visao geral*: 4 indicadores e 2 graficos gerados pelas consultas 2 e 7. Aba *Consultas*: escolhe uma das 8 consultas, executa e mostra o resultado e o SQL enviado ao banco. | todas (leitura) |

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

## Estrutura de pastas

```
match-musical/
├── pom.xml                         dependencia mysql-connector-j e plugins de build/execucao
├── .env.example                    modelo das variaveis SYNC_DB_*
├── sql/
│   ├── 01_criacao_tabelas.sql      DDL final (fonte de verdade: Esquema Relacional)
│   ├── 02_insercao_dados.sql       carga de exemplo (~30 linhas por tabela)
│   ├── 03_migracao_entrega03.sql   converte uma base da Entrega 02 para o esquema atual
│   └── 04_consultas.sql            as 8 consultas comentadas
├── src/
│   ├── Main.java                   ponto de entrada (abre MusicoFrame)
│   ├── ConnectionFactory.java      abre conexoes JDBC a partir das variaveis de ambiente
│   ├── BaseDAO.java                helpers com PreparedStatement e transacao (commit/rollback)
│   ├── UsuarioDAO.java, MusicoDAO.java    CRUD Usuario + Musico
│   ├── VagaDAO.java                CRUD Vaga + Requisito_Vaga
│   ├── DashboardDAO.java           indicadores e execucao generica das consultas
│   ├── ConsultaSQL.java            titulo, pergunta e SQL de cada consulta
│   ├── Usuario.java, Musico.java, GrupoMusical.java, Vaga.java, RequisitoVaga.java   modelos
│   ├── MusicoFrame.java, VagaFrame.java, DashboardFrame.java                         telas Swing
│   └── GraficoBarrasPanel.java     grafico de barras em Java2D
├── tests/
│   ├── MusicoDAOIntegrationCheck.java
│   └── VagaDAOIntegrationCheck.java
└── docs/
    └── roteiro_teste.md            roteiro de teste ponta a ponta
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
```

Cada teste cria e remove os proprios dados e verifica insercao, listagem, alteracao, rollback de transacao e exclusao em cascata. O teste de vagas tambem cobre o erro de chave estrangeira.

O roteiro manual, tela por tela, esta em [`docs/roteiro_teste.md`](docs/roteiro_teste.md).

## Uso de IA

A disciplina pede que o grupo informe onde usou IA e como validou o que ela produziu.

| Onde | Ferramenta | O que a IA fez | Como o grupo validou |
|---|---|---|---|
| Verificacao da Entrega 03 e documentacao | Claude Code | Subiu um MySQL local, rodou `01` + `02`, conferiu integridade (disjuncao Musico/Grupo, particao Banda/Orquestra/Coral, autor de mensagem pertencente ao match), executou as 8 consultas pelo `DashboardDAO`, rodou os dois testes de integracao, testou o script `03` sobre o esquema da Entrega 02 e escreveu este README e o roteiro de teste. Tambem alinhou o `ORDER BY` da consulta 6 entre `04_consultas.sql` e `ConsultaSQL.java`. | *(preencher: quem revisou e o que conferiu)* |
| *(preencher: ex. DDL / FKs)* | *(ferramenta)* | *(o que ela sugeriu)* | *(ex.: rodamos o script no Workbench, comparamos com o Esquema Relacional)* |
| *(preencher: ex. DAOs / telas)* | | | |
| *(preencher: ex. consultas / dashboard)* | | | |

*(preencher: a regra que o grupo seguiu para aceitar o que a IA gerou — por exemplo, "nenhum SQL entrou sem ser executado no MySQL e revisado por um integrante que soubesse explica-lo")*
