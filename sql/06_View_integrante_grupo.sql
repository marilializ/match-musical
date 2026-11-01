CREATE VIEW vw_integrantes_grupo AS
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