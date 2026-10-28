-- =====================================================
-- TESTE (nao altera o banco): mostra a categoria
-- "Like mutuo sem match" da consulta de auditoria.
--
-- O seed atual tem 0 casos dessa categoria. Este script abre
-- uma transacao, cria um like mutuo entre dois usuarios que
-- ainda nao tem swipe nem match, roda a consulta e desfaz
-- tudo com ROLLBACK.
--
-- Uso (na pasta onde esta o arquivo da consulta):
--   mysql -u sync_app -p <nome_do_banco> < teste_like_mutuo_sem_match_rollback.sql
-- Requer tabelas InnoDB (necessario para o ROLLBACK).
-- =====================================================
SELECT COUNT(*) AS swipes_antes FROM Swipe;

START TRANSACTION;

-- Escolhe o primeiro par de usuarios sem swipe e sem match entre si
SELECT u1.id_usuario, u2.id_usuario INTO @usuario_x, @usuario_y
FROM Usuario u1
JOIN Usuario u2 ON u1.id_usuario < u2.id_usuario
WHERE NOT EXISTS (SELECT 1 FROM Swipe s
                  WHERE (s.id_usuario_realizou = u1.id_usuario AND s.id_usuario_encontrado = u2.id_usuario)
                     OR (s.id_usuario_realizou = u2.id_usuario AND s.id_usuario_encontrado = u1.id_usuario))
  AND NOT EXISTS (SELECT 1 FROM `Match` m
                  WHERE LEAST(m.id_usuario_a, m.id_usuario_b)    = u1.id_usuario
                    AND GREATEST(m.id_usuario_a, m.id_usuario_b) = u2.id_usuario)
ORDER BY u1.id_usuario, u2.id_usuario
LIMIT 1;

SELECT @usuario_x AS usuario_x, @usuario_y AS usuario_y;

-- Like mutuo (um Like e um Super Like) sem nenhum Match
INSERT INTO Swipe (tipo_decisao, id_usuario_realizou, id_usuario_encontrado)
VALUES ('Like',       @usuario_x, @usuario_y),
       ('Super Like', @usuario_y, @usuario_x);

-- Resultado esperado: uma linha com diagnostico 'Like mutuo sem match'
-- para o par (usuario_x, usuario_y), alem das 30 linhas do seed.
SOURCE consulta_full_outer_join_swipe_match.sql;

ROLLBACK;

-- Confere que nada ficou gravado: deve voltar ao mesmo valor de antes
SELECT COUNT(*) AS swipes_depois FROM Swipe;
