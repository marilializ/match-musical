USE match_musical;

-- =====================================================
-- PROCEDIMENTO COM CURSOR (Etapa 5, pessoa 5): sincronizar a
-- situacao dos grupos
-- Acao: percorre todos os grupos e deixa as vagas e a situacao
-- de cada um coerentes com os integrantes e as vagas reais.
-- Devolve quantos grupos mudaram e quantas vagas foram fechadas.
--
-- Justificativa semantica: o aviso "procurando integrantes"
-- de um grupo e ter vaga aberta (mesma leitura da consulta 3
-- do 04_consultas.sql). Esse aviso e a situacao do grupo ficam
-- desatualizados quando musicos entram ou saem. Regras:
--   1. Formacao completa, conforme o tipo do grupo:
--      - Orquestra: integrantes ativos >= Orquestra.qnt_integrantes
--        (sem quantidade esperada, nunca e considerada completa);
--      - Banda e Coral: nao ha vaga aberta.
--   2. Ao completar, as vagas abertas do grupo sao fechadas.
--   3. Situacao do grupo: 'Ativo' se tem integrante ativo ou
--      ainda procura integrantes (vaga aberta); 'Inativo' se nao
--      tem nenhum dos dois. Grupo_Musical.situacao so aceita
--      'Ativo' e 'Inativo' (chk_grupo_musical_situacao).
-- Definicoes da Etapa 4: integrante ativo e
-- Participa.situacao = 'Ativo'; vaga aberta e
-- Vaga.situacao = 'Aberta'.
--
-- Por que cursor, e nao um UPDATE unico:
--   - a regra de "formacao completa" muda conforme o tipo do
--     grupo (Orquestra compara com qnt_integrantes; Banda e
--     Coral olham as vagas);
--   - cada grupo escreve em duas tabelas, e a segunda escrita
--     depende da primeira: a situacao e decidida com as vagas
--     que sobraram depois do fechamento do mesmo grupo;
--   - sem cursor seriam dois UPDATEs em sequencia (Vaga e depois
--     Grupo_Musical), repetindo a regra do tipo nos dois. O
--     ROW_COUNT() deles conta linhas, nao grupos: o UPDATE de
--     Vaga conta vagas, e um grupo que teve vagas fechadas e
--     situacao alterada apareceria nos dois UPDATEs.
--   O cursor trata um grupo por vez, aplica a regra do tipo,
--   faz as duas escritas pela PK do grupo corrente e soma os
--   contadores devolvidos a interface.
-- Os indices da Etapa 4 (Participa e Vaga por situacao e grupo)
-- atendem as duas contagens feitas a cada grupo.
-- Transacao unica: qualquer erro desfaz a sincronizacao inteira.
-- Altera Vaga e Grupo_Musical: teste numa copia do banco.
-- =====================================================
DROP PROCEDURE IF EXISTS sp_sincronizar_situacao_grupos;

DELIMITER $$
CREATE PROCEDURE sp_sincronizar_situacao_grupos(
    OUT p_grupos_atualizados INT,
    OUT p_vagas_fechadas     INT
)
BEGIN
    -- 1) Variaveis
    DECLARE v_fim            BOOLEAN DEFAULT FALSE;
    DECLARE v_id_grupo       INT;
    DECLARE v_situacao_atual VARCHAR(50);
    DECLARE v_tipo           VARCHAR(10);
    DECLARE v_qnt_esperada   INT;
    DECLARE v_ativos         INT;
    DECLARE v_abertas        INT;
    DECLARE v_fechadas       INT;
    DECLARE v_completa       BOOLEAN;
    DECLARE v_nova_situacao  VARCHAR(50);

    -- 2) Cursor: um grupo por linha, com o tipo e a quantidade
    --    esperada (so a Orquestra tem).
    DECLARE cur_grupos CURSOR FOR
        SELECT g.id_usuario,
               g.situacao,
               CASE
                   WHEN b.id_grupo_musical IS NOT NULL THEN 'Banda'
                   WHEN o.id_grupo_musical IS NOT NULL THEN 'Orquestra'
                   WHEN c.id_grupo_musical IS NOT NULL THEN 'Coral'
               END,
               o.qnt_integrantes
          FROM Grupo_Musical g
          LEFT JOIN Banda     b ON b.id_grupo_musical = g.id_usuario
          LEFT JOIN Orquestra o ON o.id_grupo_musical = g.id_usuario
          LEFT JOIN Coral     c ON c.id_grupo_musical = g.id_usuario
         ORDER BY g.id_usuario;

    -- 3) Handlers
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_fim = TRUE;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    SET p_grupos_atualizados = 0;
    SET p_vagas_fechadas     = 0;

    START TRANSACTION;
    OPEN cur_grupos;

    percorre_grupos: LOOP
        FETCH cur_grupos
         INTO v_id_grupo, v_situacao_atual, v_tipo, v_qnt_esperada;
        IF v_fim THEN
            LEAVE percorre_grupos;
        END IF;

        SELECT COUNT(*) INTO v_ativos
          FROM Participa
         WHERE situacao = 'Ativo'
           AND id_grupo_musical = v_id_grupo;

        SELECT COUNT(*) INTO v_abertas
          FROM Vaga
         WHERE situacao = 'Aberta'
           AND id_grupo_musical = v_id_grupo;

        -- Regra 1: formacao completa conforme o tipo.
        IF v_tipo = 'Orquestra' THEN
            SET v_completa = (v_qnt_esperada IS NOT NULL
                              AND v_ativos >= v_qnt_esperada);
        ELSE
            SET v_completa = (v_abertas = 0);
        END IF;

        -- Regra 2: grupo completo nao procura mais integrantes.
        SET v_fechadas = 0;
        IF v_completa AND v_abertas > 0 THEN
            UPDATE Vaga
               SET situacao = 'Fechada'
             WHERE situacao = 'Aberta'
               AND id_grupo_musical = v_id_grupo;
            SET v_fechadas = ROW_COUNT();
            SET v_abertas  = v_abertas - v_fechadas;
            SET p_vagas_fechadas = p_vagas_fechadas + v_fechadas;
        END IF;

        -- Regra 3: situacao com as vagas que sobraram.
        SET v_nova_situacao = IF(v_ativos > 0 OR v_abertas > 0,
                                 'Ativo', 'Inativo');

        IF NOT (v_situacao_atual <=> v_nova_situacao) THEN
            UPDATE Grupo_Musical
               SET situacao = v_nova_situacao
             WHERE id_usuario = v_id_grupo;
        END IF;

        -- Conta o grupo uma vez, se mudou a situacao ou as vagas.
        IF v_fechadas > 0
           OR NOT (v_situacao_atual <=> v_nova_situacao) THEN
            SET p_grupos_atualizados = p_grupos_atualizados + 1;
        END IF;
    END LOOP;

    CLOSE cur_grupos;
    COMMIT;
END$$
DELIMITER ;

-- Exemplo de chamada (rodar numa copia do banco):
-- CALL sp_sincronizar_situacao_grupos(@grupos, @vagas);
-- SELECT @grupos AS grupos_atualizados, @vagas AS vagas_fechadas;
-- Rodar de novo logo em seguida devolve 0 e 0: o procedimento
-- so muda o que esta incoerente.
