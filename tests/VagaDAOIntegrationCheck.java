import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public class VagaDAOIntegrationCheck {
    public static void main(String[] args) throws Exception {
        VagaDAO dao = new VagaDAO();
        VagaDAO.GrupoOpcao grupo = dao.listarGrupos().stream().findFirst()
                .orElseThrow(() -> new AssertionError("O teste exige um grupo cadastrado."));
        Vaga primeira = new Vaga(0, grupo.id(), grupo.nome(), "Aberta", "Vaga de teste",
                "Basico", "Guitarrista", List.of(
                        new RequisitoVaga(1, "Tocar guitarra"),
                        new RequisitoVaga(2, "Ensaiar aos sabados")));
        Vaga segunda = new Vaga(0, grupo.id(), grupo.nome(), "Aberta", "Segunda vaga de teste",
                null, "Baixista", List.of(new RequisitoVaga(1, "Tocar baixo")));
        long idPrimeira = 0;
        long idSegunda = 0;
        try {
            idPrimeira = dao.inserir(primeira);
            idSegunda = dao.inserir(segunda);
            exigir(dao.listar().stream().anyMatch(vaga -> vaga.getId() == primeira.getId()),
                    "Vaga criada nao apareceu na listagem.");
            exigir(contarRequisitos(idPrimeira) == 2, "Primeira vaga deveria ter 2 requisitos.");
            exigir(contarRequisitos(idSegunda) == 1, "Segunda vaga deveria ter 1 requisito.");
            exigir(primeira.getRequisitos().get(0).idRequisito() == 1
                    && segunda.getRequisitos().get(0).idRequisito() == 1,
                    "IDs de requisito deveriam ser locais a cada vaga.");

            Vaga alterada = new Vaga(idPrimeira, grupo.id(), grupo.nome(), "Fechada",
                    "Vaga atualizada", "Avancado", "Guitarrista", List.of(
                    new RequisitoVaga(1, "Tocar guitarra solo"),
                    new RequisitoVaga(2, "Ensaiar aos sabados"),
                    new RequisitoVaga(3, "Ter instrumento proprio")));
            exigir(dao.atualizar(alterada), "Atualizacao nao encontrou a vaga.");
            Vaga lida = dao.buscarPorId(idPrimeira).orElseThrow();
            exigir(Objects.equals(lida.getSituacao(), "Fechada")
                    && Objects.equals(lida.getDescricao(), "Vaga atualizada")
                    && lida.getRequisitos().size() == 3
                    && Objects.equals(lida.getRequisitos().get(0).descricao(), "Tocar guitarra solo"),
                    "Atualizacao da vaga e dos requisitos nao foi persistida.");

            Vaga invalida = new Vaga(idPrimeira, grupo.id(), grupo.nome(), "Aberta",
                    "Nao deve persistir", "Basico", "Guitarrista", List.of(
                    new RequisitoVaga(1, "Valido"), new RequisitoVaga(2, " ")));
            try {
                dao.atualizar(invalida);
                throw new AssertionError("Requisito vazio deveria falhar.");
            } catch (SQLException esperado) {
                exigir(esperado.getMessage().contains("Requisito nao pode"),
                        "Erro inesperado no teste de rollback: " + esperado.getMessage());
            }
            lida = dao.buscarPorId(idPrimeira).orElseThrow();
            exigir(Objects.equals(lida.getDescricao(), "Vaga atualizada")
                    && lida.getRequisitos().size() == 3,
                    "A transacao deixou uma atualizacao parcial.");

            Vaga grupoInvalido = new Vaga(idPrimeira, 2_000_000_000L, "Inexistente",
                    "Aberta", "Grupo inexistente", null, "Guitarrista",
                    List.of(new RequisitoVaga(1, "Outro requisito")));
            try {
                dao.atualizar(grupoInvalido);
                throw new AssertionError("Grupo inexistente deveria falhar por FK.");
            } catch (SQLException esperado) {
                exigir(esperado.getErrorCode() == 1452,
                        "Erro inesperado no teste de FK: " + esperado.getMessage());
            }
            lida = dao.buscarPorId(idPrimeira).orElseThrow();
            exigir(Objects.equals(lida.getDescricao(), "Vaga atualizada")
                    && lida.getRequisitos().size() == 3,
                    "Erro de FK deixou uma atualizacao parcial.");

            Vaga reduzida = new Vaga(idPrimeira, grupo.id(), grupo.nome(), "Aberta",
                    "Vaga com um requisito", null, "Guitarrista",
                    List.of(new RequisitoVaga(1, "Tocar guitarra")));
            exigir(dao.atualizar(reduzida), "Reducao dos requisitos falhou.");
            exigir(contarRequisitos(idPrimeira) == 1,
                    "Requisitos removidos ainda aparecem no banco.");

            exigir(dao.excluir(idPrimeira), "Exclusao nao encontrou a vaga.");
            exigir(dao.buscarPorId(idPrimeira).isEmpty(), "Vaga ainda aparece apos exclusao.");
            exigir(contarRequisitos(idPrimeira) == 0,
                    "Exclusao da vaga nao removeu requisitos em cascata.");
            exigir(contarRequisitos(idSegunda) == 1,
                    "Exclusao afetou os requisitos de outra vaga.");
            idPrimeira = 0;
            exigir(dao.excluir(idSegunda), "Exclusao da segunda vaga falhou.");
            idSegunda = 0;
            System.out.println("CRUD Vaga: insercao, listagem, alteracao, rollback e cascata OK");
        } finally {
            if (idPrimeira != 0) {
                dao.excluir(idPrimeira);
            }
            if (idSegunda != 0) {
                dao.excluir(idSegunda);
            }
        }
    }

    private static long contarRequisitos(long idVaga) throws SQLException {
        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM Requisito_Vaga WHERE id_vaga = ?")) {
            statement.setLong(1, idVaga);
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
