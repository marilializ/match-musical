import java.util.List;

/**
 * As 12 consultas mostradas na aba "Consultas" do dashboard:
 *  - 1 a 8: Etapa 3, com o mesmo SQL de sql/04_consultas.sql;
 *  - 9 a 12: Etapa 4, com o SQL de sql/05_consultas_avancadas.sql (anti join e
 *    as 2 subconsultas, IN e tabela derivada) e de sql/consulta_full_outer_join_swipe_match.sql
 *    (auditoria Swipe x Match, FULL OUTER JOIN emulado).
 * Cada consulta guarda um titulo, a pergunta de negocio que ela responde e
 * o SQL puro que e enviado ao banco por PreparedStatement (sem ORM).
 *
 * Parametros: nos arquivos .sql o valor aparece fixo; aqui ele vira "?".
 * Os filtros opcionais usam "(? IS NULL OR coluna = ?)": o mesmo valor
 * preenche os dois "?" e, sem valor (null), o filtro nao restringe nada.
 * O texto digitado/escolhido nunca e concatenado no SQL.
 *
 * colunaDestaque: coluna realcada na tabela de resultado (a metrica que
 * responde a pergunta). Na auditoria, a linha inteira ganha a cor do
 * diagnostico.
 */
public record ConsultaSQL(int numero, String origem, String titulo, String pergunta, String sql,
                          Parametro parametro, String colunaDestaque) {

    /** Que valor a consulta pede e quantos "?" ele preenche. */
    public enum Parametro {
        NENHUM("", 0),
        MUSICO("Músico", 1),
        CATEGORIA("Categoria principal", 2),
        INSTRUMENTO("Instrumento", 2),
        DIAGNOSTICO("Diagnóstico", 2);

        public final String rotulo;
        public final int ocorrencias;

        Parametro(String rotulo, int ocorrencias) {
            this.rotulo = rotulo;
            this.ocorrencias = ocorrencias;
        }
    }

    /** Diagnosticos possiveis da auditoria (os rotulos do CASE da consulta 12). */
    public static final java.util.List<String> DIAGNOSTICOS =
            java.util.List.of("Consistente", "Match sem like mutuo", "Like mutuo sem match");

    /** Atalho das consultas da Etapa 3: usaMusico liga o parametro da consulta 6. */
    private ConsultaSQL(int numero, String titulo, String pergunta, String sql,
                        boolean usaMusico, String colunaDestaque) {
        this(numero, "Etapa 3", titulo, pergunta, sql,
                usaMusico ? Parametro.MUSICO : Parametro.NENHUM, colunaDestaque);
    }

    /** Mantido para o codigo que ja perguntava se a consulta usa musico. */
    public boolean usaMusico() {
        return parametro == Parametro.MUSICO;
    }

    public static final List<ConsultaSQL> TODAS = List.of(
            new ConsultaSQL(1, "Vagas abertas com grupo e cidade",
                    "Quais vagas estão disponíveis agora e de qual grupo/cidade elas são?",
                    """
                    SELECT
                        v.id_vaga,
                        v.funcao_instrumento,
                        g.nome_grupo,
                        g.cidade_atuacao
                    FROM Vaga v
                    JOIN Grupo_Musical g
                        ON v.id_grupo_musical = g.id_usuario
                    WHERE v.situacao = 'Aberta'
                    ORDER BY g.nome_grupo
                    """, false, "funcao_instrumento"),

            new ConsultaSQL(2, "Músicos por instrumento",
                    "Quais instrumentos têm mais músicos e qual a experiência média de quem toca?",
                    """
                    SELECT
                        i.nome                               AS instrumento,
                        COUNT(t.id_musico)                   AS qtd_musicos,
                        ROUND(AVG(t.tempo_experiencia), 1)   AS media_anos_experiencia
                    FROM Instrumento i
                    LEFT JOIN Toca t
                        ON t.id_instrumento = i.id_instrumento
                    GROUP BY i.id_instrumento, i.nome
                    ORDER BY qtd_musicos DESC, i.nome
                    """, false, "qtd_musicos"),

            new ConsultaSQL(3, "Grupos sem vaga aberta",
                    "Quais grupos não estão procurando músicos no momento?",
                    """
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
                    ORDER BY total_vagas DESC, g.nome_grupo
                    """, false, "total_vagas"),

            new ConsultaSQL(4, "Matches ativos e mensagens",
                    "Quais conexões estão ativas e quanto cada uma já conversou?",
                    """
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
                    ORDER BY qtd_mensagens DESC, m.id_match
                    """, false, "qtd_mensagens"),

            new ConsultaSQL(5, "Músicos multi-instrumentistas",
                    "Quais músicos tocam mais de um instrumento, e quais são eles?",
                    """
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
                    ORDER BY qtd_instrumentos DESC, m.nome
                    """, false, "qtd_instrumentos"),

            new ConsultaSQL(6, "Vagas compatíveis com o nível do músico",
                    "Para quais vagas abertas o músico escolhido já tem nível suficiente?",
                    """
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
                              WHERE m.id_usuario = ?)
                          )
                    ORDER BY FIELD(v.nivel_minimo,
                                   'Iniciante','Basico','Intermediario','Avancado','Profissional') DESC,
                             g.nome_grupo, v.id_vaga
                    """, true, "nivel_minimo"),

            new ConsultaSQL(7, "Oferta × procura por gênero",
                    "Em quais gêneros há muitos grupos e poucos músicos interessados (ou o contrário)?",
                    """
                    SELECT
                        gm.nome AS genero,
                        (SELECT COUNT(*)
                           FROM Grupo_Genero gg
                          WHERE gg.id_genero_musical = gm.id_genero_musical) AS qtd_grupos,
                        (SELECT COUNT(*)
                           FROM Interesse it
                          WHERE it.id_genero_musical = gm.id_genero_musical) AS qtd_musicos_interessados
                    FROM Genero_Musical gm
                    ORDER BY qtd_grupos DESC, qtd_musicos_interessados DESC, gm.nome
                    """, false, "qtd_musicos_interessados"),

            new ConsultaSQL(8, "Duplas que já tocaram juntas",
                    "Quais músicos já tocaram juntos, e essa parceria virou grupo?",
                    """
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
                    ORDER BY grupos_em_comum DESC, m1.nome, m2.nome
                    """, false, "grupos_em_comum"),

            // ---------------------------------------------------------- Etapa 4

            new ConsultaSQL(9, "Etapa 4", "Músicos fora de qualquer grupo",
                    "Quais músicos não participam de nenhum grupo musical (anti join)?",
                    """
                    SELECT
                        m.id_usuario AS id_musico,
                        m.nome,
                        m.categoria_principal
                    FROM Musico m
                    LEFT JOIN Participa p
                        ON p.id_musico = m.id_usuario
                    WHERE p.id_musico IS NULL
                      AND (? IS NULL OR m.categoria_principal = ?)
                    ORDER BY m.id_usuario
                    """, Parametro.CATEGORIA, "nome"),

            new ConsultaSQL(10, "Etapa 4", "Músicos interessados em grupos contratando",
                    "Quais músicos deram Like em grupos que têm pelo menos uma vaga aberta"
                            + " (subconsulta com IN)?",
                    """
                    SELECT
                        m.id_usuario AS id_musico,
                        m.nome       AS nome_musico,
                        m.categoria_principal,
                        g.id_usuario AS id_grupo,
                        g.nome_grupo,
                        s.data_hora  AS data_hora_swipe
                    FROM Swipe s
                    JOIN Musico m
                        ON m.id_usuario = s.id_usuario_realizou
                    JOIN Grupo_Musical g
                        ON g.id_usuario = s.id_usuario_encontrado
                    WHERE s.tipo_decisao = 'Like'
                      AND s.id_usuario_encontrado IN (
                          SELECT v.id_grupo_musical
                            FROM Vaga v
                           WHERE v.situacao = 'Aberta'
                      )
                      AND (? IS NULL OR m.categoria_principal = ?)
                    ORDER BY g.nome_grupo, m.nome, s.data_hora
                    """, Parametro.CATEGORIA, "nome_grupo"),

            new ConsultaSQL(11, "Etapa 4", "Grupos com mais integrantes que a média",
                    "Quais grupos têm mais integrantes ativos do que a média dos grupos"
                            + " (subconsulta no FROM)?",
                    """
                    SELECT
                        g.id_usuario   AS id_grupo,
                        g.nome_grupo,
                        c.qtd_ativos
                    FROM (
                        SELECT id_grupo_musical, COUNT(*) AS qtd_ativos
                        FROM Participa
                        WHERE situacao = 'Ativo'
                        GROUP BY id_grupo_musical
                    ) AS c
                    JOIN Grupo_Musical g
                        ON g.id_usuario = c.id_grupo_musical
                    WHERE c.qtd_ativos > (
                        SELECT AVG(x.qtd_ativos)
                        FROM (
                            SELECT COUNT(*) AS qtd_ativos
                            FROM Participa
                            WHERE situacao = 'Ativo'
                            GROUP BY id_grupo_musical
                        ) AS x
                    )
                    ORDER BY c.qtd_ativos DESC, g.nome_grupo
                    """, Parametro.NENHUM, "qtd_ativos"),

            new ConsultaSQL(12, "Etapa 4", "Auditoria Swipe × Match",
                    "Quais pares têm like mútuo sem Match, e quais Matches existem sem like mútuo"
                            + " (FULL OUTER JOIN emulado)?",
                    """
                    WITH swipes_positivos AS (
                        SELECT DISTINCT
                               id_usuario_realizou   AS id_origem,
                               id_usuario_encontrado AS id_destino
                        FROM Swipe
                        WHERE tipo_decisao IN ('Like', 'Super Like')
                    ),
                    likes_mutuos AS (
                        SELECT sp1.id_origem  AS usuario_menor,
                               sp1.id_destino AS usuario_maior
                        FROM swipes_positivos sp1
                        JOIN swipes_positivos sp2
                          ON sp2.id_origem  = sp1.id_destino
                         AND sp2.id_destino = sp1.id_origem
                        WHERE sp1.id_origem < sp1.id_destino
                    ),
                    pares_match AS (
                        SELECT id_match,
                               situacao                             AS situacao_match,
                               LEAST(id_usuario_a, id_usuario_b)    AS usuario_menor,
                               GREATEST(id_usuario_a, id_usuario_b) AS usuario_maior
                        FROM `Match`
                    ),
                    auditoria AS (
                        SELECT COALESCE(l.usuario_menor, m.usuario_menor) AS usuario_menor,
                               COALESCE(l.usuario_maior, m.usuario_maior) AS usuario_maior,
                               m.id_match,
                               m.situacao_match,
                               l.usuario_menor IS NOT NULL                AS tem_like_mutuo
                        FROM likes_mutuos l
                        LEFT JOIN pares_match m
                               ON m.usuario_menor = l.usuario_menor
                              AND m.usuario_maior = l.usuario_maior

                        UNION ALL

                        SELECT COALESCE(l.usuario_menor, m.usuario_menor),
                               COALESCE(l.usuario_maior, m.usuario_maior),
                               m.id_match,
                               m.situacao_match,
                               l.usuario_menor IS NOT NULL
                        FROM likes_mutuos l
                        RIGHT JOIN pares_match m
                                ON m.usuario_menor = l.usuario_menor
                               AND m.usuario_maior = l.usuario_maior
                        WHERE l.usuario_menor IS NULL
                    ),
                    diagnosticos AS (
                        SELECT usuario_menor,
                               usuario_maior,
                               id_match,
                               situacao_match,
                               CASE
                                   WHEN id_match IS NULL     THEN 'Like mutuo sem match'
                                   WHEN tem_like_mutuo = 0   THEN 'Match sem like mutuo'
                                   ELSE                           'Consistente'
                               END AS diagnostico
                        FROM auditoria
                    )
                    SELECT usuario_menor, usuario_maior, id_match, situacao_match, diagnostico
                    FROM diagnosticos
                    WHERE (? IS NULL OR diagnostico = ?)
                    ORDER BY diagnostico, usuario_menor, usuario_maior, id_match
                    """, Parametro.DIAGNOSTICO, "diagnostico")
    );

    public static ConsultaSQL numero(int numero) {
        return TODAS.get(numero - 1);
    }

    @Override
    public String toString() {
        return numero + ". " + titulo + " (" + origem + ")";
    }
}
