-- =====================================================
-- CONSULTA 1: Músicos que não participam de nenhum grupo musical
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
-- CONSULTA 3: Grupos com mais integrantes ativos que a média
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