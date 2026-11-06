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


-- =====================================================
-- ÍNDICE 1: Vaga(situacao, id_grupo_musical)
-- Conceito: índice composto para filtro + junção
-- Justificativa: o filtro situacao = 'Aberta' aparece na
-- vw_recomendacao_vagas e em consultas do 04_consultas.sql,
-- e a FK cobre só id_grupo_musical.
-- EXPLAIN (vw_recomendacao_vagas, tabela Vaga):
--   antes:  type = ALL, key = NULL, lê 35 linhas
--   depois: type = ref, key = idx_vaga_situacao_grupo, lê 27
-- Cardinalidade: 27 de 35 vagas estão abertas, então o
-- ganho é pequeno com estes dados.
-- =====================================================
CREATE INDEX idx_vaga_situacao_grupo
    ON Vaga (situacao, id_grupo_musical);


-- =====================================================
-- ÍNDICE 2: Participa(situacao, id_grupo_musical)
-- Conceito: índice composto de cobertura
-- Justificativa: a consulta 3 e a vw_integrantes_grupo
-- filtram situacao = 'Ativo' e usam o grupo. Na consulta 3,
-- o índice basta para responder, sem ler a tabela.
-- EXPLAIN (consulta 3, tabela Participa):
--   antes:  type = index, key = fk_participa_grupo, lê 45
--   depois: type = ref, key = idx_participa_situacao_grupo,
--           lê 36, Extra = Using index
-- EXPLAIN (vw_integrantes_grupo, tabela Participa):
--   antes:  type = ALL, key = NULL, lê 45 linhas
--   depois: type = ref, key = idx_participa_situacao_grupo, lê 36
-- Cardinalidade: 36 de 45 participações estão ativas, então
-- o ganho é pequeno com estes dados.
-- =====================================================
CREATE INDEX idx_participa_situacao_grupo
    ON Participa (situacao, id_grupo_musical);