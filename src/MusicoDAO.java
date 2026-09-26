import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

public class MusicoDAO extends BaseDAO {
    private static final String SELECT_MUSICOS = """
            SELECT u.id_usuario, u.nome_usuario, u.email,
                   m.nome, m.data_nascimento, m.nome_artistico,
                   m.nivel_experiencia, m.disponibilidade, m.biografia,
                   m.categoria_principal
            FROM Usuario u
            JOIN Musico m ON m.id_usuario = u.id_usuario
            """;

    private final UsuarioDAO usuarioDAO = new UsuarioDAO();

    public long inserir(Musico musico) throws SQLException {
        long id = inTransaction(connection -> {
            long novoId = usuarioDAO.inserir(connection, musico);
            String sql = """
                    INSERT INTO Musico (id_usuario, nome, data_nascimento, nome_artistico,
                                        nivel_experiencia, disponibilidade, biografia, categoria_principal)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, novoId);
                preencherCampos(statement, musico, 2);
                statement.executeUpdate();
            }
            return novoId;
        });
        musico.setID(id);
        return id;
    }

    public boolean atualizar(Musico musico) throws SQLException {
        return inTransaction(connection -> {
            try (PreparedStatement verificar = connection.prepareStatement(
                    "SELECT id_usuario FROM Musico WHERE id_usuario = ? FOR UPDATE")) {
                verificar.setLong(1, musico.getID());
                try (ResultSet result = verificar.executeQuery()) {
                    if (!result.next()) {
                        return false;
                    }
                }
            }
            String sql = """
                    UPDATE Musico
                    SET nome = ?, data_nascimento = ?, nome_artistico = ?,
                        nivel_experiencia = ?, disponibilidade = ?, biografia = ?,
                        categoria_principal = ?
                    WHERE id_usuario = ?
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                preencherCampos(statement, musico, 1);
                statement.setLong(8, musico.getID());
                statement.executeUpdate();
            }
            usuarioDAO.atualizar(connection, musico);
            return true;
        });
    }

    public boolean excluir(long id) throws SQLException {
        String sql = """
                DELETE FROM Usuario
                WHERE id_usuario = ?
                  AND EXISTS (SELECT 1 FROM Musico WHERE id_usuario = ?)
                """;
        return update(sql, statement -> {
            statement.setLong(1, id);
            statement.setLong(2, id);
        }) == 1;
    }

    public Optional<Musico> buscarPorId(long id) throws SQLException {
        List<Musico> musicos = query(SELECT_MUSICOS + " WHERE u.id_usuario = ?",
                statement -> statement.setLong(1, id), this::mapear);
        return musicos.stream().findFirst();
    }

    public List<Musico> listar() throws SQLException {
        return query(SELECT_MUSICOS + " ORDER BY m.nome, u.id_usuario",
                statement -> { }, this::mapear);
    }

    private static void preencherCampos(PreparedStatement statement, Musico musico, int inicio)
            throws SQLException {
        statement.setString(inicio, musico.getNome());
        if (musico.getDatadeNascimento() == null) {
            statement.setNull(inicio + 1, Types.DATE);
        } else {
            statement.setDate(inicio + 1, Date.valueOf(musico.getDatadeNascimento()));
        }
        statement.setString(inicio + 2, musico.getNomeArtistico());
        statement.setString(inicio + 3, musico.getNiveldeExperiencia());
        statement.setString(inicio + 4, musico.getDisponibilidade());
        statement.setString(inicio + 5, musico.getBiografia());
        statement.setString(inicio + 6, musico.getCategoriaPrincipal());
    }

    private Musico mapear(ResultSet result) throws SQLException {
        Date nascimento = result.getDate("data_nascimento");
        return new Musico(
                result.getLong("id_usuario"), "", result.getString("email"),
                result.getString("nome_usuario"), result.getString("disponibilidade"),
                result.getString("nome"), result.getString("nome_artistico"),
                result.getString("biografia"), result.getString("categoria_principal"),
                nascimento == null ? null : nascimento.toLocalDate(),
                result.getString("nivel_experiencia"));
    }
}
