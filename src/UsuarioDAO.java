import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class UsuarioDAO extends BaseDAO {
    long inserir(Connection connection, Usuario usuario) throws SQLException {
        String sql = "INSERT INTO Usuario (nome_usuario, email, senha) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, usuario.getNomedeUsuario());
            statement.setString(2, usuario.getEmail());
            statement.setString(3, usuario.getSenha());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("O banco nao retornou o ID do usuario criado.");
                }
                return keys.getLong(1);
            }
        }
    }

    void atualizar(Connection connection, Usuario usuario) throws SQLException {
        boolean alterarSenha = usuario.getSenha() != null && !usuario.getSenha().isBlank();
        String sql = alterarSenha
                ? "UPDATE Usuario SET nome_usuario = ?, email = ?, senha = ? WHERE id_usuario = ?"
                : "UPDATE Usuario SET nome_usuario = ?, email = ? WHERE id_usuario = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, usuario.getNomedeUsuario());
            statement.setString(2, usuario.getEmail());
            if (alterarSenha) {
                statement.setString(3, usuario.getSenha());
                statement.setLong(4, usuario.getID());
            } else {
                statement.setLong(3, usuario.getID());
            }
            statement.executeUpdate();
        }
    }
}
