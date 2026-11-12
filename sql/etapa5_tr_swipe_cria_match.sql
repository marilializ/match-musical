USE match_musical;

-- =====================================================
-- TRIGGER (Etapa 5, pessoa 6): cria o Match no like mutuo
-- Conceito: trigger de regra de negocio (AFTER INSERT ON Swipe)
--
-- Justificativa semantica: o match nasce do interesse mutuo,
-- que e a regra central do app. Evita match criado a mao ou
-- esquecido, que e a lacuna que a auditoria Swipe x Match da
-- Etapa 4 detecta ('Like mutuo sem match'). Cria o Match
-- 'Ativo' quando:
--   1. o swipe novo e positivo ('Like' ou 'Super Like');
--   2. nao e um swipe do usuario nele mesmo;
--   3. o outro usuario ja tinha dado um swipe positivo de volta;
--   4. ainda nao existe nenhum Match do par.
-- Match nao tem UNIQUE nem ordem fixa: (1, 5) e (5, 1) sao o
-- mesmo par, por isso o NOT EXISTS compara com LEAST/GREATEST
-- e um like repetido nao duplica o match (armadilha 5).
-- O trigger escreve em Match, nao em Swipe (sem erro 1442).
--
-- Cuidado A: com este trigger ativo, um like mutuo novo gera o
-- Match na hora, entao o teste com ROLLBACK da auditoria da
-- Etapa 4 deixa de mostrar 'Like mutuo sem match'. Rodem esse
-- teste antes de carregar o trigger, ou num banco sem ele.
--
-- Na interface: registrar um swipe e ver o match aparecer na
-- pagina Matches.
--
-- Exemplo de teste (nao altera o banco):
--   START TRANSACTION;
--   SELECT COUNT(*) AS antes FROM `Match`;            -- 30
--   INSERT INTO Swipe (tipo_decisao, id_usuario_realizou, id_usuario_encontrado)
--   VALUES ('Like', 5, 1);  -- o usuario 1 ja tinha dado Like no 5
--   INSERT INTO Swipe (tipo_decisao, id_usuario_realizou, id_usuario_encontrado)
--   VALUES ('Like', 5, 1);  -- repetido: nao cria outro match
--   SELECT COUNT(*) AS depois FROM `Match`;           -- 31
--   ROLLBACK;
-- =====================================================
DROP TRIGGER IF EXISTS tr_swipe_cria_match;

DELIMITER $$
CREATE TRIGGER tr_swipe_cria_match
    AFTER INSERT ON Swipe
    FOR EACH ROW
BEGIN
    IF NEW.tipo_decisao IN ('Like', 'Super Like')
       AND NEW.id_usuario_realizou <> NEW.id_usuario_encontrado
       AND EXISTS (
            SELECT 1
              FROM Swipe s
             WHERE s.id_usuario_realizou   = NEW.id_usuario_encontrado
               AND s.id_usuario_encontrado = NEW.id_usuario_realizou
               AND s.tipo_decisao IN ('Like', 'Super Like')
       )
       AND NOT EXISTS (
            SELECT 1
              FROM `Match` m
             WHERE LEAST(m.id_usuario_a, m.id_usuario_b)
                   = LEAST(NEW.id_usuario_realizou, NEW.id_usuario_encontrado)
               AND GREATEST(m.id_usuario_a, m.id_usuario_b)
                   = GREATEST(NEW.id_usuario_realizou, NEW.id_usuario_encontrado)
       )
    THEN
        INSERT INTO `Match` (id_usuario_a, id_usuario_b, situacao)
        VALUES (NEW.id_usuario_encontrado, NEW.id_usuario_realizou, 'Ativo');
END IF;
END$$
DELIMITER ;