import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;

/**
 * Acesso ao banco para o dashboard. Tudo e SQL explicito enviado por
 * PreparedStatement, sem ORM.
 */
public class DashboardDAO extends BaseDAO {

    /** Resultado generico de uma consulta: nomes das colunas + linhas. */
    public record Resultado(List<String> colunas, List<Object[]> linhas) {
        /** Posicao de uma coluna pelo nome (o apelido do AS). */
        public int indice(String coluna) {
            int posicao = colunas.indexOf(coluna);
            if (posicao < 0) {
                throw new IllegalArgumentException("Coluna nao encontrada: " + coluna);
            }
            return posicao;
        }
    }

    /** Numeros gerais mostrados no topo do dashboard. */
    public record Indicadores(int musicos, int grupos, int vagasAbertas, int matchesAtivos) {
    }

    private static final String SQL_INDICADORES = """
            SELECT
                (SELECT COUNT(*) FROM Musico)                              AS musicos,
                (SELECT COUNT(*) FROM Grupo_Musical)                       AS grupos,
                (SELECT COUNT(*) FROM Vaga    WHERE situacao = 'Aberta')   AS vagas_abertas,
                (SELECT COUNT(*) FROM `Match` WHERE situacao = 'Ativo')    AS matches_ativos
            """;

    public Indicadores indicadores() throws SQLException {
        List<Indicadores> resultado = query(SQL_INDICADORES, statement -> { },
                result -> new Indicadores(
                        result.getInt("musicos"),
                        result.getInt("grupos"),
                        result.getInt("vagas_abertas"),
                        result.getInt("matches_ativos")));
        return resultado.get(0);
    }

    /** Grafico 1: frequencia por nivel, filtrada pela categoria principal. */
    public Resultado niveisPorCategoria(String categoria) throws SQLException {
        return resultado("SELECT COALESCE(nivel_experiencia, 'Não informado') AS nivel, "
                + "COUNT(*) AS qtd FROM Musico WHERE (? IS NULL OR categoria_principal = ?) "
                + "GROUP BY nivel_experiencia ORDER BY FIELD(nivel_experiencia, "
                + "'Iniciante','Basico','Intermediario','Avancado','Profissional')",
                s -> { s.setString(1, categoria); s.setString(2, categoria); });
    }

    public List<String> categorias() throws SQLException {
        return query("SELECT DISTINCT categoria_principal FROM Musico "
                + "WHERE categoria_principal IS NOT NULL ORDER BY categoria_principal", s -> {},
                r -> r.getString(1));
    }

    public List<String> habilidades() throws SQLException {
        return query("SELECT DISTINCT nivel_habilidade FROM Toca "
                + "WHERE nivel_habilidade IS NOT NULL ORDER BY nivel_habilidade", s -> {},
                r -> r.getString(1));
    }

    /** Grafico 2: Consulta 2 da Etapa 3, acrescida do filtro de habilidade. */
    public Resultado instrumentosPorHabilidade(String habilidade) throws SQLException {
        return resultado("SELECT i.nome AS instrumento, COUNT(t.id_musico) AS qtd_musicos, "
                + "ROUND(AVG(t.tempo_experiencia), 1) AS media_anos_experiencia "
                + "FROM Instrumento i LEFT JOIN Toca t ON t.id_instrumento = i.id_instrumento "
                + "AND (? IS NULL OR t.nivel_habilidade = ?) "
                + "GROUP BY i.id_instrumento, i.nome ORDER BY qtd_musicos DESC, i.nome",
                s -> { s.setString(1, habilidade); s.setString(2, habilidade); });
    }

    /** Grafico 3: periodo fechado em datas civis; limite superior exclusivo. */
    public Resultado decisoesPorPeriodo(LocalDate inicio, LocalDate fim) throws SQLException {
        return resultado("SELECT tipo_decisao AS decisao, COUNT(*) AS qtd FROM Swipe "
                + "WHERE (? IS NULL OR data_hora >= ?) AND (? IS NULL OR data_hora < ?) "
                + "GROUP BY tipo_decisao ORDER BY FIELD(tipo_decisao, 'Like','Dislike','Super Like')",
                s -> {
                    Date inicioSql = inicio == null ? null : Date.valueOf(inicio);
                    Date fimExclusivo = fim == null ? null : Date.valueOf(fim.plusDays(1));
                    s.setDate(1, inicioSql); s.setDate(2, inicioSql);
                    s.setDate(3, fimExclusivo); s.setDate(4, fimExclusivo);
                });
    }

    // ------------------------------------------------------------------
    // Etapa 6 (Pessoa 6): seis indicadores, graficos 4 a 6 e seus filtros.
    // Periodo: datas civis, inicio inclusivo e fim inclusivo (o SQL usa o
    // dia seguinte como limite exclusivo). null = sem limite.
    // ------------------------------------------------------------------

    /** Os seis cartoes do topo (+ taxa media de retribuicao, opcional). */
    public record Painel(int musicos, int grupos, int vagasAbertas, int matchesNoPeriodo,
                         int matchesAtivos, int matchesConvertidos, Double mediaMensagensPorMatch,
                         Double taxaMediaRetribuicao) {
        /** % de matches do periodo que viraram participacao (Convertido). */
        public Double percentualConvertidos() {
            return matchesNoPeriodo == 0 ? null : 100.0 * matchesConvertidos / matchesNoPeriodo;
        }
    }

    /** Filtro de periodo sobre uma coluna de data (4 "?": inicio, inicio, fim, fim). */
    private static String periodo(String coluna) {
        return "(? IS NULL OR " + coluna + " >= ?) AND (? IS NULL OR " + coluna + " < ?)";
    }

    /** Preenche "repeticoes" filtros de periodo seguidos, a partir da posicao "desde". */
    private static int ligarPeriodo(PreparedStatement statement, int desde, int repeticoes,
                                    LocalDate inicio, LocalDate fim) throws SQLException {
        Date inicioSql = inicio == null ? null : Date.valueOf(inicio);
        Date fimExclusivo = fim == null ? null : Date.valueOf(fim.plusDays(1));
        int posicao = desde;
        for (int i = 0; i < repeticoes; i++) {
            statement.setDate(posicao++, inicioSql); statement.setDate(posicao++, inicioSql);
            statement.setDate(posicao++, fimExclusivo); statement.setDate(posicao++, fimExclusivo);
        }
        return posicao;
    }

    private static final String SQL_PAINEL = """
            SELECT
                (SELECT COUNT(*) FROM Musico)                            AS musicos,
                (SELECT COUNT(*) FROM Grupo_Musical)                     AS grupos,
                (SELECT COUNT(*) FROM Vaga WHERE situacao = 'Aberta')    AS vagas_abertas,
                (SELECT COUNT(*) FROM `Match` m WHERE %1$s)              AS matches_periodo,
                (SELECT COUNT(*) FROM `Match` m
                  WHERE m.situacao = 'Ativo' AND %1$s)                   AS matches_ativos,
                (SELECT COUNT(*) FROM `Match` m
                  WHERE m.situacao = 'Convertido' AND %1$s)              AS matches_convertidos,
                (SELECT ROUND(AVG(por_match.qtd), 2)
                   FROM (SELECT COUNT(me.id_mensagem) AS qtd
                           FROM `Match` m
                           LEFT JOIN Mensagem me ON me.id_match = m.id_match
                          WHERE %1$s
                          GROUP BY m.id_match) AS por_match)            AS media_mensagens
            """.formatted(periodo("m.data_criacao"));

    /**
     * Taxa media de retribuicao (fn_taxa_retribuicao_usuario, Etapa 5) entre os
     * usuarios que deram pelo menos um like. Opcional: sem a funcao no banco
     * (erro 1305) ou sem permissao (1370), o cartao mostra "-".
     */
    private static final String SQL_TAXA_MEDIA = """
            SELECT ROUND(AVG(fn_taxa_retribuicao_usuario(s.id_usuario_realizou)), 1) AS taxa_media
            FROM (SELECT DISTINCT id_usuario_realizou
                    FROM Swipe
                   WHERE tipo_decisao IN ('Like', 'Super Like')) AS s
            """;

    public Painel painel(LocalDate inicio, LocalDate fim) throws SQLException {
        try (Connection connection = ConnectionFactory.getConnection()) {
            int musicos, grupos, vagas, noPeriodo, ativos, convertidos;
            Double mediaMensagens;
            try (PreparedStatement statement = connection.prepareStatement(SQL_PAINEL)) {
                ligarPeriodo(statement, 1, 4, inicio, fim);
                try (ResultSet r = statement.executeQuery()) {
                    r.next();
                    musicos = r.getInt("musicos"); grupos = r.getInt("grupos");
                    vagas = r.getInt("vagas_abertas"); noPeriodo = r.getInt("matches_periodo");
                    ativos = r.getInt("matches_ativos"); convertidos = r.getInt("matches_convertidos");
                    mediaMensagens = (Double) numeroOuNulo(r.getObject("media_mensagens"));
                }
            }
            Double taxa = null;
            try (PreparedStatement statement = connection.prepareStatement(SQL_TAXA_MEDIA);
                 ResultSet r = statement.executeQuery()) {
                if (r.next()) taxa = (Double) numeroOuNulo(r.getObject("taxa_media"));
            } catch (SQLException error) {
                if (error.getErrorCode() != 1305 && error.getErrorCode() != 1370) throw error;
            }
            return new Painel(musicos, grupos, vagas, noPeriodo, ativos, convertidos, mediaMensagens, taxa);
        }
    }

    private static Object numeroOuNulo(Object valor) {
        return valor == null ? null : ((Number) valor).doubleValue();
    }

    /** Grafico 4: uma semana (segunda-feira de inicio) com swipes e matches criados. */
    public record Semana(LocalDate inicio, int swipes, int matches) {
    }

    /**
     * Grafico 4: tendencia temporal. Agrupa Swipe.data_hora e Match.data_criacao
     * pela segunda-feira da semana; as semanas sem nenhum evento entram com zero
     * (preenchidas aqui, para a linha nao "pular" semanas).
     */
    public List<Semana> atividadeSemanal(LocalDate inicio, LocalDate fim) throws SQLException {
        String sql = """
                SELECT inicio_semana, SUM(swipe) AS swipes, SUM(novo_match) AS matches
                FROM (
                    SELECT DATE_SUB(DATE(data_hora), INTERVAL WEEKDAY(data_hora) DAY) AS inicio_semana,
                           1 AS swipe, 0 AS novo_match
                    FROM Swipe
                    WHERE %s
                    UNION ALL
                    SELECT DATE_SUB(DATE(data_criacao), INTERVAL WEEKDAY(data_criacao) DAY),
                           0, 1
                    FROM `Match`
                    WHERE %s
                ) AS eventos
                GROUP BY inicio_semana
                ORDER BY inicio_semana
                """.formatted(periodo("data_hora"), periodo("data_criacao"));
        List<Semana> semanas = new ArrayList<>();
        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            ligarPeriodo(statement, 1, 2, inicio, fim);
            try (ResultSet r = statement.executeQuery()) {
                while (r.next()) {
                    semanas.add(new Semana(r.getDate("inicio_semana").toLocalDate(),
                            r.getInt("swipes"), r.getInt("matches")));
                }
            }
        }
        List<Semana> continuas = new ArrayList<>();
        for (int i = 0; i < semanas.size(); i++) {
            Semana atual = semanas.get(i);
            if (!continuas.isEmpty()) {
                LocalDate esperada = continuas.get(continuas.size() - 1).inicio().plusWeeks(1);
                while (esperada.isBefore(atual.inicio())) {
                    continuas.add(new Semana(esperada, 0, 0));
                    esperada = esperada.plusWeeks(1);
                }
            }
            continuas.add(atual);
        }
        return continuas;
    }

    /**
     * Grafico 5: mensagens por match. frequencias = {qtd_mensagens, qtd_matches},
     * ordenadas; media, variancia e desvio padrao AMOSTRAIS (n - 1) vem do SQL.
     * Mediana e moda saem das frequencias (o MySQL nao tem MEDIAN nem MODE).
     */
    public record Distribuicao(List<int[]> frequencias, int n, Double media,
                               Double variancia, Double desvioPadrao) {
        public Double mediana() { return Estatistica.mediana(frequencias, n); }
        public List<Integer> modas() { return Estatistica.modas(frequencias); }
    }

    private static final String SQL_MENSAGENS_POR_MATCH = """
            SELECT m.id_match, COUNT(me.id_mensagem) AS qtd_mensagens
            FROM `Match` m
            LEFT JOIN Mensagem me ON me.id_match = m.id_match
            WHERE (? IS NULL OR m.situacao = ?) AND %s
            GROUP BY m.id_match
            """.formatted(periodo("m.data_criacao"));

    public Distribuicao mensagensPorMatch(String situacao, LocalDate inicio, LocalDate fim)
            throws SQLException {
        String sqlFrequencias = "SELECT qtd_mensagens, COUNT(*) AS qtd_matches FROM ("
                + SQL_MENSAGENS_POR_MATCH + ") AS por_match GROUP BY qtd_mensagens ORDER BY qtd_mensagens";
        String sqlResumo = "SELECT COUNT(*) AS n, AVG(qtd_mensagens) AS media, "
                + "VAR_SAMP(qtd_mensagens) AS variancia, STDDEV_SAMP(qtd_mensagens) AS desvio FROM ("
                + SQL_MENSAGENS_POR_MATCH + ") AS por_match";
        List<int[]> frequencias = new ArrayList<>();
        try (Connection connection = ConnectionFactory.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(sqlFrequencias)) {
                statement.setString(1, situacao); statement.setString(2, situacao);
                ligarPeriodo(statement, 3, 1, inicio, fim);
                try (ResultSet r = statement.executeQuery()) {
                    while (r.next()) frequencias.add(new int[] {r.getInt(1), r.getInt(2)});
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(sqlResumo)) {
                statement.setString(1, situacao); statement.setString(2, situacao);
                ligarPeriodo(statement, 3, 1, inicio, fim);
                try (ResultSet r = statement.executeQuery()) {
                    r.next();
                    return new Distribuicao(frequencias, r.getInt("n"),
                            (Double) numeroOuNulo(r.getObject("media")),
                            (Double) numeroOuNulo(r.getObject("variancia")),
                            (Double) numeroOuNulo(r.getObject("desvio")));
                }
            }
        }
    }

    /** Grafico 6: um musico = um ponto (x = instrumentos que toca, y = media de anos). */
    public record PontoMusico(long id, String nome, int instrumentos, double mediaAnos) {
    }

    private static final String SQL_PONTOS = """
            SELECT t.id_musico, m.nome,
                   COUNT(*)                 AS qtd_instrumentos,
                   AVG(t.tempo_experiencia) AS media_anos
            FROM Toca t
            JOIN Musico m ON m.id_usuario = t.id_musico
            WHERE (? IS NULL OR EXISTS (SELECT 1 FROM Toca f
                                         WHERE f.id_musico = t.id_musico
                                           AND f.id_instrumento = ?))
            GROUP BY t.id_musico, m.nome
            HAVING AVG(t.tempo_experiencia) IS NOT NULL
            """;

    public List<PontoMusico> instrumentosVersusExperiencia(Integer idInstrumento) throws SQLException {
        return query(SQL_PONTOS + " ORDER BY qtd_instrumentos, media_anos",
                s -> { ligarInteiro(s, 1, idInstrumento); ligarInteiro(s, 2, idInstrumento); },
                r -> new PontoMusico(r.getLong("id_musico"), r.getString("nome"),
                        r.getInt("qtd_instrumentos"), r.getDouble("media_anos")));
    }

    /**
     * Coeficiente de Pearson pela formula com AVG e desvio padrao (o MySQL nao
     * tem CORR): r = (media(xy) - media(x)*media(y)) / (dp(x) * dp(y)),
     * com desvios POPULACIONAIS, coerentes com a covariancia populacional
     * do numerador. null quando x ou y nao variam.
     */
    public Double pearsonInstrumentosExperiencia(Integer idInstrumento) throws SQLException {
        String sql = "SELECT (AVG(x * y) - AVG(x) * AVG(y)) / NULLIF(STDDEV_POP(x) * STDDEV_POP(y), 0) AS r "
                + "FROM (SELECT qtd_instrumentos AS x, media_anos AS y FROM (" + SQL_PONTOS + ") AS p) AS d";
        List<Double> r = query(sql,
                s -> { ligarInteiro(s, 1, idInstrumento); ligarInteiro(s, 2, idInstrumento); },
                result -> (Double) numeroOuNulo(result.getObject("r")));
        return r.isEmpty() ? null : r.get(0);
    }

    private static void ligarInteiro(PreparedStatement statement, int posicao, Integer valor) throws SQLException {
        if (valor == null) statement.setNull(posicao, java.sql.Types.INTEGER);
        else statement.setInt(posicao, valor);
    }

    /** Item de filtro com id e nome (instrumento). */
    public record Opcao(int id, String nome) {
        @Override public String toString() { return nome; }
    }

    public List<Opcao> instrumentos() throws SQLException {
        return query("SELECT id_instrumento, nome FROM Instrumento ORDER BY nome", s -> { },
                r -> new Opcao(r.getInt(1), r.getString(2)));
    }

    public List<String> situacoesMatch() throws SQLException {
        return query("SELECT DISTINCT situacao FROM `Match` ORDER BY situacao", s -> { },
                r -> r.getString(1));
    }

    private Resultado resultado(String sql, StatementBinder binder) throws SQLException {
        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet result = statement.executeQuery()) {
                ResultSetMetaData meta = result.getMetaData();
                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) columns.add(meta.getColumnLabel(i));
                List<Object[]> rows = new ArrayList<>();
                while (result.next()) {
                    Object[] row = new Object[columns.size()];
                    for (int i = 0; i < row.length; i++) row[i] = result.getObject(i + 1);
                    rows.add(row);
                }
                return new Resultado(columns, rows);
            }
        }
    }

    /**
     * Executa qualquer uma das 12 consultas e devolve colunas + linhas.
     * Usa ResultSetMetaData para descobrir as colunas, assim a mesma tabela
     * da tela serve para todas as consultas.
     *
     * @param valor o valor do parametro da consulta (ver ConsultaSQL.Parametro):
     *              id do musico (consulta 6, obrigatorio), categoria, id do
     *              instrumento ou diagnostico (opcionais: null = sem filtro).
     *              Preenche todos os "?" da consulta, sempre por PreparedStatement.
     */
    public Resultado executar(ConsultaSQL consulta, Object valor) throws SQLException {
        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(consulta.sql())) {
            if (consulta.usaMusico() && valor == null) {
                throw new SQLException("Escolha um musico para a consulta " + consulta.numero() + ".");
            }
            for (int posicao = 1; posicao <= consulta.parametro().ocorrencias; posicao++) {
                if (valor instanceof Number numero) statement.setLong(posicao, numero.longValue());
                else statement.setString(posicao, (String) valor); // null vira NULL no SQL
            }
            try (ResultSet result = statement.executeQuery()) {
                ResultSetMetaData meta = result.getMetaData();
                int total = meta.getColumnCount();
                List<String> colunas = new ArrayList<>();
                for (int coluna = 1; coluna <= total; coluna++) {
                    // getColumnLabel devolve o apelido do AS (ex.: qtd_musicos)
                    colunas.add(meta.getColumnLabel(coluna));
                }
                List<Object[]> linhas = new ArrayList<>();
                while (result.next()) {
                    Object[] linha = new Object[total];
                    for (int coluna = 1; coluna <= total; coluna++) {
                        linha[coluna - 1] = result.getObject(coluna);
                    }
                    linhas.add(linha);
                }
                return new Resultado(colunas, linhas);
            }
        }
    }
}
