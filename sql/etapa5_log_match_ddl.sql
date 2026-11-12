USE match_musical;

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
