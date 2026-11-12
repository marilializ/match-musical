-- =====================================================================
-- MATCH MUSICAL - ETAPA 04 - ITEM 1: SQL DOS INDICES
-- 2 indices compostos, usados pelas consultas e views da Etapa 04.
-- Observacao: o InnoDB ja cria indice para toda PK e FK, por isso nao
-- indexamos colunas isoladas que ja sao chave estrangeira.
-- =====================================================================
USE match_musical;

-- =====================================================
-- ÍNDICE 1: Vaga(situacao, id_grupo_musical)
-- Conceito: índice composto para filtro + junção
-- Justificativa: o filtro situacao = 'Aberta' aparece na
-- vw_recomendacao_vagas e em consultas do 04_consultas.sql,
-- e a FK cobre só id_grupo_musical.
-- EXPLAIN (vw_recomendacao_vagas, tabela Vaga):
--   antes:  type = ALL, key = NULL, lê 35 linhas
--   depois: type = ref, key = idx_vaga_situacao_grupo, lê 27
-- Cardinalidade: 27 de 35 vagas estão abertas, então o
-- ganho é pequeno com estes dados.
-- =====================================================
CREATE INDEX idx_vaga_situacao_grupo
    ON Vaga (situacao, id_grupo_musical);


-- =====================================================
-- ÍNDICE 2: Participa(situacao, id_grupo_musical)
-- Conceito: índice composto de cobertura
-- Justificativa: a consulta 3 e a vw_integrantes_grupo
-- filtram situacao = 'Ativo' e usam o grupo. Na consulta 3,
-- o índice basta para responder, sem ler a tabela.
-- EXPLAIN (consulta 3, tabela Participa):
--   antes:  type = index, key = fk_participa_grupo, lê 45
--   depois: type = ref, key = idx_participa_situacao_grupo,
--           lê 36, Extra = Using index
-- EXPLAIN (vw_integrantes_grupo, tabela Participa):
--   antes:  type = ALL, key = NULL, lê 45 linhas
--   depois: type = ref, key = idx_participa_situacao_grupo, lê 36
-- Cardinalidade: 36 de 45 participações estão ativas, então
-- o ganho é pequeno com estes dados.
-- =====================================================
CREATE INDEX idx_participa_situacao_grupo
    ON Participa (situacao, id_grupo_musical);
