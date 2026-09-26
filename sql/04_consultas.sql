-- =====================================================
-- CONSULTA 1: Vagas abertas com nome e cidade do grupo
-- Conceito: INNER JOIN + filtro com WHERE
-- Pergunta que responde: "Quais vagas estão disponíveis
-- agora e de qual grupo/cidade elas são?"
-- =====================================================
SELECT
    v.id_vaga,
    v.funcao_instrumento,
    g.nome_grupo,
    g.cidade_atuacao
FROM Vaga v
JOIN Grupo_Musical g
    ON v.id_grupo_musical = g.id_usuario
WHERE v.situacao = 'Aberta'
ORDER BY g.nome_grupo;


-- =====================================================
-- CONSULTA 2: Quantidade de músicos por instrumento
-- Conceito: LEFT JOIN + GROUP BY + funções de agregação
-- Pergunta que responde: "Quais instrumentos têm mais
-- músicos e qual a experiência média de quem toca?"
-- =====================================================
SELECT
    i.nome                               AS instrumento,
    COUNT(t.id_musico)                   AS qtd_musicos,
    ROUND(AVG(t.tempo_experiencia), 1)   AS media_anos_experiencia
FROM Instrumento i
LEFT JOIN Toca t
    ON t.id_instrumento = i.id_instrumento
GROUP BY i.id_instrumento, i.nome
ORDER BY qtd_musicos DESC, i.nome;

-- =====================================================
-- CONSULTA 3: Grupos sem nenhuma vaga aberta
-- Conceito: subconsulta correlacionada com NOT EXISTS
--           + subconsulta escalar no SELECT
-- Pergunta que responde: "Quais grupos não estão
-- procurando músicos no momento?"
-- =====================================================
SELECT
    g.id_usuario        AS id_grupo,
    g.nome_grupo,
    g.cidade_atuacao,
    (SELECT COUNT(*)
       FROM Vaga v2
      WHERE v2.id_grupo_musical = g.id_usuario) AS total_vagas
FROM Grupo_Musical g
WHERE NOT EXISTS (
    SELECT 1
      FROM Vaga v
     WHERE v.id_grupo_musical = g.id_usuario
       AND v.situacao = 'Aberta'
)
ORDER BY total_vagas DESC, g.nome_grupo;


-- =====================================================
-- CONSULTA 4: Matches ativos com número de mensagens
-- Conceito: JOIN com a mesma tabela duas vezes (aliases)
--           + LEFT JOIN + GROUP BY + COUNT/MAX
-- Pergunta que responde: "Quais conexões estão ativas
-- e quanto cada uma já conversou?"
-- =====================================================
SELECT
    m.id_match,
    ua.nome_usuario                 AS usuario_a,
    ub.nome_usuario                 AS usuario_b,
    m.data_criacao,
    COUNT(msg.id_mensagem)          AS qtd_mensagens,
    MAX(msg.data_hora_envio)        AS ultima_mensagem
FROM `Match` m
JOIN Usuario ua ON ua.id_usuario = m.id_usuario_a
JOIN Usuario ub ON ub.id_usuario = m.id_usuario_b
LEFT JOIN Mensagem msg ON msg.id_match = m.id_match
WHERE m.situacao = 'Ativo'
GROUP BY m.id_match, ua.nome_usuario, ub.nome_usuario, m.data_criacao
ORDER BY qtd_mensagens DESC, m.id_match;

-- =====================================================
-- CONSULTA 5: Músicos multi-instrumentistas
-- Conceito: JOIN de 3 tabelas (N:N) + GROUP BY + HAVING
--           + GROUP_CONCAT
-- Pergunta que responde: "Quais músicos tocam mais de
-- um instrumento, e quais são eles?"
-- =====================================================
SELECT
    m.id_usuario                                        AS id_musico,
    m.nome,
    m.nivel_experiencia,
    COUNT(*)                                            AS qtd_instrumentos,
    GROUP_CONCAT(i.nome ORDER BY i.nome SEPARATOR ', ') AS instrumentos
FROM Musico m
JOIN Toca t        ON t.id_musico      = m.id_usuario
JOIN Instrumento i ON i.id_instrumento = t.id_instrumento
GROUP BY m.id_usuario, m.nome, m.nivel_experiencia
HAVING COUNT(*) > 1
ORDER BY qtd_instrumentos DESC, m.nome;

-- =====================================================
-- CONSULTA 6: Vagas abertas compatíveis com o nível
--             de um músico específico
-- Conceito: JOIN + subconsulta escalar no WHERE
--           + FIELD() para ordenar valores de texto
-- Pergunta que responde: "Para quais vagas abertas o
-- músico X já tem nível suficiente?"
-- (no Java, o 1 vira um parâmetro ? do PreparedStatement)
-- =====================================================
SELECT
    v.id_vaga,
    v.funcao_instrumento,
    COALESCE(v.nivel_minimo, '(sem minimo)') AS nivel_minimo,
    g.nome_grupo,
    g.cidade_atuacao
FROM Vaga v
JOIN Grupo_Musical g ON g.id_usuario = v.id_grupo_musical
WHERE v.situacao = 'Aberta'
  AND (
        v.nivel_minimo IS NULL
     OR FIELD(v.nivel_minimo,
              'Iniciante','Basico','Intermediario','Avancado','Profissional')
        <=
        (SELECT FIELD(m.nivel_experiencia,
                      'Iniciante','Basico','Intermediario','Avancado','Profissional')
           FROM Musico m
          WHERE m.id_usuario = 1)
      )
ORDER BY FIELD(v.nivel_minimo,
               'Iniciante','Basico','Intermediario','Avancado','Profissional') DESC,
         g.nome_grupo;

-- =====================================================
-- CONSULTA 7: Oferta x procura por gênero musical
-- Conceito: duas subconsultas escalares correlacionadas
--           sobre tabelas associativas (N:N)
-- Pergunta que responde: "Em quais gêneros há muitos
-- grupos e poucos músicos interessados (ou o contrário)?"
-- =====================================================
SELECT
    gm.nome AS genero,
    (SELECT COUNT(*)
       FROM Grupo_Genero gg
      WHERE gg.id_genero_musical = gm.id_genero_musical) AS qtd_grupos,
    (SELECT COUNT(*)
       FROM Interesse it
      WHERE it.id_genero_musical = gm.id_genero_musical) AS qtd_musicos_interessados
FROM Genero_Musical gm
ORDER BY qtd_grupos DESC, qtd_musicos_interessados DESC, gm.nome;

-- =====================================================
-- CONSULTA 8: Duplas que já tocaram juntas e quantos
--             grupos elas têm em comum
-- Conceito: AUTO-RELACIONAMENTO (Tocou_com) + mesma
--           tabela com dois aliases + subconsulta com
--           self-join em Participa
-- Pergunta que responde: "Quais músicos já tocaram
-- juntos, e essa parceria virou grupo?"
-- =====================================================
SELECT
    m1.nome AS musico_1,
    m2.nome AS musico_2,
    (SELECT COUNT(*)
       FROM Participa p1
       JOIN Participa p2
         ON p2.id_grupo_musical = p1.id_grupo_musical
      WHERE p1.id_musico = tc.id_musico_1
        AND p2.id_musico = tc.id_musico_2) AS grupos_em_comum
FROM Tocou_com tc
JOIN Musico m1 ON m1.id_usuario = tc.id_musico_1
JOIN Musico m2 ON m2.id_usuario = tc.id_musico_2
ORDER BY grupos_em_comum DESC, m1.nome, m2.nome;