CREATE DATABASE IF NOT EXISTS match_musical;
USE match_musical;
SHOW TABLES;

-- 1. LOCALIZACAO
CREATE TABLE Localizacao (
    id_localizacao INT AUTO_INCREMENT PRIMARY KEY,
    cidade VARCHAR(100) NOT NULL,
    estado VARCHAR(100) NOT NULL,
    pais VARCHAR(100) NOT NULL
);

-- 2. USUARIO
CREATE TABLE Usuario (
    id_usuario INT AUTO_INCREMENT PRIMARY KEY,
    nome_usuario VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(150) NOT NULL UNIQUE,
    senha VARCHAR(255) NOT NULL,
    id_localizacao INT,

    FOREIGN KEY (id_localizacao)
        REFERENCES Localizacao(id_localizacao)
);

-- 3. TELEFONE
CREATE TABLE Telefone (
    id_telefone INT AUTO_INCREMENT PRIMARY KEY,
    telefone VARCHAR(20) NOT NULL,
    id_usuario INT NOT NULL,

    FOREIGN KEY (id_usuario)
        REFERENCES Usuario(id_usuario)
);

-- 4. SWIPE
CREATE TABLE Swipe (
    id_swipe INT AUTO_INCREMENT PRIMARY KEY,
    data_hora DATETIME NOT NULL,
    tipo_decisao VARCHAR(20) NOT NULL,
    id_usuario_realizou INT NOT NULL,
    id_usuario_encontrado INT NOT NULL,

    FOREIGN KEY (id_usuario_realizou)
        REFERENCES Usuario(id_usuario),

    FOREIGN KEY (id_usuario_encontrado)
        REFERENCES Usuario(id_usuario)
);

-- 5. MATCH
CREATE TABLE `Match` (
    id_match INT AUTO_INCREMENT PRIMARY KEY,
    data_criacao DATETIME NOT NULL,
    situacao VARCHAR(30) NOT NULL,
    id_usuario_a INT NOT NULL,
    id_usuario_b INT NOT NULL,

    FOREIGN KEY (id_usuario_a)
        REFERENCES Usuario(id_usuario),

    FOREIGN KEY (id_usuario_b)
        REFERENCES Usuario(id_usuario)
);

-- 6. MENSAGEM
CREATE TABLE Mensagem (
    id_mensagem INT AUTO_INCREMENT PRIMARY KEY,
    conteudo TEXT NOT NULL,
    data_hora_envio DATETIME NOT NULL,
    id_match INT NOT NULL,
    id_usuario INT NOT NULL,

    FOREIGN KEY (id_match)
        REFERENCES `Match`(id_match),

    FOREIGN KEY (id_usuario)
        REFERENCES Usuario(id_usuario)
);

-- 7. MUSICO
CREATE TABLE Musico (
    id_usuario INT PRIMARY KEY,
    nome VARCHAR(150) NOT NULL,
    data_nascimento DATE,
    nome_artistico VARCHAR(150),
    nivel_experiencia VARCHAR(50),
    disponibilidade VARCHAR(100),
    biografia TEXT,
    categoria_principal VARCHAR(100),

    FOREIGN KEY (id_usuario)
        REFERENCES Usuario(id_usuario)
);

-- 8. LINKS
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
CREATE TABLE Toca (
    id_musico INT NOT NULL,
    id_instrumento INT NOT NULL,
    nivel_habilidade VARCHAR(50),
    tempo_experiencia INT,

    PRIMARY KEY (id_musico, id_instrumento),

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
CREATE TABLE Grupo_Musical (
    id_usuario INT PRIMARY KEY,
    nome_grupo VARCHAR(150) NOT NULL,
    data_criacao DATE,
    descricao TEXT,
    situacao VARCHAR(50),
    cidade_atuacao VARCHAR(100),

    FOREIGN KEY (id_usuario)
        REFERENCES Usuario(id_usuario)
);

-- 14b. GRUPO_GENERO
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
CREATE TABLE Participa (
    id_musico INT NOT NULL,
    id_grupo_musical INT NOT NULL,
    data_entrada DATE NOT NULL,
    funcao VARCHAR(100),
    situacao VARCHAR(50),
    data_saida DATE,

    PRIMARY KEY (id_musico, id_grupo_musical),

    FOREIGN KEY (id_musico)
        REFERENCES Musico(id_usuario),

    FOREIGN KEY (id_grupo_musical)
        REFERENCES Grupo_Musical(id_usuario)
);

-- 16. BANDA
CREATE TABLE Banda (
    id_grupo_musical INT PRIMARY KEY,
    descricao_formacao TEXT,

    FOREIGN KEY (id_grupo_musical)
        REFERENCES Grupo_Musical(id_usuario)
);

-- 17. ORQUESTRA
CREATE TABLE Orquestra (
    id_grupo_musical INT PRIMARY KEY,
    categoria VARCHAR(100),
    qnt_integrantes INT,

    FOREIGN KEY (id_grupo_musical)
        REFERENCES Grupo_Musical(id_usuario)
);

-- 18. CORAL
CREATE TABLE Coral (
    id_grupo_musical INT PRIMARY KEY,
    formacao_vocal VARCHAR(150),

    FOREIGN KEY (id_grupo_musical)
        REFERENCES Grupo_Musical(id_usuario)
);

-- 19. VAGA
CREATE TABLE Vaga (
    id_vaga INT AUTO_INCREMENT PRIMARY KEY,
    situacao VARCHAR(50) NOT NULL,
    descricao TEXT,
    nivel_minimo VARCHAR(50),
    funcao_instrumento VARCHAR(100),
    id_grupo_musical INT NOT NULL,

    FOREIGN KEY (id_grupo_musical)
        REFERENCES Grupo_Musical(id_usuario)
);

-- 20. REQUISITO VAGA
CREATE TABLE Requisito_Vaga (
    id_vaga INT NOT NULL,
    id_requisito INT NOT NULL,
    descricao TEXT NOT NULL,

    PRIMARY KEY (id_vaga, id_requisito),

    FOREIGN KEY (id_vaga)
        REFERENCES Vaga(id_vaga)
);
