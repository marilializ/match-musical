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
     * Executa qualquer uma das 8 consultas e devolve colunas + linhas.
     * Usa ResultSetMetaData para descobrir as colunas, assim a mesma tabela
     * da tela serve para todas as consultas.
     *
     * @param idMusico usado so pela consulta 6 (preenche o "?")
     */
    public Resultado executar(ConsultaSQL consulta, Long idMusico) throws SQLException {
        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(consulta.sql())) {
            if (consulta.usaMusico()) {
                if (idMusico == null) {
                    throw new SQLException("Escolha um musico para a consulta " + consulta.numero() + ".");
                }
                statement.setLong(1, idMusico);
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
