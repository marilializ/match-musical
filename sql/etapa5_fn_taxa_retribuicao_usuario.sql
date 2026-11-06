USE match_musical;

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
