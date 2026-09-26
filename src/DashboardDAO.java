import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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
