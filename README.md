# SYNC

Projeto Java/JDBC com MySQL para pareamento de musicos e grupos musicais.

## Preparar o banco

Em uma base nova, execute `sql/01_criacao_tabelas.sql` e depois `sql/02_insercao_dados.sql` no MySQL Workbench. O primeiro script cria o banco `match_musical`; o segundo insere dados de exemplo. Execute a carga de exemplo somente uma vez em cada banco novo. O modelo atual permite varios generos por grupo por meio de `Grupo_Genero`.

Se voce ja tem a base antiga da Entrega 02, faca um backup e execute apenas `sql/03_migracao_entrega03.sql`. A migracao preserva os dados e move o genero anteriormente salvo em `Grupo_Musical` para `Grupo_Genero`; nao rode o script de carga novamente nessa base.

## Configurar a conexao

Defina as variaveis de ambiente no terminal que executa o Java:

```sh
export SYNC_DB_URL='jdbc:mysql://localhost:3306/match_musical'
export SYNC_DB_USER='sync_app'
export SYNC_DB_PASSWORD='sua_senha_local'
```

`SYNC_DB_URL` e `SYNC_DB_USER` usam os valores acima por padrao; a senha vazia e o padrao se a variavel nao estiver definida. O login salvo com `mysql_config_editor` vale para o cliente `mysql`, mas nao configura o JDBC: defina `SYNC_DB_PASSWORD` ao executar a interface. `.env.example` e apenas um modelo; o projeto nao carrega arquivos `.env` automaticamente. Nao salve credenciais reais no repositorio.

## Compilar

Use um JDK 17 ou superior e execute `mvn package`. O `pom.xml` baixa o MySQL Connector/J e compila as classes existentes em `src/`. Com as variaveis de conexao configuradas, rode `mvn exec:java` para abrir a tela de musicos (ou execute `Main` pela IDE). O botao **Vagas** abre a tela de vagas. A classe `ConnectionFactory` abre conexoes com o banco; `BaseDAO` fornece consultas e atualizacoes com `PreparedStatement`, alem de um helper para transacoes. `MusicoDAO` implementa o CRUD de `Usuario` + `Musico`; `VagaDAO` implementa o CRUD de `Vaga` + `Requisito_Vaga`.

Na tela de musicos, selecione uma linha para editar ou excluir. Ao atualizar, deixe a senha vazia para mante-la. A exclusao pede confirmacao e remove o usuario e seus registros dependentes pelas FKs com `ON DELETE CASCADE`.

Na tela de vagas, escolha um grupo musical, informe situacao, funcao, nivel e requisitos (um por linha). Selecione uma vaga na tabela para alterar ou excluir. Os requisitos sao numerados dentro de cada vaga; ao excluir a vaga, seus requisitos sao removidos em cascata.

Para repetir o teste de integracao do DAO em um banco de teste com as variaveis `SYNC_DB_*` configuradas, use `mvn test-compile exec:java -Dexec.mainClass=MusicoDAOIntegrationCheck -Dexec.classpathScope=test`. O teste cria e remove um musico de teste, verificando tambem rollback e exclusao em cascata.

Para testar o DAO de vagas no mesmo banco de teste, use `mvn test-compile exec:java -Dexec.mainClass=VagaDAOIntegrationCheck -Dexec.classpathScope=test`. Ele cria e remove vagas de teste, incluindo requisitos, rollback e erro de chave estrangeira.
