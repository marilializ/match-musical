-- =====================================================
-- CONSULTA (Etapa 4, item 2): Subconsulta A
-- Conceito: SUBCONSULTA com IN (...)
-- Pergunta que responde: "Quais musicos demonstraram
-- interesse (Like) em grupos que estao contratando, ou
-- seja, que tem pelo menos uma vaga aberta?"
--
-- Decisoes de projeto (para a apresentacao):
--  * A subconsulta IN lista os grupos com vaga aberta:
--    SELECT id_grupo_musical FROM Vaga WHERE situacao =
--    'Aberta'. A consulta externa so mantem os swipes cujo
--    id_usuario_encontrado esta nessa lista.
--  * IN e nao JOIN com Vaga: um grupo pode ter varias vagas
--    abertas (ex.: o grupo 31 tem 2). Com JOIN, o mesmo
--    swipe apareceria uma vez por vaga. O IN so pergunta
--    "o grupo esta na lista?", entao nao duplica linhas.
--  * Considera apenas tipo_decisao = 'Like', como no
--    enunciado da tarefa. 'Super Like' ficou de fora de
--    proposito (a consulta de auditoria Swipe x Match conta
--    os dois como interesse; confirmar com o grupo se aqui
--    tambem deve contar). Para incluir, bastaria trocar o
--    filtro por tipo_decisao IN ('Like', 'Super Like').
--  * Quem fez o swipe precisa ser MUSICO: o JOIN com Musico
--    em id_usuario_realizou descarta os swipes feitos por
--    grupos (Usuario e superentidade de Musico e Grupo).
--  * O JOIN com Grupo_Musical em id_usuario_encontrado traz
--    o nome do grupo e, de quebra, garante que o alvo do
--    swipe e um grupo.
--  * Sem DISTINCT: Swipe nao tem UNIQUE, entao o mesmo
--    musico pode dar Like no mesmo grupo mais de uma vez,
--    mas cada Like e um evento com data_hora propria, e
--    data_hora faz parte da saida. DISTINCT nao eliminaria
--    nada (as linhas ja seriam diferentes) e esconderia o
--    historico. Nenhum JOIN desta consulta multiplica
--    linhas: Musico e Grupo_Musical sao ligados pela PK.
-- =====================================================
SELECT
    m.id_usuario AS id_musico,
    m.nome       AS nome_musico,
    g.id_usuario AS id_grupo,
    g.nome_grupo,
    s.data_hora  AS data_hora_swipe
FROM Swipe s
JOIN Musico m
    ON m.id_usuario = s.id_usuario_realizou
JOIN Grupo_Musical g
    ON g.id_usuario = s.id_usuario_encontrado
WHERE s.tipo_decisao = 'Like'
  AND s.id_usuario_encontrado IN (
      SELECT v.id_grupo_musical
        FROM Vaga v
       WHERE v.situacao = 'Aberta'
  )
ORDER BY g.nome_grupo, m.nome, s.data_hora;
