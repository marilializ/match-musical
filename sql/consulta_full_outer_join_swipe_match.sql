-- =====================================================
-- CONSULTA (Etapa 4, item 5): Auditoria Swipe x Match
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
