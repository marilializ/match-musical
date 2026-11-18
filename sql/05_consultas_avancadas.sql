-- =====================================================
-- CONSULTA 1: Músicos que não participam de nenhum grupo musical
-- Conceito: ANTI JOIN (LEFT JOIN + IS NULL)
-- Pergunta que responde: "Quais músicos não participam
-- de nenhum grupo musical?"
-- (no Java, a categoria principal vira um filtro opcional ?)
-- =====================================================
SELECT
    m.id_usuario AS id_musico,
    m.nome,
    m.categoria_principal
FROM Musico m
LEFT JOIN Participa p
    ON p.id_musico = m.id_usuario
WHERE p.id_musico IS NULL
ORDER BY m.id_usuario;


-- =====================================================
-- CONSULTA 2: Músicos com experiência acima da média
--             no próprio instrumento
-- Conceito: SUBCONSULTA CORRELACIONADA no WHERE (a média é
--           recalculada para o instrumento de cada linha de
--           Toca) + subconsulta escalar correlacionada no SELECT
-- Pergunta que responde: "Quem tem mais anos de experiência
-- em um instrumento do que a média de quem toca esse mesmo
-- instrumento?"
-- (no Java, o instrumento vira um filtro opcional ?)
-- Escrita na Etapa 6 para completar as 4 consultas da Etapa 4
-- (anti join, full outer join e 2 subconsultas): o script
-- tinha só as consultas 1 e 3.
-- =====================================================
SELECT
    m.id_usuario        AS id_musico,
    m.nome,
    i.nome              AS instrumento,
    t.tempo_experiencia AS anos_experiencia,
    (SELECT ROUND(AVG(t2.tempo_experiencia), 1)
       FROM Toca t2
      WHERE t2.id_instrumento = t.id_instrumento) AS media_do_instrumento
FROM Toca t
JOIN Musico m
    ON m.id_usuario = t.id_musico
JOIN Instrumento i
    ON i.id_instrumento = t.id_instrumento
WHERE t.tempo_experiencia > (
    SELECT AVG(t3.tempo_experiencia)
    FROM Toca t3
    WHERE t3.id_instrumento = t.id_instrumento
)
ORDER BY i.nome, anos_experiencia DESC, m.nome;


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