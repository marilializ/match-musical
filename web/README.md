# Interface web do SYNC

Interface responsiva para celular e desktop. As telas usam o MySQL do projeto quando o servidor Java esta ativo; sem conexao, a interface entra em modo demonstracao com dados ficticios.

## Executar

Na raiz do repositorio, configure as mesmas variaveis de banco usadas pelo aplicativo Swing:

```sh
export SYNC_DB_URL='jdbc:mysql://localhost:3306/match_musical'
export SYNC_DB_USER='sync_app'
export SYNC_DB_PASSWORD='sua_senha_local'
mvn compile exec:java -Dexec.mainClass=WebMain
```

Abra http://127.0.0.1:8080 no navegador. A porta pode ser alterada com `SYNC_WEB_PORT`. O servidor escuta apenas em `127.0.0.1`.

As paginas Inicio, Descobrir, Vagas, Matches e Perfil leem dados do DDL e do seed existentes. A pessoa selecionada no topo e um perfil de demonstracao. Os botoes de swipe gravam em `Swipe` somente quando a interface esta conectada ao banco; no modo demonstracao, alteram apenas os dados da pagina. Um Match automatico depende do trigger da Etapa 5, ainda pendente no repositorio.

**Acesso:** ainda nao existe login. Por isso esta versao e apenas local; nao publique o servidor na rede nem use perfis de usuarios reais ate implementar autenticacao.
