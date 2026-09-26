-- Migracao unica da base antiga (Entrega 02) para o esquema da Entrega 03.
-- Execute somente depois de fazer backup. Preserva os dados existentes.
-- Nao execute em uma base criada por 01_criacao_tabelas.sql atual.
USE match_musical;

-- Cada genero antes salvo diretamente no grupo vira uma associacao.
CREATE TABLE Grupo_Genero (
    id_grupo_musical INT NOT NULL,
    id_genero_musical INT NOT NULL,
    PRIMARY KEY (id_grupo_musical, id_genero_musical),
    CONSTRAINT fk_grupo_genero_grupo
        FOREIGN KEY (id_grupo_musical) REFERENCES Grupo_Musical(id_usuario)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_grupo_genero_genero
        FOREIGN KEY (id_genero_musical) REFERENCES Genero_Musical(id_genero_musical)
        ON DELETE RESTRICT ON UPDATE CASCADE
);

INSERT INTO Grupo_Genero (id_grupo_musical, id_genero_musical)
SELECT id_usuario, id_genero_musical
FROM Grupo_Musical
WHERE id_genero_musical IS NOT NULL;

ALTER TABLE Grupo_Musical
    DROP FOREIGN KEY grupo_musical_ibfk_2,
    DROP COLUMN id_genero_musical,
    DROP FOREIGN KEY grupo_musical_ibfk_1,
    ADD CONSTRAINT fk_grupo_musical_usuario
        FOREIGN KEY (id_usuario) REFERENCES Usuario(id_usuario)
        ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT chk_grupo_musical_situacao
        CHECK (situacao IS NULL OR situacao IN ('Ativo', 'Inativo'));

-- A base antiga nao tinha DEFAULTs nem regras de exclusao/atualizacao.
ALTER TABLE Usuario
    DROP FOREIGN KEY usuario_ibfk_1,
    ADD CONSTRAINT fk_usuario_localizacao
        FOREIGN KEY (id_localizacao) REFERENCES Localizacao(id_localizacao)
        ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE Telefone
    DROP FOREIGN KEY telefone_ibfk_1,
    ADD CONSTRAINT fk_telefone_usuario
        FOREIGN KEY (id_usuario) REFERENCES Usuario(id_usuario)
        ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE Swipe
    MODIFY data_hora DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    DROP FOREIGN KEY swipe_ibfk_1,
    DROP FOREIGN KEY swipe_ibfk_2,
    ADD CONSTRAINT fk_swipe_usuario_realizou
        FOREIGN KEY (id_usuario_realizou) REFERENCES Usuario(id_usuario)
        ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT fk_swipe_usuario_encontrado
        FOREIGN KEY (id_usuario_encontrado) REFERENCES Usuario(id_usuario)
        ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT chk_swipe_tipo_decisao
        CHECK (tipo_decisao IN ('Like', 'Dislike', 'Super Like'));

ALTER TABLE `Match`
    MODIFY data_criacao DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    MODIFY situacao VARCHAR(30) NOT NULL DEFAULT 'Ativo',
    DROP FOREIGN KEY match_ibfk_1,
    DROP FOREIGN KEY match_ibfk_2,
    ADD CONSTRAINT fk_match_usuario_a
        FOREIGN KEY (id_usuario_a) REFERENCES Usuario(id_usuario)
        ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT fk_match_usuario_b
        FOREIGN KEY (id_usuario_b) REFERENCES Usuario(id_usuario)
        ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT chk_match_situacao
        CHECK (situacao IN ('Ativo', 'Encerrado', 'Convertido', 'Pendente'));

ALTER TABLE Mensagem
    MODIFY data_hora_envio DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    DROP FOREIGN KEY mensagem_ibfk_1,
    DROP FOREIGN KEY mensagem_ibfk_2,
    ADD CONSTRAINT fk_mensagem_match
        FOREIGN KEY (id_match) REFERENCES `Match`(id_match)
        ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT fk_mensagem_usuario
        FOREIGN KEY (id_usuario) REFERENCES Usuario(id_usuario)
        ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE Musico
    DROP FOREIGN KEY musico_ibfk_1,
    ADD CONSTRAINT fk_musico_usuario
        FOREIGN KEY (id_usuario) REFERENCES Usuario(id_usuario)
        ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT chk_musico_nivel_experiencia
        CHECK (nivel_experiencia IS NULL OR nivel_experiencia IN
            ('Iniciante', 'Basico', 'Intermediario', 'Avancado', 'Profissional'));

ALTER TABLE Links
    DROP FOREIGN KEY links_ibfk_1,
    ADD CONSTRAINT fk_links_musico
        FOREIGN KEY (id_musico) REFERENCES Musico(id_usuario)
        ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE Toca
    DROP FOREIGN KEY toca_ibfk_1,
    DROP FOREIGN KEY toca_ibfk_2,
    ADD CONSTRAINT fk_toca_musico
        FOREIGN KEY (id_musico) REFERENCES Musico(id_usuario)
        ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT fk_toca_instrumento
        FOREIGN KEY (id_instrumento) REFERENCES Instrumento(id_instrumento)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    ADD CONSTRAINT chk_toca_tempo_experiencia
        CHECK (tempo_experiencia IS NULL OR tempo_experiencia >= 0);

ALTER TABLE Interesse
    DROP FOREIGN KEY interesse_ibfk_1,
    DROP FOREIGN KEY interesse_ibfk_2,
    ADD CONSTRAINT fk_interesse_musico
        FOREIGN KEY (id_musico) REFERENCES Musico(id_usuario)
        ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT fk_interesse_genero
        FOREIGN KEY (id_genero_musical) REFERENCES Genero_Musical(id_genero_musical)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Tocou_com
    DROP FOREIGN KEY tocou_com_ibfk_1,
    DROP FOREIGN KEY tocou_com_ibfk_2,
    ADD CONSTRAINT fk_tocou_com_musico_1
        FOREIGN KEY (id_musico_1) REFERENCES Musico(id_usuario)
        ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT fk_tocou_com_musico_2
        FOREIGN KEY (id_musico_2) REFERENCES Musico(id_usuario)
        ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE Participa
    MODIFY situacao VARCHAR(50) DEFAULT 'Ativo',
    DROP FOREIGN KEY participa_ibfk_1,
    DROP FOREIGN KEY participa_ibfk_2,
    ADD CONSTRAINT fk_participa_musico
        FOREIGN KEY (id_musico) REFERENCES Musico(id_usuario)
        ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT fk_participa_grupo
        FOREIGN KEY (id_grupo_musical) REFERENCES Grupo_Musical(id_usuario)
        ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT chk_participa_situacao
        CHECK (situacao IS NULL OR situacao IN ('Ativo', 'Inativo')),
    ADD CONSTRAINT chk_participa_datas
        CHECK (data_saida IS NULL OR data_saida >= data_entrada);

ALTER TABLE Banda
    DROP FOREIGN KEY banda_ibfk_1,
    ADD CONSTRAINT fk_banda_grupo
        FOREIGN KEY (id_grupo_musical) REFERENCES Grupo_Musical(id_usuario)
        ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE Orquestra
    DROP FOREIGN KEY orquestra_ibfk_1,
    ADD CONSTRAINT fk_orquestra_grupo
        FOREIGN KEY (id_grupo_musical) REFERENCES Grupo_Musical(id_usuario)
        ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT chk_orquestra_qnt_integrantes
        CHECK (qnt_integrantes IS NULL OR qnt_integrantes > 0);

ALTER TABLE Coral
    DROP FOREIGN KEY coral_ibfk_1,
    ADD CONSTRAINT fk_coral_grupo
        FOREIGN KEY (id_grupo_musical) REFERENCES Grupo_Musical(id_usuario)
        ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE Vaga
    MODIFY situacao VARCHAR(50) NOT NULL DEFAULT 'Aberta',
    DROP FOREIGN KEY vaga_ibfk_1,
    ADD CONSTRAINT fk_vaga_grupo
        FOREIGN KEY (id_grupo_musical) REFERENCES Grupo_Musical(id_usuario)
        ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT chk_vaga_situacao
        CHECK (situacao IN ('Aberta', 'Fechada')),
    ADD CONSTRAINT chk_vaga_nivel_minimo
        CHECK (nivel_minimo IS NULL OR nivel_minimo IN
            ('Iniciante', 'Basico', 'Intermediario', 'Avancado', 'Profissional'));

ALTER TABLE Requisito_Vaga
    DROP FOREIGN KEY requisito_vaga_ibfk_1,
    ADD CONSTRAINT fk_requisito_vaga
        FOREIGN KEY (id_vaga) REFERENCES Vaga(id_vaga)
        ON DELETE CASCADE ON UPDATE CASCADE;
