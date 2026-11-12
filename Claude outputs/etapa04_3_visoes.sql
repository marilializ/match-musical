-- =====================================================================
-- MATCH MUSICAL - ETAPA 04 - ITEM 3: SQL DAS VISOES
-- 2 visoes com justificativa semantica no dominio da aplicacao.
-- =====================================================================
USE match_musical;

-- =====================================================
-- VISAO 1: vw_integrantes_grupo
-- Joins: 7 (Usuario, Localizacao, Participa, Musico,
--        Banda, Orquestra, Coral)
-- Justificativa semantica: e a tela "Integrantes" do perfil
-- de um grupo musical. Quem visita o grupo ve, em uma linha
-- por integrante ATIVO, o nome (artistico, se houver), a
-- funcao e desde quando toca, junto com o tipo do grupo e a
-- cidade. O tipo vem da especializacao Banda/Orquestra/Coral
-- (LEFT JOIN + CASE) e a cidade vem da Localizacao do
-- Usuario (superentidade do grupo). Integrantes que ja
-- sairam (situacao 'Inativo') ficam de fora.
-- =====================================================
CREATE OR REPLACE VIEW vw_integrantes_grupo AS
SELECT
    g.id_usuario                          AS id_grupo,
    g.nome_grupo,
    CASE
        WHEN b.id_grupo_musical IS NOT NULL THEN 'Banda'
        WHEN o.id_grupo_musical IS NOT NULL THEN 'Orquestra'
        WHEN c.id_grupo_musical IS NOT NULL THEN 'Coral'
    END                                   AS tipo_grupo,
    l.cidade,
    l.estado,
    m.id_usuario                          AS id_musico,
    COALESCE(m.nome_artistico, m.nome)    AS musico,
    p.funcao,
    p.data_entrada
FROM Grupo_Musical g
JOIN Usuario ug        ON ug.id_usuario = g.id_usuario
LEFT JOIN Localizacao l ON l.id_localizacao = ug.id_localizacao
JOIN Participa p       ON p.id_grupo_musical = g.id_usuario
                      AND p.situacao = 'Ativo'
JOIN Musico m          ON m.id_usuario = p.id_musico
LEFT JOIN Banda b      ON b.id_grupo_musical = g.id_usuario
LEFT JOIN Orquestra o  ON o.id_grupo_musical = g.id_usuario
LEFT JOIN Coral c      ON c.id_grupo_musical = g.id_usuario;


-- =====================================================
-- VISAO 2: vw_recomendacao_vagas
-- Joins: 5 (Grupo_Musical, Grupo_Genero, Interesse, Musico,
--        Genero_Musical) + subconsulta NOT EXISTS
-- Justificativa semantica: e o feed de recomendacao de vagas
-- para o musico, o centro do "match" da aplicacao. Para cada
-- vaga ABERTA, lista os musicos que (1) se interessam por
-- pelo menos um genero do grupo, (2) tem nivel de
-- experiencia igual ou maior que o nivel minimo da vaga e
-- (3) ainda nao sao integrantes ativos daquele grupo. Mostra
-- os generos em comum e quantos sao, o que permite ordenar
-- as recomendacoes pela afinidade.
-- =====================================================
CREATE OR REPLACE VIEW vw_recomendacao_vagas AS
SELECT
    v.id_vaga,
    g.nome_grupo,
    v.funcao_instrumento,
    v.nivel_minimo,
    m.id_usuario                                        AS id_musico,
    m.nome                                              AS musico,
    m.nivel_experiencia,
    GROUP_CONCAT(DISTINCT gm.nome ORDER BY gm.nome
                 SEPARATOR ', ')                        AS generos_em_comum,
    COUNT(DISTINCT gm.id_genero_musical)                AS qtd_generos_em_comum
FROM Vaga v
JOIN Grupo_Musical g   ON g.id_usuario = v.id_grupo_musical
JOIN Grupo_Genero gg   ON gg.id_grupo_musical = g.id_usuario
JOIN Interesse i       ON i.id_genero_musical = gg.id_genero_musical
JOIN Musico m          ON m.id_usuario = i.id_musico
JOIN Genero_Musical gm ON gm.id_genero_musical = gg.id_genero_musical
WHERE v.situacao = 'Aberta'
  AND (v.nivel_minimo IS NULL
       OR FIELD(m.nivel_experiencia,
                'Iniciante','Basico','Intermediario','Avancado','Profissional')
          >= FIELD(v.nivel_minimo,
                'Iniciante','Basico','Intermediario','Avancado','Profissional'))
  AND NOT EXISTS (
        SELECT 1 FROM Participa p
         WHERE p.id_musico = m.id_usuario
           AND p.id_grupo_musical = g.id_usuario
           AND p.situacao = 'Ativo')
GROUP BY v.id_vaga, g.nome_grupo, v.funcao_instrumento, v.nivel_minimo,
         m.id_usuario, m.nome, m.nivel_experiencia;
