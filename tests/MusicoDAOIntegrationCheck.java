import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

public class MusicoDAOIntegrationCheck {
    public static void main(String[] args) throws Exception {
        MusicoDAO dao = new MusicoDAO();
        String sufixo = Long.toString(System.nanoTime());
        Musico musico = new Musico(0, "senha_teste", "teste_" + sufixo + "@example.com",
                "teste_" + sufixo, "Finais de semana", "Musico Teste", null,
                null, "Guitarrista", null, "Intermediario");
        long id = 0;
        try {
            id = dao.inserir(musico);
            exigir(dao.buscarPorId(id).isPresent(), "Cadastro nao apareceu na busca.");
            exigir(dao.listar().stream().anyMatch(item -> item.getID() == musico.getID()),
                    "Cadastro nao apareceu na listagem.");

            musico.setNome("Musico Atualizado");
            musico.setEmail("atualizado_" + sufixo + "@example.com");
            musico.setSenha("");
            exigir(dao.atualizar(musico), "Atualizacao nao encontrou o musico.");
            Musico atualizado = dao.buscarPorId(id).orElseThrow();
            exigir(Objects.equals(atualizado.getNome(), "Musico Atualizado"),
                    "Nome do musico nao foi atualizado.");
            exigir(Objects.equals(atualizado.getEmail(), musico.getEmail()),
                    "E-mail do usuario nao foi atualizado.");

            try (Connection connection = ConnectionFactory.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "INSERT INTO Links (link, id_musico) VALUES (?, ?)")) {
                statement.setString(1, "https://example.com/teste-" + sufixo);
                statement.setLong(2, id);
                statement.executeUpdate();
            }

            Musico duplicado = new Musico(0, "senha_teste", musico.getEmail(),
                    "duplicado_" + sufixo, null, "Duplicado", null,
                    null, null, null, null);
            try {
                dao.inserir(duplicado);
                throw new AssertionError("E-mail duplicado deveria falhar.");
            } catch (SQLException esperado) {
                exigir(esperado.getSQLState().startsWith("23"),
                        "Falha inesperada no teste de UNIQUE: " + esperado.getMessage());
            }
            exigir(contar("SELECT COUNT(*) FROM Usuario WHERE nome_usuario = ?",
                    "duplicado_" + sufixo) == 0,
                    "Usuario duplicado foi inserido.");

            Musico invalido = new Musico(0, "senha_teste",
                    "invalido_" + sufixo + "@example.com", "invalido_" + sufixo,
                    null, "Nivel Invalido", null, null, null, null, "Outro nivel");
            try {
                dao.inserir(invalido);
                throw new AssertionError("Nivel invalido deveria falhar.");
            } catch (SQLException esperado) {
                exigir(esperado.getMessage().contains("chk_musico_nivel_experiencia"),
                        "Falha inesperada no teste de CHECK: " + esperado.getMessage());
            }
            exigir(contar("SELECT COUNT(*) FROM Usuario WHERE nome_usuario = ?",
                    "invalido_" + sufixo) == 0,
                    "A transacao deixou um Usuario sem Musico.");

            exigir(dao.excluir(id), "Exclusao nao encontrou o musico.");
            exigir(dao.buscarPorId(id).isEmpty(), "Musico continuou no banco.");
            exigir(contar("SELECT COUNT(*) FROM Usuario WHERE id_usuario = ?", id) == 0,
                    "Usuario continuou no banco.");
            exigir(contar("SELECT COUNT(*) FROM Links WHERE id_musico = ?", id) == 0,
                    "O link dependente nao foi excluido em cascata.");
            id = 0;
            System.out.println("CRUD Musico: insercao, listagem, alteracao, rollback e exclusao OK");
        } finally {
            if (id != 0) {
                dao.excluir(id);
            }
        }
    }

    private static long contar(String sql, Object parametro) throws SQLException {
        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, parametro);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static void exigir(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new AssertionError(mensagem);
        }
    }
}
