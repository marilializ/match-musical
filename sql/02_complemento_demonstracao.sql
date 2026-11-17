USE match_musical;

-- =====================================================
-- Complemento de demonstracao da Etapa 6
-- Executar uma vez, depois de 02 e ANTES dos triggers de 06.
-- O seed original tem 40 swipes e 30 matches, mas somente dois
-- matches com like mutuo. Estes 16 swipes associam oito pares
-- existentes, sem criar matches artificiais ou mudar os ids.
-- =====================================================
INSERT INTO Swipe (data_hora, tipo_decisao, id_usuario_realizou, id_usuario_encontrado) VALUES
('2026-09-17 10:09:00', 'Like', 1, 6),
('2026-09-17 10:11:00', 'Like', 6, 1),
('2026-09-18 11:20:00', 'Like', 10, 23),
('2026-09-18 11:22:00', 'Super Like', 23, 10),
('2026-09-19 12:31:00', 'Like', 19, 40),
('2026-09-19 12:33:00', 'Like', 40, 19),
('2026-09-20 13:42:00', 'Like', 28, 57),
('2026-09-20 13:44:00', 'Like', 57, 28),
('2026-09-21 14:53:00', 'Like', 37, 74),
('2026-09-21 14:55:00', 'Like', 74, 37),
('2026-09-22 15:04:00', 'Like', 46, 91),
('2026-09-22 15:06:00', 'Like', 91, 46),
('2026-09-23 16:15:00', 'Like', 55, 108),
('2026-09-23 16:17:00', 'Like', 108, 55),
('2026-09-24 17:26:00', 'Like', 64, 5),
('2026-09-24 17:28:00', 'Like', 5, 64);

-- Cinco semanas adicionais para os graficos temporais.
INSERT INTO Swipe (data_hora, tipo_decisao, id_usuario_realizou, id_usuario_encontrado) VALUES
('2026-10-05 10:00:00', 'Like', 3, 40),
('2026-10-05 10:05:00', 'Like', 40, 3),
('2026-10-12 10:00:00', 'Like', 4, 41),
('2026-10-12 10:05:00', 'Super Like', 41, 4),
('2026-10-19 10:00:00', 'Like', 5, 42),
('2026-10-19 10:05:00', 'Like', 42, 5),
('2026-10-26 10:00:00', 'Like', 6, 43),
('2026-10-26 10:05:00', 'Like', 43, 6),
('2026-11-02 10:00:00', 'Like', 7, 44),
('2026-11-02 10:05:00', 'Like', 44, 7);

INSERT INTO `Match` (data_criacao, situacao, id_usuario_a, id_usuario_b) VALUES
('2026-10-05 10:05:00', 'Ativo', 3, 40),
('2026-10-12 10:05:00', 'Ativo', 4, 41),
('2026-10-19 10:05:00', 'Ativo', 5, 42),
('2026-10-26 10:05:00', 'Ativo', 6, 43),
('2026-11-02 10:05:00', 'Ativo', 7, 44);

INSERT INTO Mensagem (conteudo, data_hora_envio, id_match, id_usuario) VALUES
('Podemos conversar sobre repertorio?', '2026-10-05 10:10:00', 31, 3),
('Vamos marcar um ensaio.', '2026-10-12 10:10:00', 32, 4),
('Gostei da proposta.', '2026-10-19 10:10:00', 33, 5);
-- Os matches 34 e 35 ficam sem mensagem para a faixa zero do histograma.
