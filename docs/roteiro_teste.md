# Roteiro de teste ponta a ponta — Entrega 03

Este roteiro registra a base historica com apenas `01` + `02`. Para a Etapa 06, use [`roteiro_5_minutos_etapa06.md`](roteiro_5_minutos_etapa06.md) e [`../sql/08_verificacao_etapa06.sql`](../sql/08_verificacao_etapa06.sql): o complemento da carga muda as contagens de swipes, matches, mensagens e matches ativos.

Use este roteiro no checkpoint de integracao e antes do envio. Cada integrante deve conseguir rodar tudo sozinho.

Os valores esperados abaixo valem para um banco **recem-criado** com `01` + `02`. Se voce ja cadastrou ou excluiu dados, os numeros mudam — recrie o banco para comparar.

## 0. Preparacao

- [ ] `DROP DATABASE IF EXISTS match_musical;` e depois rodar `sql/01_criacao_tabelas.sql` e `sql/02_insercao_dados.sql` sem erro.
- [ ] Conferir: `SELECT COUNT(*) FROM Usuario;` → **120** (30 musicos + 90 grupos).
- [ ] Usuario `sync_app` criado (ver README) e variaveis `SYNC_DB_*` exportadas no terminal ou na IDE.
- [ ] `mvn package` termina sem erro.

## 1. Testes automaticos

- [ ] `MusicoDAOIntegrationCheck` imprime `CRUD Musico: insercao, listagem, alteracao, rollback e exclusao OK`.
- [ ] `VagaDAOIntegrationCheck` imprime `CRUD Vaga: insercao, listagem, alteracao, rollback e cascata OK`.

(Comandos no README, secao *Testes*.)

## 2. Tela de Musicos — `mvn exec:java`

- [ ] A tabela lista **30** musicos.
- [ ] **Inserir:** preencher um musico novo (usuario e e-mail ineditos) → *Cadastrar* → "Musico cadastrado." e ele aparece na tabela.
  - No MySQL: `SELECT * FROM Usuario u JOIN Musico m USING(id_usuario) WHERE u.email = '<e-mail usado>';` devolve 1 linha.
- [ ] **E-mail duplicado:** cadastrar outro musico com o mesmo e-mail → erro "Usuario ou e-mail ja cadastrado." e nada e gravado (nem em `Usuario`: a transacao desfaz tudo).
- [ ] **Alterar:** selecionar o musico criado, mudar nome artistico e nivel, deixar a senha vazia → *Atualizar selecionado* → "Musico atualizado.". Conferir no banco que a senha nao mudou.
- [ ] **Excluir:** selecionar o musico criado → *Excluir selecionado* → confirmar → "Musico excluido.". Conferir que sumiu de `Usuario` e de `Musico`.
- [ ] **Cascata:** excluir um musico da carga que tenha instrumentos (ex.: id 1) e verificar que `SELECT COUNT(*) FROM Toca WHERE id_musico = 1;` → 0. *(Recrie o banco depois deste passo.)*

## 3. Tela de Vagas — botao **Vagas**

- [ ] A tabela lista **35** vagas.
- [ ] **Inserir:** escolher um grupo, situacao *Aberta*, funcao, nivel minimo e 2 requisitos (um por linha) → *Cadastrar* → "Vaga cadastrada.".
  - No MySQL: `SELECT * FROM Requisito_Vaga WHERE id_vaga = <nova>;` → requisitos 1 e 2.
- [ ] **Alterar:** selecionar a vaga, trocar a situacao para *Fechada* e deixar so 1 requisito → *Atualizar selecionada*. O requisito 2 deve sumir do banco.
- [ ] **Excluir:** selecionar a vaga → *Excluir selecionada* → confirmar. `Requisito_Vaga` nao deve ter mais linhas dessa vaga (cascata da entidade fraca).

## 4. Dashboard — botao **Dashboard**

**Aba Visao geral** (banco recem-criado):

- [ ] Indicadores: Musicos **30**, Grupos **90**, Vagas abertas **27**, Matches ativos **11**.
- [ ] Grafico "Musicos por instrumento" com Acordeon, Harpa e Violao no topo (4 cada).
- [ ] Grafico "Oferta x procura" com Musica Barroca no topo (20 grupos, 1 musico interessado).
- [ ] Cadastrar uma vaga *Aberta* na tela de Vagas, voltar e clicar *Atualizar dados* → Vagas abertas passa para 28.

**Aba Consultas** — executar cada uma e conferir a quantidade de linhas no rodape:

| Consulta | Linhas esperadas |
|---|---|
| 1. Vagas abertas com grupo e cidade | 27 |
| 2. Musicos por instrumento | 30 |
| 3. Grupos sem vaga aberta | 66 |
| 4. Matches ativos e mensagens | 11 |
| 5. Musicos multi-instrumentistas | 14 |
| 6. Vagas compativeis (musico Ana Silva, #1) | 18 |
| 7. Oferta x procura por genero | 30 |
| 8. Duplas que ja tocaram juntas | 32 |

- [ ] O quadro "SQL enviado ao banco" mostra o mesmo SQL de `sql/04_consultas.sql`.
- [ ] Consulta 6: trocar o musico no combo muda o resultado — um musico *Profissional* (ex.: #5) ve **27** vagas; um *Iniciante* (ex.: #6) ve **0**, porque nenhuma vaga aberta da carga aceita nivel Iniciante.

## 5. Erro de conexao

- [ ] Com `SYNC_DB_PASSWORD` errada, abrir a aplicacao → aparece uma mensagem de erro de acesso, sem travar o programa.

## 7. Etapa 6 — views, funcoes, procedimentos e triggers na interface web

Banco recriado com o reset (como `root`, na raiz do repositorio) e servidor web no ar:

```sh
mysql --commands --default-character-set=utf8mb4 -u root -p -e "source sql/00_reset.sql"
mvn -q compile exec:java "-Dexec.mainClass=WebMain"    # abrir http://127.0.0.1:8080
```

- [ ] O reset lista 2 views, 2 funcoes, 2 procedimentos e 2 triggers, e termina com `linhas_log` = 0.
- [ ] `Etapa6IntegrationCheck` imprime as 3 linhas `... OK` (comando no README, secao *Testes*). Ele nao deixa nada gravado.

**Tarefa 4 — views e funcoes**

- [ ] **Vagas → Para voce** (`vw_recomendacao_vagas` + `fn_compatibilidade_vaga`): lista vagas com generos em comum e o selo *Combina* / *Nivel acima*. A busca (ex.: `gui`) e os chips de nivel mudam a lista; o resumo acima dela (vagas que combinam, quantas o seu nivel atende, total aberto) acompanha o perfil escolhido em **Perfil → Trocar perfil**.
- [ ] **Descobrir → Ver mais → Integrantes** (`vw_integrantes_grupo`): mostra os integrantes ativos com funcao e data de entrada. No celular abre de baixo; a partir de 768 px os detalhes saem de dentro do card, colados a direita.
- [ ] **Perfil** (`fn_taxa_retribuicao_usuario`): o anel mostra o percentual *das suas curtidas voltaram*.
- [ ] Nenhum nome de view ou funcao aparece na tela.

**Pessoas 3 e 6 — consultas e dashboard (Swing)**

- [ ] **Dashboard → Consultas**: o combo lista 12 consultas (1 a 8 da Etapa 3, 9 a 12 da Etapa 4). Na 9 (anti join), escolher uma categoria principal muda a lista; na 10 (subconsulta com `IN`), escolher uma categoria principal; na 12 (auditoria), filtrar *Consistente* e conferir a cor verde de cada linha.
- [ ] **Dashboard → Visao geral**: 7 cartoes (6 indicadores + taxa media de retribuicao) e 6 graficos numerados, alem do de generos. Preencher o periodo muda os cartoes de match e os graficos 3, 4 e 5; trocar situacao do match muda o histograma e as medidas; trocar instrumento muda a dispersao e o r de Pearson.
- [ ] **Aceitacao:** inserir um swipe em **Swipes e Matches** e, no dashboard, clicar em *Atualizar dados*: a pizza (3) e a linha semanal (4) contam o swipe novo.

**Tarefa 5 — procedimentos e triggers**

- [ ] **Match pelo trigger** (`tr_swipe_cria_match`): com o perfil Pedro Guitar (#2), curtir o grupo #64 em **Descobrir** (deslizar para a direita, botao ♥ ou tecla →) → tela *Deu match!*; em **Matches → Conexoes**, a linha nova com o selo *Novo* e o badge na aba Matches.
- [ ] **Conversao** (`sp_converter_match_em_participacao`): tocar nesse match → a funcao ja vem preenchida com a categoria do perfil (ex.: `Guitarra`) → **Entrar no grupo** → a etiqueta passa para *No grupo*.
- [ ] **Log** (`tr_match_log_situacao`): em **Matches → Historico** aparece "Ativo → No grupo" para esse grupo. Os chips de situacao filtram a lista na hora; o filtro por match fica na sheet do proprio match (secao *Historico*). O historico so mostra matches do perfil escolhido.
- [ ] **Erro do procedimento na tela:** abrir **Matches** em duas abas com o mesmo perfil e um match *Ativo* com grupo. Converter na primeira aba; na segunda, sem recarregar, tentar converter o mesmo match → aviso "So um match Ativo pode ser convertido…" embaixo do campo.
- [ ] **Sincronizacao** (`sp_sincronizar_situacao_grupos`): em **Perfil → Sincronizar grupos** → aviso com grupos atualizados e vagas fechadas. Tocar de novo → "0 grupos atualizados · 0 vagas fechadas".
- [ ] Rodar o reset de novo no fim, para voltar o banco ao seed.

## 6. Antes do envio

- [ ] `git status` limpo e tudo enviado para o GitHub.
- [ ] Nenhuma senha real em arquivo do repositorio: `ConnectionFactory` so le `SYNC_DB_PASSWORD` do ambiente, e `.env.example` tem apenas o valor de exemplo.
- [ ] Secao *Uso de IA* do README preenchida por todos.
- [ ] Gerar o `.zip` ou o `link.txt` com o link do repositorio e enviar no Classroom.
