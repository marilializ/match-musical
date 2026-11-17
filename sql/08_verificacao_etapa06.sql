USE match_musical;

-- Executar apos 00_reset.sql; as contagens abaixo devem ser 66, 35, 38, 0.
SELECT (SELECT COUNT(*) FROM Swipe) AS swipes,
       (SELECT COUNT(*) FROM `Match`) AS matches,
       (SELECT COUNT(*) FROM Mensagem) AS mensagens,
       (SELECT COUNT(*) FROM Log_Match) AS linhas_log;

-- O complemento torna o interesse mutuo visivel sem depender de acao manual.
SELECT COUNT(*) AS matches_com_like_mutuo
FROM `Match` m
WHERE EXISTS (SELECT 1 FROM Swipe a
              WHERE a.id_usuario_realizou = m.id_usuario_a
                AND a.id_usuario_encontrado = m.id_usuario_b
                AND a.tipo_decisao IN ('Like', 'Super Like'))
  AND EXISTS (SELECT 1 FROM Swipe b
              WHERE b.id_usuario_realizou = m.id_usuario_b
                AND b.id_usuario_encontrado = m.id_usuario_a
                AND b.tipo_decisao IN ('Like', 'Super Like'));
-- Esperado: 15.

SELECT 'view' AS tipo, table_name AS objeto FROM information_schema.views
 WHERE table_schema = 'match_musical'
UNION ALL
SELECT routine_type, routine_name FROM information_schema.routines
 WHERE routine_schema = 'match_musical'
UNION ALL
SELECT 'trigger', trigger_name FROM information_schema.triggers
 WHERE trigger_schema = 'match_musical'
ORDER BY tipo, objeto;

SELECT DATE_FORMAT(data_hora, '%x-%v') AS semana, COUNT(*) AS swipes
FROM Swipe GROUP BY semana ORDER BY semana;
SELECT DATE_FORMAT(data_criacao, '%x-%v') AS semana, COUNT(*) AS matches
FROM `Match` GROUP BY semana ORDER BY semana;
SELECT tipo_decisao, COUNT(*) AS total FROM Swipe GROUP BY tipo_decisao;

-- Matches sem mensagem tambem entram no histograma coletivo.
SELECT COUNT(*) AS matches_sem_mensagem FROM (
    SELECT m.id_match FROM `Match` m
    LEFT JOIN Mensagem msg ON msg.id_match = m.id_match
    GROUP BY m.id_match HAVING COUNT(msg.id_mensagem) = 0
) AS sem_mensagem;
-- Esperado: 2.
