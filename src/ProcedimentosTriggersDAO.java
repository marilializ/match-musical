import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Etapa 6, tarefa 5: efeitos dos triggers da Etapa 5 na interface.
 * Os procedimentos sao chamados no WebMain por CallableStatement; aqui ficam
 * as leituras que mostram o que os triggers fizeram. SQL explicito, sempre
 * com "?" (sem ORM).
 */
public class ProcedimentosTriggersDAO extends BaseDAO {

    /** Uma mudanca de situacao de match gravada pelo tr_match_log_situacao. */
    public record LinhaLog(int idMatch, String situacaoAnterior, String situacaoNova, String quando) {
    }

    // =====================================================
    // CONSULTA: match do par de usuarios
    // Conceito: normalizacao do par com LEAST/GREATEST
    // Pergunta que responde: "Existe match entre estes dois
    // usuarios? Qual o id?" `Match` nao tem ordem fixa entre
    // id_usuario_a e id_usuario_b, entao (2, 64) e (64, 2) sao
    // o mesmo par (mesma regra do tr_swipe_cria_match).
    // Roda na conexao do swipe, dentro da mesma transacao, para
    // comparar antes e depois do INSERT em Swipe.
    // =====================================================
    private static final String SQL_MATCH_DO_PAR = """
            SELECT MAX(id_match) AS id_match
              FROM `Match`
             WHERE LEAST(id_usuario_a, id_usuario_b)    = LEAST(?, ?)
               AND GREATEST(id_usuario_a, id_usuario_b) = GREATEST(?, ?)
            """;

    /** @return o id do match do par, ou null se nao houver. */
    public Integer matchDoPar(Connection connection, int usuario1, int usuario2) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL_MATCH_DO_PAR)) {
            statement.setInt(1, usuario1);
            statement.setInt(2, usuario2);
            statement.setInt(3, usuario1);
            statement.setInt(4, usuario2);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                int id = result.getInt("id_match");
                return result.wasNull() ? null : id;
            }
        }
    }

    // =====================================================
    // CONSULTA: historico de mudancas de situacao dos matches
    //           de um usuario
    // Conceito: JOIN com filtros opcionais por parametro
    // Pergunta que responde: "Quais mudancas de situacao o
    // trigger registrou nas conexoes deste usuario, para um
    // match ou para uma situacao?"
    // O JOIN com `Match` restringe o log aos matches do usuario
    // (a tela e "Seus matches"). O "? IS NULL OR ..." desliga o
    // filtro quando o campo da tela vem vazio. LIMIT mantem a
    // tela leve.
    // =====================================================
    private static final String SQL_LOG_FILTRADO = """
            SELECT l.id_match,
                   l.situacao_anterior,
                   l.situacao_nova,
                   DATE_FORMAT(l.data_hora, '%d/%m/%Y %H:%i') AS quando
              FROM Log_Match l
              JOIN `Match` m ON m.id_match = l.id_match
             WHERE (m.id_usuario_a = ? OR m.id_usuario_b = ?)
               AND (? IS NULL OR l.id_match = ?)
               AND (? IS NULL OR l.situacao_nova = ?)
             ORDER BY l.data_hora DESC, l.id_log DESC
             LIMIT 50
            """;

    public List<LinhaLog> logFiltrado(int idUsuario, Integer idMatch, String situacaoNova)
            throws SQLException {
        return query(SQL_LOG_FILTRADO, statement -> {
            statement.setInt(1, idUsuario);
            statement.setInt(2, idUsuario);
            statement.setObject(3, idMatch);
            statement.setObject(4, idMatch);
            statement.setString(5, situacaoNova);
            statement.setString(6, situacaoNova);
        }, result -> new LinhaLog(
                result.getInt("id_match"),
                result.getString("situacao_anterior"),
                result.getString("situacao_nova"),
                result.getString("quando")));
    }
}
