CREATE VIEW vw_recomendacao_vagas AS
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