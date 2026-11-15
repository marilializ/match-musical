USE match_musical;

-- =====================================================
-- ETAPA 5: funcoes, procedimentos e triggers (consolidado)
-- Ordem de execucao (cada objeto depende dos anteriores):
--   1. Tabela Log_Match           (usada pelo trigger de log)
--   2. fn_taxa_retribuicao_usuario
--   3. fn_compatibilidade_vaga
--   4. sp_converter_match_em_participacao
--   5. sp_sincronizar_situacao_grupos
--   6. tr_match_log_situacao      (depende de Log_Match)
--   7. tr_swipe_cria_match
--
-- Os blocos abaixo sao copias dos scripts individuais
-- etapa5_*.sql, sem mudanca de logica; os comentarios de cada
-- responsavel foram mantidos. Se um objeto mudar, atualizem o
-- script individual e este arquivo.
--
-- Rodar no cliente mysql (usa DELIMITER, que nao vai pelo
-- JDBC), como um usuario com permissao de CREATE ROUTINE e
-- TRIGGER, e com --default-character-set=utf8mb4 (a
-- fn_compatibilidade_vaga devolve textos acentuados).
-- O sync_app precisa de GRANT EXECUTE para chamar as rotinas
-- (ver 00_reset.sql).
-- Atencao: o bloco de Log_Match faz DROP TABLE IF EXISTS e
-- apaga o log existente.
-- =====================================================


-- -----------------------------------------------------
-- Origem: sql/etapa5_log_match_ddl.sql
-- -----------------------------------------------------
-- =====================================================
-- TABELA (Etapa 5, pessoa 2): Log_Match
-- Objeto: tabela de auditoria do ciclo de vida do `Match`.
-- Cada linha registra UMA mudanca de situacao de um match
-- (Pendente, Ativo, Encerrado, Convertido): qual match, a
-- situacao anterior, a nova, quando e por qual usuario do
-- banco. Quem grava e o trigger tr_match_log_situacao;
-- ninguem insere aqui a mao e a aplicacao nao grava no log.
--
-- Justificativa semantica: o historico do match nao pode
-- sumir junto com ele. Por isso a FK usa ON DELETE SET NULL
-- (e nunca CASCADE): se o `Match` for apagado, a linha de
-- log sobrevive. A troca e que ela perde o vinculo com o id
-- do match apagado (id_match fica NULL). Por isso id_match
-- aceita NULL de proposito.
-- ON UPDATE CASCADE: se o id do match mudasse, o log
-- acompanharia.
--
-- CHECKs: situacao_nova e situacao_anterior usam o mesmo
-- dominio de `Match`.situacao. A anterior tambem e restrita
-- porque ela e sempre um valor que o `Match` ja teve, e o
-- proprio `Match` so aceita esses quatro.
--
-- Observacoes:
--  * DROP TABLE IF EXISTS permite rodar o script varias
--    vezes, mas APAGA TODO O LOG existente. Nao rodar de
--    novo no banco de entrega depois de usado.
--  * Sem esta tabela o trigger tr_match_log_situacao falha
--    ao disparar (qualquer UPDATE de situacao em `Match`
--    daria erro). Por isso este script vem ANTES do trigger.
--  * O trigger e so AFTER UPDATE: apagar um match (DELETE)
--    nao gera linha de log.
--  * `Match` e palavra reservada, por isso as crases.
--  * Nao ha indice extra: o MySQL ja cria um indice para a
--    FK id_match.
-- =====================================================
DROP TABLE IF EXISTS Log_Match;

CREATE TABLE Log_Match (
    id_log INT AUTO_INCREMENT PRIMARY KEY,
    id_match INT NULL,
    situacao_anterior VARCHAR(30) NOT NULL,
    situacao_nova VARCHAR(30) NOT NULL,
    data_hora DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    usuario_banco VARCHAR(100) NOT NULL,

    CONSTRAINT chk_log_match_situacao_anterior
        CHECK (situacao_anterior IN ('Ativo', 'Encerrado', 'Convertido', 'Pendente')),

    CONSTRAINT chk_log_match_situacao_nova
        CHECK (situacao_nova IN ('Ativo', 'Encerrado', 'Convertido', 'Pendente')),

    -- SET NULL (nao CASCADE): apagar o match nao pode apagar a auditoria.
    CONSTRAINT fk_log_match_match
        FOREIGN KEY (id_match)
        REFERENCES `Match`(id_match)
        ON DELETE SET NULL
        ON UPDATE CASCADE
);

-- Exemplo de consulta (as 10 ultimas mudancas registradas):
-- SELECT * FROM Log_Match ORDER BY id_log DESC LIMIT 10;



-- -----------------------------------------------------
-- Origem: sql/etapa5_fn_taxa_retribuicao_usuario.sql
-- -----------------------------------------------------
-- =====================================================
-- FUNCAO (Etapa 5, pessoa 1): taxa de retribuicao do usuario
-- Pergunta que responde: "Que percentual dos swipes positivos
-- deste usuario recebeu um Like ou Super Like de volta?"
--
-- Justificativa semantica: mede a reciprocidade do interesse
-- demonstrado pelo usuario, sem depender de Match (o seed
-- contem matches sem swipes). Cada swipe positivo dado conta
-- uma vez no denominador, inclusive se houver repeticoes.
-- EXISTS impede que varias respostas positivas multipliquem
-- o numerador. Swipe para si mesmo nao e retribuicao.
-- Sem swipes positivos, a taxa e 0.00.
-- =====================================================
DROP FUNCTION IF EXISTS fn_taxa_retribuicao_usuario;

DELIMITER $$
CREATE FUNCTION fn_taxa_retribuicao_usuario(p_id_usuario INT)
RETURNS DECIMAL(5,2)
READS SQL DATA
BEGIN
    DECLARE v_total BIGINT DEFAULT 0;
    DECLARE v_retribuidos BIGINT DEFAULT 0;

    SELECT COUNT(*),
           COALESCE(SUM(
               CASE WHEN s.id_usuario_encontrado <> s.id_usuario_realizou
                     AND EXISTS (
                         SELECT 1
                         FROM Swipe AS resposta
                         WHERE resposta.id_usuario_realizou = s.id_usuario_encontrado
                           AND resposta.id_usuario_encontrado = s.id_usuario_realizou
                           AND resposta.tipo_decisao IN ('Like', 'Super Like')
                     )
                    THEN 1 ELSE 0 END
           ), 0)
      INTO v_total, v_retribuidos
      FROM Swipe AS s
     WHERE s.id_usuario_realizou = p_id_usuario
       AND s.tipo_decisao IN ('Like', 'Super Like');

    RETURN COALESCE(ROUND(v_retribuidos * 100.0 / NULLIF(v_total, 0), 2), 0.00);
END$$
DELIMITER ;

-- Exemplo de uso (no seed, o usuario 1 tem 2 de 3 swipes positivos retribuidos):
-- SELECT fn_taxa_retribuicao_usuario(1) AS taxa_percentual; -- 66.67



-- -----------------------------------------------------
-- Origem: sql/etapa5_fn_compatibilidade_vaga.sql
-- -----------------------------------------------------
-- =====================================================
-- FUNCAO (Etapa 5, pessoa 3): compatibilidade do musico com a vaga
-- Conceito: funcao com estrutura condicional (IF + CASE)
-- Pergunta que responde: "Este musico tem nivel suficiente
-- para esta vaga?"
--
-- Justificativa semantica: mostra ao musico, antes de dar
-- like, se ele atende ao nivel minimo da vaga. Devolve um
-- diagnostico em texto:
--   'Nao encontrado'   musico ou vaga nao existem
--   'Vaga fechada'     a vaga nao esta mais 'Aberta'
--   'Compativel'       sem nivel minimo, ou nivel do musico
--                      igual ou acima do minimo
--   'Abaixo do nivel'  nivel do musico abaixo do minimo
-- Usa a mesma escala do FIELD() da Consulta 6 (Iniciante = 1
-- ... Profissional = 5) e a mesma definicao de vaga aberta
-- da Etapa 4 (situacao = 'Aberta'). Musico sem nivel
-- cadastrado: FIELD(NULL, ...) devolve 0, entao o resultado
-- e 'Abaixo do nivel' (comportamento seguro).
-- Compara so por nivel: nao junta funcao_instrumento com
-- Instrumento.nome (armadilha 9).
-- READS SQL DATA: a funcao so le tabelas (evita o erro 1418).
--
-- Na interface: coluna 'Compatibilidade' na lista de vagas
-- abertas, para o musico selecionado.
--
-- Exemplo de chamada:
--   SELECT fn_compatibilidade_vaga(1, 1);  -- 'Compatível'
--   SELECT fn_compatibilidade_vaga(1, 3);  -- 'Abaixo do nível'
--   SELECT fn_compatibilidade_vaga(1, 5);  -- 'Vaga fechada'
--
--   SELECT v.id_vaga, g.nome_grupo, v.nivel_minimo,
--          fn_compatibilidade_vaga(1, v.id_vaga) AS compatibilidade
--     FROM Vaga v
--     JOIN Grupo_Musical g ON g.id_usuario = v.id_grupo_musical
--    WHERE v.situacao = 'Aberta';
-- =====================================================
DROP FUNCTION IF EXISTS fn_compatibilidade_vaga;

DELIMITER $$
CREATE FUNCTION fn_compatibilidade_vaga(p_id_musico INT, p_id_vaga INT)
    RETURNS VARCHAR(20)
    READS SQL DATA
BEGIN
    DECLARE v_musico_existe INT DEFAULT 0;
    DECLARE v_vaga_existe   INT DEFAULT 0;
    DECLARE v_nivel_musico  VARCHAR(50);
    DECLARE v_nivel_minimo  VARCHAR(50);
    DECLARE v_situacao_vaga VARCHAR(50);
    DECLARE v_peso_musico   INT;
    DECLARE v_peso_vaga     INT;

SELECT COUNT(*), MAX(nivel_experiencia)
INTO v_musico_existe, v_nivel_musico
FROM Musico
WHERE id_usuario = p_id_musico;

SELECT COUNT(*), MAX(situacao), MAX(nivel_minimo)
INTO v_vaga_existe, v_situacao_vaga, v_nivel_minimo
FROM Vaga
WHERE id_vaga = p_id_vaga;

IF v_musico_existe = 0 OR v_vaga_existe = 0 THEN
        RETURN 'Não encontrado';
END IF;

    IF v_situacao_vaga <> 'Aberta' THEN
        RETURN 'Vaga fechada';
END IF;

    IF v_nivel_minimo IS NULL THEN
        RETURN 'Compatível';
END IF;

    SET v_peso_musico = FIELD(v_nivel_musico,
        'Iniciante', 'Basico', 'Intermediario', 'Avancado', 'Profissional');
    SET v_peso_vaga = FIELD(v_nivel_minimo,
        'Iniciante', 'Basico', 'Intermediario', 'Avancado', 'Profissional');

RETURN CASE
           WHEN v_peso_musico >= v_peso_vaga THEN 'Compatível'
           ELSE 'Abaixo do nível'
    END;
END $$
DELIMITER ;


-- -----------------------------------------------------
-- Origem: sql/etapa5_sp_converter_match_em_participacao.sql
-- -----------------------------------------------------
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



-- -----------------------------------------------------
-- Origem: sql/etapa5_sp_sincronizar_situacao_grupos.sql
-- -----------------------------------------------------
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



-- -----------------------------------------------------
-- Origem: sql/etapa5_tr_match_log_situacao.sql
-- -----------------------------------------------------
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



-- -----------------------------------------------------
-- Origem: sql/etapa5_tr_swipe_cria_match.sql
-- -----------------------------------------------------
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
