USE match_musical;

-- =====================================================
-- TRIGGER (Etapa 5, pessoa 2): auditoria da situacao do `Match`
-- Evento: AFTER UPDATE ON `Match`, FOR EACH ROW.
-- Acao: quando a situacao de um match muda, grava uma linha em
-- Log_Match com o id do match, a situacao anterior, a nova, a
-- data/hora (DEFAULT da tabela) e o usuario do banco.
--
-- Justificativa semantica: audita o ciclo de vida do match
-- (Pendente, Ativo, Encerrado, Convertido). Fica no banco, e
-- nao na aplicacao, porque a mudanca pode vir de qualquer
-- caminho (interface, procedimento, UPDATE manual) e todos
-- precisam deixar rastro. Por isso o procedimento
-- sp_converter_match_em_participacao NAO grava em Log_Match:
-- o UPDATE dele em `Match` ja dispara este trigger.
--
-- Decisoes de projeto:
--  * AFTER (e nao BEFORE): so registra depois que o UPDATE
--    passou pelo CHECK de `Match`.situacao. Se a situacao nova
--    for invalida, o UPDATE falha e nenhum log e gravado.
--  * IF OLD.situacao <> NEW.situacao: atualizar outra coluna
--    sem mudar a situacao nao gera log. `Match`.situacao e
--    NOT NULL, entao o <> nao precisa tratar NULL.
--  * Um UPDATE que atinge varios matches gera 1 linha de log
--    por match cuja situacao mudou (FOR EACH ROW).
--  * Usuario: USER() devolve quem esta conectado de fato (ex.:
--    sync_app@localhost). CURRENT_USER() dentro de trigger
--    devolve o DEFINER (quem criou o trigger, ex.: root), o que
--    nao identifica quem alterou o match.
--  * O trigger nao altera `Match` (erro 1442), nao faz COMMIT
--    e nao tem regra de negocio: so registra.
--  * Depende da tabela Log_Match (etapa5_log_match_ddl.sql):
--    rodar aquele script antes deste.
--  * `Match` e palavra reservada, por isso as crases.
-- =====================================================
DROP TRIGGER IF EXISTS tr_match_log_situacao;

DELIMITER $$
CREATE TRIGGER tr_match_log_situacao
AFTER UPDATE ON `Match`
FOR EACH ROW
BEGIN
    IF OLD.situacao <> NEW.situacao THEN
        INSERT INTO Log_Match
            (id_match, situacao_anterior, situacao_nova, usuario_banco)
        VALUES
            (NEW.id_match, OLD.situacao, NEW.situacao, USER());
    END IF;
END$$
DELIMITER ;

-- Exemplo de uso (rodar numa copia do banco, nao no de entrega):
-- UPDATE `Match` SET situacao = 'Encerrado' WHERE id_match = 1;
-- SELECT * FROM Log_Match ORDER BY id_log DESC LIMIT 10;
--   resultado: 1 linha com id_match 1, 'Ativo' -> 'Encerrado'.
-- UPDATE `Match` SET situacao = 'Encerrado' WHERE id_match = 1;
--   rodar de novo: a situacao nao mudou, nenhuma linha nova.
