CREATE DATABASE IF NOT EXISTS match_musical;
USE match_musical;
SHOW TABLES;

-- 1. LOCALIZACAO
-- Tabela-suporte para o atributo COMPOSTO "localizacao" de Usuario/Grupo_Musical
-- (cidade + estado + pais), extraida para tabela propria por ser reutilizada
-- por mais de uma entidade.
CREATE TABLE Localizacao (
    id_localizacao INT AUTO_INCREMENT PRIMARY KEY,
    cidade VARCHAR(100) NOT NULL,
    estado VARCHAR(100) NOT NULL,
    pais VARCHAR(100) NOT NULL
);

-- 2. USUARIO
-- Superentidade da especializacao Musico / Grupo_Musical (ver tabelas 7 e 14):
-- cada Musico e cada Grupo_Musical E-UM Usuario, identificados pelo mesmo
-- id_usuario (especializacao total, subtipos disjuntos).
-- id_localizacao e opcional (relacao "0..1"), por isso a FK usa
-- ON DELETE SET NULL: remover uma Localizacao nao deve apagar o Usuario,
-- so desassociar a localizacao dele.
CREATE TABLE Usuario (
    id_usuario INT AUTO_INCREMENT PRIMARY KEY,
    nome_usuario VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(150) NOT NULL UNIQUE,
    senha VARCHAR(255) NOT NULL,
    id_localizacao INT,

    CONSTRAINT fk_usuario_localizacao
        FOREIGN KEY (id_localizacao)
        REFERENCES Localizacao(id_localizacao)
        ON DELETE SET NULL
        ON UPDATE CASCADE
);

-- 3. TELEFONE
-- Traducao do atributo MULTIVALORADO "telefone" de Usuario: um usuario pode
-- ter varios telefones, por isso viram linhas 1:N nesta tabela em vez de
-- uma coluna em Usuario.
CREATE TABLE Telefone (
    id_telefone INT AUTO_INCREMENT PRIMARY KEY,
    telefone VARCHAR(20) NOT NULL,
    id_usuario INT NOT NULL,

    FOREIGN KEY (id_usuario)
        REFERENCES Usuario(id_usuario)
);

-- 4. SWIPE
-- chk_swipe_tipo_decisao restringe a decisao do usuario a um dominio fechado,
-- evitando valores livres/inconsistentes vindos da aplicacao.
CREATE TABLE Swipe (
    id_swipe INT AUTO_INCREMENT PRIMARY KEY,
    data_hora DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tipo_decisao VARCHAR(20) NOT NULL,
    id_usuario_realizou INT NOT NULL,
    id_usuario_encontrado INT NOT NULL,

    CONSTRAINT chk_swipe_tipo_decisao
        CHECK (tipo_decisao IN ('Like', 'Dislike', 'Super Like')),

    FOREIGN KEY (id_usuario_realizou)
        REFERENCES Usuario(id_usuario),

    FOREIGN KEY (id_usuario_encontrado)
        REFERENCES Usuario(id_usuario)
);

-- 5. MATCH
-- Relacionamento N:N entre dois Usuarios (id_usuario_a / id_usuario_b),
-- materializado como entidade porque carrega atributos proprios
-- (data_criacao, situacao). DEFAULT 'Ativo' reflete o estado inicial de
-- todo match recem-criado; chk_match_situacao fixa o dominio de estados
-- possiveis do ciclo de vida do match.
CREATE TABLE `Match` (
    id_match INT AUTO_INCREMENT PRIMARY KEY,
    data_criacao DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    situacao VARCHAR(30) NOT NULL DEFAULT 'Ativo',
    id_usuario_a INT NOT NULL,
    id_usuario_b INT NOT NULL,

    CONSTRAINT chk_match_situacao
        CHECK (situacao IN ('Ativo', 'Encerrado', 'Convertido', 'Pendente')),

    FOREIGN KEY (id_usuario_a)
        REFERENCES Usuario(id_usuario),

    FOREIGN KEY (id_usuario_b)
        REFERENCES Usuario(id_usuario)
);

-- 6. MENSAGEM
CREATE TABLE Mensagem (
    id_mensagem INT AUTO_INCREMENT PRIMARY KEY,
    conteudo TEXT NOT NULL,
    data_hora_envio DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_match INT NOT NULL,
    id_usuario INT NOT NULL,

    FOREIGN KEY (id_match)
        REFERENCES `Match`(id_match),

    FOREIGN KEY (id_usuario)
        REFERENCES Usuario(id_usuario)
);

-- 7. MUSICO
-- Subtipo (especializacao) de Usuario: id_usuario e ao mesmo tempo PK e FK,
-- ou seja, um Musico "e-um" Usuario com atributos extras. fk_musico_usuario
-- usa ON DELETE/UPDATE CASCADE porque Musico depende existencialmente de
-- Usuario (nao faz sentido existir um Musico sem o Usuario correspondente).
-- Disjuncao com Grupo_Musical (tabela 14): nenhum id_usuario deve aparecer
-- nas duas tabelas ao mesmo tempo (garantido pela carga de dados, ja que o
-- MySQL nao tem uma constraint nativa para "XOR" entre tabelas distintas).
CREATE TABLE Musico (
    id_usuario INT PRIMARY KEY,
    nome VARCHAR(150) NOT NULL,
    data_nascimento DATE,
    nome_artistico VARCHAR(150),
    nivel_experiencia VARCHAR(50),
    disponibilidade VARCHAR(100),
    biografia TEXT,
    categoria_principal VARCHAR(100),

    CONSTRAINT chk_musico_nivel_experiencia
        CHECK (nivel_experiencia IS NULL OR nivel_experiencia IN
            ('Iniciante', 'Basico', 'Intermediario', 'Avancado', 'Profissional')),

    CONSTRAINT fk_musico_usuario
        FOREIGN KEY (id_usuario)
        REFERENCES Usuario(id_usuario)
        ON DELETE CASCADE
        ON UPDATE CASCADE
);

-- 8. LINKS
-- Traducao do atributo MULTIVALORADO "link" de Musico: um musico pode
-- divulgar varios links (portfolio, redes sociais etc.), por isso viram
-- linhas 1:N nesta tabela.
CREATE TABLE Links (
    id_link INT AUTO_INCREMENT PRIMARY KEY,
    link VARCHAR(500) NOT NULL,
    id_musico INT NOT NULL,

    FOREIGN KEY (id_musico)
        REFERENCES Musico(id_usuario)
);

-- 9. INSTRUMENTO
CREATE TABLE Instrumento (
    id_instrumento INT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(100) NOT NULL
);

-- 10. TOCA
-- Entidade associativa do relacionamento N:N entre Musico e Instrumento,
-- com atributos proprios (nivel_habilidade, tempo_experiencia). PK composta
-- pela FK das duas entidades participantes.
CREATE TABLE Toca (
    id_musico INT NOT NULL,
    id_instrumento INT NOT NULL,
    nivel_habilidade VARCHAR(50),
    tempo_experiencia INT,

    PRIMARY KEY (id_musico, id_instrumento),

    CONSTRAINT chk_toca_tempo_experiencia
        CHECK (tempo_experiencia IS NULL OR tempo_experiencia >= 0),

    FOREIGN KEY (id_musico)
        REFERENCES Musico(id_usuario),

    FOREIGN KEY (id_instrumento)
        REFERENCES Instrumento(id_instrumento)
);

-- 11. GENERO MUSICAL
CREATE TABLE Genero_Musical (
    id_genero_musical INT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(100) NOT NULL
);

-- 12. INTERESSE
-- Entidade associativa do relacionamento N:N entre Musico e Genero_Musical
-- (generos de interesse do musico). Sem atributos proprios: apenas registra
-- a associacao.
CREATE TABLE Interesse (
    id_musico INT NOT NULL,
    id_genero_musical INT NOT NULL,

    PRIMARY KEY (id_musico, id_genero_musical),

    FOREIGN KEY (id_musico)
        REFERENCES Musico(id_usuario),

    FOREIGN KEY (id_genero_musical)
        REFERENCES Genero_Musical(id_genero_musical)
);

-- 13. TOCOU COM
-- AUTO-RELACIONAMENTO N:N de Musico consigo mesmo (dois musicos que ja
-- tocaram juntos). As duas FKs referenciam a mesma tabela (Musico).
CREATE TABLE Tocou_com (
    id_musico_1 INT NOT NULL,
    id_musico_2 INT NOT NULL,

    PRIMARY KEY (id_musico_1, id_musico_2),

    FOREIGN KEY (id_musico_1)
        REFERENCES Musico(id_usuario),

    FOREIGN KEY (id_musico_2)
        REFERENCES Musico(id_usuario)
);

-- 14. GRUPO MUSICAL
-- Segundo subtipo (especializacao) de Usuario, disjunto de Musico (ver
-- tabela 7): id_usuario e PK e FK ao mesmo tempo. fk_grupo_musical_usuario
-- usa ON DELETE/UPDATE CASCADE pelo mesmo motivo de dependencia existencial.
-- Grupo_Musical e por sua vez superentidade de uma segunda especializacao:
-- todo grupo e exatamente uma Banda, Orquestra OU Coral (tabelas 16-18).
CREATE TABLE Grupo_Musical (
    id_usuario INT PRIMARY KEY,
    nome_grupo VARCHAR(150) NOT NULL,
    data_criacao DATE,
    descricao TEXT,
    situacao VARCHAR(50),
    cidade_atuacao VARCHAR(100),

    CONSTRAINT chk_grupo_musical_situacao
        CHECK (situacao IS NULL OR situacao IN ('Ativo', 'Inativo')),

    CONSTRAINT fk_grupo_musical_usuario
        FOREIGN KEY (id_usuario)
        REFERENCES Usuario(id_usuario)
        ON DELETE CASCADE
        ON UPDATE CASCADE
);

-- 14b. GRUPO_GENERO
-- Entidade associativa do relacionamento N:N entre Grupo_Musical e
-- Genero_Musical (um grupo pode atuar em mais de um genero).
CREATE TABLE Grupo_Genero (
    id_grupo_musical INT NOT NULL,
    id_genero_musical INT NOT NULL,

    PRIMARY KEY (id_grupo_musical, id_genero_musical),

    FOREIGN KEY (id_grupo_musical)
        REFERENCES Grupo_Musical(id_usuario),

    FOREIGN KEY (id_genero_musical)
        REFERENCES Genero_Musical(id_genero_musical)
);

-- 15. PARTICIPA
-- Entidade associativa do relacionamento N:N entre Musico e Grupo_Musical,
-- com atributos proprios (data_entrada, funcao, situacao, data_saida) que
-- descrevem a participacao em si, nao o musico nem o grupo isoladamente.
-- chk_participa_datas impede registrar uma saida anterior a entrada.
CREATE TABLE Participa (
    id_musico INT NOT NULL,
    id_grupo_musical INT NOT NULL,
    data_entrada DATE NOT NULL,
    funcao VARCHAR(100),
    situacao VARCHAR(50) DEFAULT 'Ativo',
    data_saida DATE,

    PRIMARY KEY (id_musico, id_grupo_musical),

    CONSTRAINT chk_participa_situacao
        CHECK (situacao IS NULL OR situacao IN ('Ativo', 'Inativo')),

    CONSTRAINT chk_participa_datas
        CHECK (data_saida IS NULL OR data_saida >= data_entrada),

    FOREIGN KEY (id_musico)
        REFERENCES Musico(id_usuario),

    FOREIGN KEY (id_grupo_musical)
        REFERENCES Grupo_Musical(id_usuario)
);

-- 16. BANDA
-- Subtipo de Grupo_Musical (segunda especializacao do modelo). Junto com
-- Orquestra e Coral, forma uma particao total e disjunta de Grupo_Musical:
-- toda linha de Grupo_Musical tem exatamente uma linha correspondente aqui,
-- em Orquestra ou em Coral. CASCADE pelo mesmo motivo de dependencia
-- existencial usado nas demais especializacoes.
CREATE TABLE Banda (
    id_grupo_musical INT PRIMARY KEY,
    descricao_formacao TEXT,

    CONSTRAINT fk_banda_grupo
        FOREIGN KEY (id_grupo_musical)
        REFERENCES Grupo_Musical(id_usuario)
        ON DELETE CASCADE
        ON UPDATE CASCADE
);

-- 17. ORQUESTRA
-- Subtipo de Grupo_Musical (ver observacao de particao na tabela Banda).
CREATE TABLE Orquestra (
    id_grupo_musical INT PRIMARY KEY,
    categoria VARCHAR(100),
    qnt_integrantes INT,

    CONSTRAINT chk_orquestra_qnt_integrantes
        CHECK (qnt_integrantes IS NULL OR qnt_integrantes > 0),

    CONSTRAINT fk_orquestra_grupo
        FOREIGN KEY (id_grupo_musical)
        REFERENCES Grupo_Musical(id_usuario)
        ON DELETE CASCADE
        ON UPDATE CASCADE
);

-- 18. CORAL
-- Subtipo de Grupo_Musical (ver observacao de particao na tabela Banda).
CREATE TABLE Coral (
    id_grupo_musical INT PRIMARY KEY,
    formacao_vocal VARCHAR(150),

    CONSTRAINT fk_coral_grupo
        FOREIGN KEY (id_grupo_musical)
        REFERENCES Grupo_Musical(id_usuario)
        ON DELETE CASCADE
        ON UPDATE CASCADE
);

-- 19. VAGA
-- DEFAULT 'Aberta' reflete o estado inicial de toda vaga recem-criada;
-- chk_vaga_situacao/chk_vaga_nivel_minimo fixam os dominios validos.
CREATE TABLE Vaga (
    id_vaga INT AUTO_INCREMENT PRIMARY KEY,
    situacao VARCHAR(50) NOT NULL DEFAULT 'Aberta',
    descricao TEXT,
    nivel_minimo VARCHAR(50),
    funcao_instrumento VARCHAR(100),
    id_grupo_musical INT NOT NULL,

    CONSTRAINT chk_vaga_situacao
        CHECK (situacao IN ('Aberta', 'Fechada')),

    CONSTRAINT chk_vaga_nivel_minimo
        CHECK (nivel_minimo IS NULL OR nivel_minimo IN
            ('Iniciante', 'Basico', 'Intermediario', 'Avancado', 'Profissional')),

    FOREIGN KEY (id_grupo_musical)
        REFERENCES Grupo_Musical(id_usuario)
);

-- 20. REQUISITO VAGA
-- ENTIDADE FRACA: id_requisito nao e um identificador global, e apenas um
-- contador local dentro de cada Vaga (existe "requisito 1 da vaga 5" e
-- "requisito 1 da vaga 8" sem conflito). Por isso a PK e composta e inclui
-- a FK da entidade forte (id_vaga) da qual Requisito_Vaga depende
-- existencialmente para ser identificada.
CREATE TABLE Requisito_Vaga (
    id_vaga INT NOT NULL,
    id_requisito INT NOT NULL,
    descricao TEXT NOT NULL,

    PRIMARY KEY (id_vaga, id_requisito),

    FOREIGN KEY (id_vaga)
        REFERENCES Vaga(id_vaga)
);
