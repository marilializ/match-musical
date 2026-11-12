-- =====================================================================
-- MATCH MUSICAL - ETAPA 04 - ITEM 2: SQL DAS CONSULTAS
--   Consulta 1: anti join (LEFT JOIN + IS NULL)
--   Consulta 2: full outer join (emulado: LEFT JOIN + UNION ALL + RIGHT JOIN)
--   Consulta 3: subconsulta com IN
--   Consulta 4: subconsulta no FROM (tabela derivada)
-- Requer MySQL 8+ (a consulta 2 usa CTE).
-- =====================================================================
USE match_musical;

-- =====================================================
-- CONSULTA 1 (anti join): Músicos que não participam de nenhum grupo musical
-- Conceito: ANTI JOIN (LEFT JOIN + IS NULL)
-- Pergunta que responde: "Quais músicos não participam
-- de nenhum grupo musical?"
-- =====================================================
SELECT
    m.id_usuario AS id_musico,
    m.nome
FROM Musico m
LEFT JOIN Participa p
    ON p.id_musico = m.id_usuario
WHERE p.id_musico IS NULL
ORDER BY m.id_usuario;


-- =====================================================
-- CONSULTA 2 (full outer join): Auditoria Swipe x Match
-- Conceito: FULL OUTER JOIN emulado (LEFT JOIN + UNION ALL
--           + RIGHT JOIN filtrado com IS NULL), CTE,
--           auto join em Swipe e normalizacao do par com
--           LEAST/GREATEST
-- Pergunta que responde: "Quais pares de usuarios tem
-- interesse mutuo nos swipes mas nao possuem Match, e
-- quais Matches existem sem interesse mutuo registrado?"
--
-- Decisoes de projeto (para a apresentacao):
--  * O MySQL nao tem FULL OUTER JOIN (erro 1064). A
--    emulacao usa: A LEFT JOIN B  UNION ALL  A RIGHT JOIN B
--    WHERE A.chave IS NULL. O filtro IS NULL da 2a metade
--    evita repetir os pares que existem nos dois lados.
--  * "Interesse" = tipo_decisao 'Like' ou 'Super Like'.
--    'Dislike' nao conta. (No seed atual, nenhum Super
--    Like e correspondido, entao o resultado e o mesmo.)
--  * Like mutuo = A demonstrou interesse em B E B em A.
--    Sao 2 linhas em Swipe, por isso o lado dos swipes e
--    reduzido a 1 linha por par antes do join.
--  * Match nao tem ordem fixa: (1, 2) e (2, 1) sao o
--    mesmo par. Os dois lados sao normalizados com
--    LEAST(a, b) e GREATEST(a, b).
--  * Nao ha UNIQUE em Swipe nem em Match. Swipes repetidos
--    sao absorvidos pelo DISTINCT. Matches repetidos do
--    mesmo par aparecem em linhas separadas (1 por Match).
--  * `Match` e palavra reservada, por isso as crases.
-- =====================================================
WITH swipes_positivos AS (
    -- Quem demonstrou interesse em quem (sentido unico, sem repeticao)
    SELECT DISTINCT
           id_usuario_realizou   AS id_origem,
           id_usuario_encontrado AS id_destino
    FROM Swipe
    WHERE tipo_decisao IN ('Like', 'Super Like')
),
likes_mutuos AS (
    -- Auto join: interesse de A em B e de B em A. O filtro
    -- id_origem < id_destino deixa 1 linha por par (e descarta
    -- swipe de um usuario nele mesmo).
    SELECT sp1.id_origem  AS usuario_menor,
           sp1.id_destino AS usuario_maior
    FROM swipes_positivos sp1
    JOIN swipes_positivos sp2
      ON sp2.id_origem  = sp1.id_destino
     AND sp2.id_destino = sp1.id_origem
    WHERE sp1.id_origem < sp1.id_destino
),
pares_match AS (
    -- 1 linha por Match, com o par normalizado
    SELECT id_match,
           situacao                             AS situacao_match,
           LEAST(id_usuario_a, id_usuario_b)    AS usuario_menor,
           GREATEST(id_usuario_a, id_usuario_b) AS usuario_maior
    FROM `Match`
),
auditoria AS (
    -- Metade 1: todo like mutuo, com ou sem Match
    SELECT COALESCE(l.usuario_menor, m.usuario_menor) AS usuario_menor,
           COALESCE(l.usuario_maior, m.usuario_maior) AS usuario_maior,
           m.id_match,
           m.situacao_match,
           l.usuario_menor IS NOT NULL                AS tem_like_mutuo
    FROM likes_mutuos l
    LEFT JOIN pares_match m
           ON m.usuario_menor = l.usuario_menor
          AND m.usuario_maior = l.usuario_maior

    UNION ALL

    -- Metade 2: so os Matches que NAO tem like mutuo
    SELECT COALESCE(l.usuario_menor, m.usuario_menor),
           COALESCE(l.usuario_maior, m.usuario_maior),
           m.id_match,
           m.situacao_match,
           l.usuario_menor IS NOT NULL
    FROM likes_mutuos l
    RIGHT JOIN pares_match m
            ON m.usuario_menor = l.usuario_menor
           AND m.usuario_maior = l.usuario_maior
    WHERE l.usuario_menor IS NULL
)
SELECT usuario_menor,
       usuario_maior,
       id_match,
       situacao_match,
       CASE
           WHEN id_match IS NULL     THEN 'Like mutuo sem match'
           WHEN tem_like_mutuo = 0   THEN 'Match sem like mutuo'
           ELSE                           'Consistente'
       END AS diagnostico
FROM auditoria
ORDER BY diagnostico, usuario_menor, usuario_maior, id_match;


-- =====================================================
-- CONSULTA 3 (subconsulta 1 de 2): Subconsulta com IN
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


-- =====================================================
-- CONSULTA 4 (subconsulta 2 de 2): Grupos com mais integrantes ativos que a média
-- Conceito: SUBCONSULTA NO FROM (tabela derivada)
-- Pergunta que responde: "Quais grupos têm mais
-- integrantes ativos do que a média dos grupos?"
-- =====================================================
SELECT
    g.id_usuario   AS id_grupo,
    g.nome_grupo,
    c.qtd_ativos
FROM (
    SELECT id_grupo_musical, COUNT(*) AS qtd_ativos
    FROM Participa
    WHERE situacao = 'Ativo'
    GROUP BY id_grupo_musical
) AS c
JOIN Grupo_Musical g
    ON g.id_usuario = c.id_grupo_musical
WHERE c.qtd_ativos > (
    SELECT AVG(x.qtd_ativos)
    FROM (
        SELECT COUNT(*) AS qtd_ativos
        FROM Participa
        WHERE situacao = 'Ativo'
        GROUP BY id_grupo_musical
    ) AS x
)
ORDER BY c.qtd_ativos DESC, g.nome_grupo;
