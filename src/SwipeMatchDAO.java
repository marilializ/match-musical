import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;

/** CRUD administrativo das duas tabelas, com SQL explicito e sem ORM. */
public class SwipeMatchDAO extends BaseDAO {
    public record Swipe(int id, Timestamp dataHora, String decisao, int origem, int destino) {}
    public record Match(int id, Timestamp dataCriacao, String situacao, int usuarioA, int usuarioB) {}

    public List<Swipe> listarSwipes() throws SQLException {
        return query("SELECT id_swipe, data_hora, tipo_decisao, id_usuario_realizou, "
                + "id_usuario_encontrado FROM Swipe ORDER BY id_swipe DESC", s -> {}, r ->
                new Swipe(r.getInt(1), r.getTimestamp(2), r.getString(3), r.getInt(4), r.getInt(5)));
    }

    public List<Match> listarMatches() throws SQLException {
        return query("SELECT id_match, data_criacao, situacao, id_usuario_a, id_usuario_b "
                + "FROM `Match` ORDER BY id_match DESC", s -> {}, r ->
                new Match(r.getInt(1), r.getTimestamp(2), r.getString(3), r.getInt(4), r.getInt(5)));
    }

    public int inserirSwipe(String decisao, int origem, int destino) throws SQLException {
        validarSwipe(decisao, origem, destino);
        try (Connection c = ConnectionFactory.getConnection();
             PreparedStatement s = c.prepareStatement("INSERT INTO Swipe "
                     + "(tipo_decisao, id_usuario_realizou, id_usuario_encontrado) VALUES (?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            s.setString(1, decisao);
            s.setInt(2, origem);
            s.setInt(3, destino);
            s.executeUpdate(); // tr_swipe_cria_match decide se cria o match.
            try (ResultSet keys = s.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
            throw new SQLException("Swipe gravado sem chave retornada.");
        }
    }

    public void alterarSwipe(int id, String decisao) throws SQLException {
        if (id <= 0 || !List.of("Like", "Dislike", "Super Like").contains(decisao))
            throw new IllegalArgumentException("Selecione um swipe e uma decisão válida.");
        int n = update("UPDATE Swipe SET tipo_decisao = ? WHERE id_swipe = ?", s -> {
            s.setString(1, decisao); s.setInt(2, id);
        });
        if (n == 0) throw new IllegalArgumentException("Swipe não encontrado. Recarregue a lista.");
    }

    public void excluirSwipe(int id) throws SQLException {
        if (id <= 0) throw new IllegalArgumentException("Selecione um swipe.");
        int n = update("DELETE FROM Swipe WHERE id_swipe = ?", s -> s.setInt(1, id));
        if (n == 0) throw new IllegalArgumentException("Swipe não encontrado. Recarregue a lista.");
    }

    public int inserirMatch(int a, int b, String situacao) throws SQLException {
        validarMatch(a, b, situacao);
        if (existePar(a, b)) throw new IllegalArgumentException("Já existe um match para esse par.");
        try (Connection c = ConnectionFactory.getConnection();
             PreparedStatement s = c.prepareStatement("INSERT INTO `Match` "
                     + "(id_usuario_a, id_usuario_b, situacao) VALUES (?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            s.setInt(1, a); s.setInt(2, b); s.setString(3, situacao);
            s.executeUpdate();
            try (ResultSet keys = s.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
            throw new SQLException("Match gravado sem chave retornada.");
        }
    }

    public void alterarMatch(int id, String situacao) throws SQLException {
        if (id <= 0 || !List.of("Ativo", "Encerrado", "Convertido", "Pendente").contains(situacao))
            throw new IllegalArgumentException("Selecione um match e uma situação válida.");
        int n = update("UPDATE `Match` SET situacao = ? WHERE id_match = ?", s -> {
            s.setString(1, situacao); s.setInt(2, id);
        }); // tr_match_log_situacao registra a mudanca de situacao.
        if (n == 0) throw new IllegalArgumentException("Match não encontrado. Recarregue a lista.");
    }

    public void excluirMatch(int id) throws SQLException {
        if (id <= 0) throw new IllegalArgumentException("Selecione um match.");
        int n = update("DELETE FROM `Match` WHERE id_match = ?", s -> s.setInt(1, id));
        if (n == 0) throw new IllegalArgumentException("Match não encontrado. Recarregue a lista.");
    }

    public boolean existePar(int a, int b) throws SQLException {
        return !query("SELECT id_match FROM `Match` WHERE "
                + "(id_usuario_a = ? AND id_usuario_b = ?) OR (id_usuario_a = ? AND id_usuario_b = ?) LIMIT 1",
                s -> { s.setInt(1, a); s.setInt(2, b); s.setInt(3, b); s.setInt(4, a); },
                r -> r.getInt(1)).isEmpty();
    }

    public List<Object[]> listarLog(int idMatch) throws SQLException {
        if (idMatch <= 0) return List.of();
        return query("SELECT id_log, situacao_anterior, situacao_nova, data_hora "
                + "FROM Log_Match WHERE id_match = ? ORDER BY id_log DESC", s -> s.setInt(1, idMatch),
                r -> new Object[] {r.getInt(1), r.getString(2), r.getString(3), r.getTimestamp(4)});
    }

    private static void validarSwipe(String decisao, int origem, int destino) {
        if (!List.of("Like", "Dislike", "Super Like").contains(decisao))
            throw new IllegalArgumentException("Escolha Like, Dislike ou Super Like.");
        if (origem <= 0 || destino <= 0 || origem == destino)
            throw new IllegalArgumentException("Informe usuários diferentes e existentes.");
    }

    private static void validarMatch(int a, int b, String situacao) {
        if (a <= 0 || b <= 0 || a == b)
            throw new IllegalArgumentException("Informe usuários diferentes e existentes.");
        if (!List.of("Ativo", "Encerrado", "Convertido", "Pendente").contains(situacao))
            throw new IllegalArgumentException("Escolha uma situação válida.");
    }
}
