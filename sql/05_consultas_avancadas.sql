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
