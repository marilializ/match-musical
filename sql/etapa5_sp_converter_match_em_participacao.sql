USE match_musical;

-- =====================================================
-- PROCEDIMENTO (Etapa 5, pessoa 4): converter match em participacao
-- Acao: transforma um match musico-grupo em uma linha de
-- Participa e marca o match como 'Convertido'.
--
-- Justificativa semantica: fecha o ciclo do app previsto no
-- minimundo. O match que termina com a entrada do musico no
-- grupo vira participacao, e o match sai de 'Ativo'.
-- Regras (cada violacao gera SIGNAL com a mensagem para a
-- interface):
--   - a funcao e obrigatoria (Participa.funcao, VARCHAR(100));
--   - o match precisa existir e estar 'Ativo';
--   - o match precisa ser entre um Musico e um Grupo_Musical,
--     em qualquer ordem (id_usuario_a, id_usuario_b). Match
--     entre dois musicos ou entre dois grupos nao vira
--     participacao;
--   - se o musico ja e integrante ativo do grupo, a conversao
--     e recusada.
-- Musico que saiu e volta ao mesmo grupo: a PK de Participa e
-- (id_musico, id_grupo_musical), entao nao cabe uma segunda
-- linha para o par. A linha 'Inativo' e reativada: situacao
-- volta a 'Ativo', data_entrada passa a ser hoje, data_saida
-- volta a NULL e a funcao e a informada.
--
-- Transacao unica: qualquer erro desfaz tudo (ROLLBACK) e o
-- RESIGNAL repassa a mensagem original para a aplicacao.
-- O log da mudanca de situacao do match e gravado pelo trigger
-- tr_match_log_situacao (pessoa 2); este procedimento nao grava
-- em Log_Match, para nao duplicar a linha.
-- Atencao: o START TRANSACTION confirma qualquer transacao que
-- estiver aberta na sessao. Por isso nao da para testar com
-- ROLLBACK: teste numa copia do banco (scripts 01 e 02).
-- =====================================================
DROP PROCEDURE IF EXISTS sp_converter_match_em_participacao;

DELIMITER $$
CREATE PROCEDURE sp_converter_match_em_participacao(
    IN p_id_match INT,
    IN p_funcao   VARCHAR(100)
)
BEGIN
    DECLARE v_usuario_a INT;
    DECLARE v_usuario_b INT;
    DECLARE v_situacao  VARCHAR(30);
    DECLARE v_id_musico INT;
    DECLARE v_id_grupo  INT;
    DECLARE v_funcao    VARCHAR(100);
    DECLARE v_msg       VARCHAR(255);

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    SET v_funcao = NULLIF(TRIM(p_funcao), '');
    IF v_funcao IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Informe a funcao do musico no grupo.';
    END IF;

    -- FOR UPDATE trava o match ate o COMMIT: duas conversoes
    -- simultaneas do mesmo match nao passam as duas.
    SELECT id_usuario_a, id_usuario_b, situacao
      INTO v_usuario_a, v_usuario_b, v_situacao
      FROM `Match`
     WHERE id_match = p_id_match
       FOR UPDATE;

    IF v_situacao IS NULL THEN
        SET v_msg = CONCAT('Match ', COALESCE(p_id_match, 'NULL'),
                           ' nao encontrado.');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    IF v_situacao <> 'Ativo' THEN
        SET v_msg = CONCAT('So um match Ativo pode ser convertido; o match ',
                           p_id_match, ' esta ', v_situacao, '.');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    -- Descobre quem e o musico e quem e o grupo, em qualquer ordem.
    IF EXISTS (SELECT 1 FROM Musico WHERE id_usuario = v_usuario_a)
       AND EXISTS (SELECT 1 FROM Grupo_Musical WHERE id_usuario = v_usuario_b) THEN
        SET v_id_musico = v_usuario_a;
        SET v_id_grupo  = v_usuario_b;
    ELSEIF EXISTS (SELECT 1 FROM Musico WHERE id_usuario = v_usuario_b)
       AND EXISTS (SELECT 1 FROM Grupo_Musical WHERE id_usuario = v_usuario_a) THEN
        SET v_id_musico = v_usuario_b;
        SET v_id_grupo  = v_usuario_a;
    ELSE
        SET v_msg = CONCAT('O match ', p_id_match,
                           ' nao e entre um musico e um grupo; nao vira participacao.');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    -- Integrante ativo segue a definicao da Etapa 4: situacao = 'Ativo'.
    IF EXISTS (SELECT 1
                 FROM Participa
                WHERE id_musico = v_id_musico
                  AND id_grupo_musical = v_id_grupo
                  AND situacao = 'Ativo') THEN
        SET v_msg = CONCAT('O musico ', v_id_musico,
                           ' ja e integrante ativo do grupo ', v_id_grupo, '.');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    IF EXISTS (SELECT 1
                 FROM Participa
                WHERE id_musico = v_id_musico
                  AND id_grupo_musical = v_id_grupo) THEN
        -- Musico voltando ao grupo: reativa a linha existente.
        UPDATE Participa
           SET situacao     = 'Ativo',
               data_entrada = CURDATE(),
               data_saida   = NULL,
               funcao       = v_funcao
         WHERE id_musico = v_id_musico
           AND id_grupo_musical = v_id_grupo;
    ELSE
        INSERT INTO Participa
            (id_musico, id_grupo_musical, data_entrada, funcao, situacao, data_saida)
        VALUES
            (v_id_musico, v_id_grupo, CURDATE(), v_funcao, 'Ativo', NULL);
    END IF;

    UPDATE `Match`
       SET situacao = 'Convertido'
     WHERE id_match = p_id_match;

    COMMIT;
END$$
DELIMITER ;

-- Exemplos de chamada (no seed; rodar numa copia do banco):
-- CALL sp_converter_match_em_participacao(2, 'Vocalista');
--   match 2 = musico 1 x grupo 31. O musico 1 saiu do grupo 31
--   em 2022 (Participa 'Inativo'): a linha e reativada.
-- CALL sp_converter_match_em_participacao(11, 'Violinista');
--   match 11 = grupo 73 x musico 22 (ordem invertida): insere
--   a participacao do musico 22 na orquestra 73.
-- CALL sp_converter_match_em_participacao(1, 'Guitarrista');
--   erro: o match 1 e entre dois musicos (1 e 2).
-- CALL sp_converter_match_em_participacao(3, 'Guitarrista');
--   erro: o match 3 esta Encerrado.
