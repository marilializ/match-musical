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
-- CONSULTA 2: Músicos interessados em grupos que estão
--             contratando
-- Conceito: SUBCONSULTA com IN (...)
-- Pergunta que responde: "Quais músicos deram Like em
-- grupos que estão contratando, ou seja, que têm pelo
-- menos uma vaga aberta?"
-- (no Java, a categoria principal vira um filtro opcional ?)
-- Escrita na Etapa 4 (branch etapa4-subconsulta-in, arquivo
-- subconsulta_a_in_swipe_vaga.sql) e trazida para cá na
-- Etapa 6; a coluna categoria_principal entrou para o filtro.
--
-- Decisões de projeto:
--  * A subconsulta IN lista os grupos com vaga aberta:
--    SELECT id_grupo_musical FROM Vaga WHERE situacao =
--    'Aberta'. A consulta externa só mantém os swipes cujo
--    id_usuario_encontrado está nessa lista.
--  * IN e não JOIN com Vaga: um grupo pode ter várias vagas
--    abertas (ex.: o grupo 31 tem 2). Com JOIN, o mesmo
--    swipe apareceria uma vez por vaga. O IN só pergunta
--    "o grupo está na lista?", então não duplica linhas.
--  * Considera apenas tipo_decisao = 'Like', como no
--    enunciado da tarefa. 'Super Like' ficou de fora de
--    propósito (a auditoria Swipe x Match conta os dois como
--    interesse). Para incluir, bastaria trocar o filtro por
--    tipo_decisao IN ('Like', 'Super Like').
--  * Quem fez o swipe precisa ser MÚSICO: o JOIN com Musico
--    em id_usuario_realizou descarta os swipes feitos por
--    grupos (Usuario é superentidade de Musico e Grupo).
--  * O JOIN com Grupo_Musical em id_usuario_encontrado traz
--    o nome do grupo e garante que o alvo do swipe é um grupo.
--  * Sem DISTINCT: Swipe não tem UNIQUE, então o mesmo
--    músico pode dar Like no mesmo grupo mais de uma vez;
--    cada Like é um evento com data_hora própria, e data_hora
--    faz parte da saída. Nenhum JOIN desta consulta multiplica
--    linhas: Musico e Grupo_Musical são ligados pela PK.
-- =====================================================
SELECT
    m.id_usuario AS id_musico,
    m.nome       AS nome_musico,
    m.categoria_principal,
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
-- EXPLAIN (consulta 2, subconsulta IN sobre Vaga):
--   depois: semijoin (FirstMatch) com "Covering index lookup
--           on v using idx_vaga_situacao_grupo": o índice
--           sozinho responde (situacao, id_grupo_musical),
--           e o FirstMatch para na 1ª vaga de cada grupo.
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