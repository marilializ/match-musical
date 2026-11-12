USE match_musical;

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