-- =====================================================
-- RESET DO BANCO DE DEMONSTRACAO (Etapa 6)
-- Recria o match_musical do zero, na ordem das etapas:
--   01  tabelas (DDL)
--   02  carga de exemplo (seed)
--   05  consultas avancadas e indices da Etapa 4
--   06_View_integrante_grupo / 07_recomendacao_vagas  views da Etapa 4
--   06_funcoes_procedimentos_triggers  objetos da Etapa 5
-- e no fim da ao sync_app a permissao de chamar as rotinas.
--
-- Por que existe: a demonstracao altera o banco (swipes novos,
-- conversao de match, sincronizacao de grupos). Rodar este
-- script antes de cada ensaio e da apresentacao volta tudo ao
-- estado do seed.
--
-- Uso (na RAIZ do repositorio, os SOURCE usam caminho relativo):
--   mysql --commands --default-character-set=utf8mb4 -u root -p -e "source sql/00_reset.sql"
--  * --commands: liga o SOURCE (alguns clientes, como o 26.7,
--    vem com comandos locais desligados).
--  * --default-character-set=utf8mb4: a fn_compatibilidade_vaga
--    devolve textos acentuados ('Compatível').
--  * -e "source ...": funciona no PowerShell, que nao aceita "<".
--  * Rodar como root (ou usuario com CREATE, CREATE VIEW,
--    CREATE ROUTINE, TRIGGER e GRANT OPTION): o sync_app nao
--    tem essas permissoes, de proposito.
--  * O usuario sync_app precisa existir antes (README, passo 1).
--    DROP DATABASE nao apaga os GRANTs ja dados a ele.
--
-- ATENCAO: APAGA o banco match_musical inteiro, inclusive o
-- Log_Match. Nunca rodar no banco de entrega depois de usado.
-- =====================================================
DROP DATABASE IF EXISTS match_musical;

SOURCE sql/01_criacao_tabelas.sql;
SOURCE sql/02_insercao_dados.sql;
SOURCE sql/05_consultas_avancadas.sql;
SOURCE sql/06_View_integrante_grupo.sql;
SOURCE sql/07_recomendacao_vagas.sql;
SOURCE sql/06_funcoes_procedimentos_triggers.sql;

-- A aplicacao chama funcoes e procedimentos pelo JDBC: sem
-- EXECUTE, a chamada falha com o erro 1370.
GRANT EXECUTE ON match_musical.* TO 'sync_app'@'localhost';

-- Conferencia: objetos criados e contagens do seed.
SELECT 'VIEW' AS tipo, table_name AS objeto
  FROM information_schema.views
 WHERE table_schema = 'match_musical'
UNION ALL
SELECT routine_type, routine_name
  FROM information_schema.routines
 WHERE routine_schema = 'match_musical'
UNION ALL
SELECT 'TRIGGER', trigger_name
  FROM information_schema.triggers
 WHERE trigger_schema = 'match_musical'
ORDER BY tipo, objeto;

SELECT (SELECT COUNT(*) FROM match_musical.Swipe)     AS swipes,
       (SELECT COUNT(*) FROM match_musical.`Match`)   AS matches,
       (SELECT COUNT(*) FROM match_musical.Log_Match) AS linhas_log;
