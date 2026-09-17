INSERT INTO match_musical.Localizacao (cidade, estado, pais)
VALUES
('Recife', 'Pernambuco', 'Brasil'),
('Olinda', 'Pernambuco', 'Brasil'),
('Jaboatao dos Guararapes', 'Pernambuco', 'Brasil');

INSERT INTO match_musical.Usuario
(nome_usuario, email, senha, id_localizacao)
VALUES
('ana_music', 'ana@email.com', 'senha_teste_01', 1),
('pedro_guitar', 'pedro@email.com', 'senha_teste_02', 2),
('lucas_bass', 'lucas@email.com', 'senha_teste_03', 1),
('marina_voice', 'marina@email.com', 'senha_teste_04', 3),
('banda_horizonte', 'horizonte@email.com', 'senha_teste_05', 1),
('orquestra_recife', 'orquestra@email.com', 'senha_teste_06', 1),
('coral_vozes', 'coral@email.com', 'senha_teste_07', 2);

INSERT INTO match_musical.Telefone
(telefone, id_usuario)
VALUES
('81999990001', 1),
('81999990002', 2),
('81999990003', 3),
('81999990004', 4),
('81988880005', 5),
('81988880006', 6),
('81988880007', 7);

INSERT INTO match_musical.Musico
(id_usuario, nome, data_nascimento, nome_artistico,
nivel_experiencia, disponibilidade, biografia, categoria_principal)
VALUES
(1, 'Ana Silva', '2002-05-15', 'Ana S',
'Intermediario', 'Noites e finais de semana',
'Cantora interessada em formar uma banda.', 'Cantora'),

(2, 'Pedro Santos', '2000-08-20', 'Pedro Guitar',
'Avancado', 'Finais de semana',
'Guitarrista de rock com experiencia em shows.', 'Guitarrista'),

(3, 'Lucas Oliveira', '2001-03-10', 'Lucas Bass',
'Intermediario', 'Noites',
'Baixista interessado em novos projetos.', 'Baixista'),

(4, 'Marina Costa', '1999-11-25', 'Marina Voice',
'Avancado', 'Manhas e tardes',
'Cantora com experiencia em corais.', 'Cantora');

INSERT INTO match_musical.Links
(link, id_musico)
VALUES
('https://example.com/ana', 1),
('https://example.com/pedro', 2),
('https://example.com/lucas', 3),
('https://example.com/marina', 4);

INSERT INTO match_musical.Instrumento
(nome)
VALUES
('Violao'),
('Guitarra'),
('Baixo'),
('Piano'),
('Voz');

INSERT INTO match_musical.Toca
(id_musico, id_instrumento, nivel_habilidade, tempo_experiencia)
VALUES
(1, 5, 'Intermediario', 4),
(1, 1, 'Basico', 2),
(2, 2, 'Avancado', 8),
(3, 3, 'Intermediario', 5),
(4, 5, 'Avancado', 10),
(4, 4, 'Intermediario', 4);

INSERT INTO match_musical.Genero_Musical
(nome)
VALUES
('Rock'),
('Pop'),
('MPB'),
('Musica Classica'),
('Gospel');

INSERT INTO match_musical.Interesse
(id_musico, id_genero_musical)
VALUES
(1, 1),
(1, 2),
(2, 1),
(3, 1),
(3, 3),
(4, 4),
(4, 5);

INSERT INTO match_musical.Tocou_com
(id_musico_1, id_musico_2)
VALUES
(1, 2),
(2, 3),
(1, 3);

INSERT INTO match_musical.Grupo_Musical
(id_usuario, nome_grupo, data_criacao,
descricao, situacao, cidade_atuacao, id_genero_musical)
VALUES
(5, 'Banda Horizonte', '2023-01-15',
'Banda de rock alternativo procurando integrantes.',
'Ativo', 'Recife', 1),

(6, 'Orquestra Recife', '2020-06-10',
'Orquestra dedicada a musica classica.',
'Ativo', 'Recife', 4),

(7, 'Coral Vozes', '2022-03-20',
'Coral com repertorio de musica gospel.',
'Ativo', 'Olinda', 5);

INSERT INTO match_musical.Participa
(id_musico, id_grupo_musical, data_entrada,
funcao, situacao, data_saida)
VALUES
(2, 5, '2023-02-01', 'Guitarrista', 'Ativo', NULL),
(3, 5, '2023-03-01', 'Baixista', 'Ativo', NULL),
(4, 7, '2022-04-01', 'Vocalista', 'Ativo', NULL);

INSERT INTO match_musical.Banda
(id_grupo_musical, descricao_formacao)
VALUES
(5, 'Banda de rock com guitarra e baixo, buscando vocalista e baterista.');

INSERT INTO match_musical.Orquestra
(id_grupo_musical, categoria, qnt_integrantes)
VALUES
(6, 'Sinfonica', 25);

INSERT INTO match_musical.Coral
(id_grupo_musical, formacao_vocal)
VALUES
(7, 'Soprano, contralto, tenor e baixo');

INSERT INTO match_musical.Vaga
(situacao, descricao, nivel_minimo,
funcao_instrumento, id_grupo_musical)
VALUES
('Aberta', 'Procuramos vocalista para banda de rock.',
'Intermediario', 'Vocalista', 5),

('Aberta', 'Procuramos baterista para shows.',
'Intermediario', 'Baterista', 5),

('Aberta', 'Procuramos violinista para orquestra.',
'Avancado', 'Violinista', 6),

('Aberta', 'Procuramos novos cantores para o coral.',
'Basico', 'Vocalista', 7);

INSERT INTO match_musical.Requisito_Vaga
(id_vaga, id_requisito, descricao)
VALUES
(1, 1, 'Ter experiencia com rock.'),
(1, 2, 'Disponibilidade para ensaios aos sabados.'),
(2, 1, 'Possuir bateria ou acesso ao instrumento.'),
(3, 1, 'Saber ler partituras.'),
(4, 1, 'Disponibilidade para ensaios semanais.');

INSERT INTO match_musical.Swipe
(data_hora, tipo_decisao, id_usuario_realizou, id_usuario_encontrado)
VALUES
('2026-09-15 10:00:00', 'Like', 1, 2),
('2026-09-15 10:05:00', 'Like', 2, 1),
('2026-09-15 11:00:00', 'Like', 1, 5),
('2026-09-15 11:10:00', 'Like', 5, 1),
('2026-09-15 12:00:00', 'Dislike', 3, 7);

INSERT INTO match_musical.`Match`
(data_criacao, situacao, id_usuario_a, id_usuario_b)
VALUES
('2026-09-15 10:05:00', 'Ativo', 1, 2),
('2026-09-15 11:10:00', 'Ativo', 1, 5);

INSERT INTO match_musical.Mensagem
(conteudo, data_hora_envio, id_match, id_usuario)
VALUES
('Oi Pedro! Vi que voce toca guitarra.',
'2026-09-15 10:10:00', 1, 1),

('Oi Ana! Estou procurando uma cantora para um projeto.',
'2026-09-15 10:12:00', 1, 2),

('Ola! Gostaria de participar da Banda Horizonte.',
'2026-09-15 11:15:00', 2, 1),

('Oi Ana! Estamos procurando uma vocalista.',
'2026-09-15 11:20:00', 2, 5);

