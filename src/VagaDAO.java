import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class VagaDAO extends BaseDAO {
    public record GrupoOpcao(long id, String nome) {
        @Override
        public String toString() {
            return nome + " (#" + id + ")";
        }
    }

    private static final String SELECT_VAGAS = """
            SELECT v.id_vaga, v.id_grupo_musical, g.nome_grupo, v.situacao,
                   v.descricao, v.nivel_minimo, v.funcao_instrumento,
                   r.id_requisito, r.descricao AS descricao_requisito
            FROM Vaga v
            JOIN Grupo_Musical g ON g.id_usuario = v.id_grupo_musical
            LEFT JOIN Requisito_Vaga r ON r.id_vaga = v.id_vaga
            """;

    public long inserir(Vaga vaga) throws SQLException {
        long id = inTransaction(connection -> {
            String sql = """
                    INSERT INTO Vaga (situacao, descricao, nivel_minimo,
                                      funcao_instrumento, id_grupo_musical)
                    VALUES (?, ?, ?, ?, ?)
                    """;
            long novoId;
            try (PreparedStatement statement = connection.prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS)) {
                preencherVaga(statement, vaga);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("O banco nao retornou o ID da vaga criada.");
                    }
                    novoId = keys.getLong(1);
                }
            }
            salvarRequisitos(connection, novoId, vaga.getRequisitos());
            return novoId;
        });
        vaga.setId(id);
        return id;
    }

    public boolean atualizar(Vaga vaga) throws SQLException {
        return inTransaction(connection -> {
            try (PreparedStatement verificar = connection.prepareStatement(
                    "SELECT id_vaga FROM Vaga WHERE id_vaga = ? FOR UPDATE")) {
                verificar.setLong(1, vaga.getId());
                try (ResultSet result = verificar.executeQuery()) {
                    if (!result.next()) {
                        return false;
                    }
                }
            }

            String sql = """
                    UPDATE Vaga SET situacao = ?, descricao = ?, nivel_minimo = ?,
                                    funcao_instrumento = ?, id_grupo_musical = ?
                    WHERE id_vaga = ?
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                preencherVaga(statement, vaga);
                statement.setLong(6, vaga.getId());
                statement.executeUpdate();
            }
            salvarRequisitos(connection, vaga.getId(), vaga.getRequisitos());
            return true;
        });
    }

    public boolean excluir(long id) throws SQLException {
        return update("DELETE FROM Vaga WHERE id_vaga = ?",
                statement -> statement.setLong(1, id)) == 1;
    }

    public Optional<Vaga> buscarPorId(long id) throws SQLException {
        return consultar(" WHERE v.id_vaga = ?", id).stream().findFirst();
    }

    public List<Vaga> listar() throws SQLException {
        return consultar("", null);
    }

    public List<GrupoOpcao> listarGrupos() throws SQLException {
        return query("SELECT id_usuario, nome_grupo FROM Grupo_Musical ORDER BY nome_grupo, id_usuario",
                statement -> { }, result -> new GrupoOpcao(
                        result.getLong("id_usuario"), result.getString("nome_grupo")));
    }

    private List<Vaga> consultar(String filtro, Long id) throws SQLException {
        String sql = SELECT_VAGAS + filtro + " ORDER BY v.id_vaga, r.id_requisito";
        Map<Long, DadosVaga> vagas = new LinkedHashMap<>();
        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (id != null) {
                statement.setLong(1, id);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long idVaga = result.getLong("id_vaga");
                    DadosVaga dados = vagas.get(idVaga);
                    if (dados == null) {
                        dados = new DadosVaga(result.getLong("id_grupo_musical"),
                                result.getString("nome_grupo"), result.getString("situacao"),
                                result.getString("descricao"), result.getString("nivel_minimo"),
                                result.getString("funcao_instrumento"));
                        vagas.put(idVaga, dados);
                    }
                    int idRequisito = result.getInt("id_requisito");
                    if (!result.wasNull()) {
                        dados.requisitos.add(new RequisitoVaga(
                                idRequisito, result.getString("descricao_requisito")));
                    }
                }
            }
        }
        List<Vaga> resposta = new ArrayList<>();
        for (Map.Entry<Long, DadosVaga> item : vagas.entrySet()) {
            DadosVaga dados = item.getValue();
            resposta.add(new Vaga(item.getKey(), dados.idGrupo, dados.nomeGrupo,
                    dados.situacao, dados.descricao, dados.nivelMinimo,
                    dados.funcaoInstrumento, dados.requisitos));
        }
        return resposta;
    }

    private static void preencherVaga(PreparedStatement statement, Vaga vaga) throws SQLException {
        statement.setString(1, vaga.getSituacao());
        statement.setString(2, vaga.getDescricao());
        statement.setString(3, vaga.getNivelMinimo());
        statement.setString(4, vaga.getFuncaoInstrumento());
        statement.setLong(5, vaga.getIdGrupoMusical());
    }

    private static void salvarRequisitos(Connection connection, long idVaga,
                                         List<RequisitoVaga> requisitos) throws SQLException {
        // O identificador do requisito e local a vaga: 1, 2, 3... para cada id_vaga.
        try (PreparedStatement remover = connection.prepareStatement(
                "DELETE FROM Requisito_Vaga WHERE id_vaga = ? AND id_requisito > ?")) {
            remover.setLong(1, idVaga);
            remover.setInt(2, requisitos.size());
            remover.executeUpdate();
        }
        String sql = """
                INSERT INTO Requisito_Vaga (id_vaga, id_requisito, descricao)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE descricao = ?
                """;
        try (PreparedStatement salvar = connection.prepareStatement(sql)) {
            for (int indice = 0; indice < requisitos.size(); indice++) {
                String descricao = requisitos.get(indice).descricao();
                if (descricao == null || descricao.isBlank()) {
                    throw new SQLException("Requisito nao pode ter descricao vazia.");
                }
                salvar.setLong(1, idVaga);
                salvar.setInt(2, indice + 1);
                salvar.setString(3, descricao.trim());
                salvar.setString(4, descricao.trim());
                salvar.executeUpdate();
            }
        }
    }

    private static class DadosVaga {
        final long idGrupo;
        final String nomeGrupo;
        final String situacao;
        final String descricao;
        final String nivelMinimo;
        final String funcaoInstrumento;
        final List<RequisitoVaga> requisitos = new ArrayList<>();

        DadosVaga(long idGrupo, String nomeGrupo, String situacao, String descricao,
                  String nivelMinimo, String funcaoInstrumento) {
            this.idGrupo = idGrupo;
            this.nomeGrupo = nomeGrupo;
            this.situacao = situacao;
            this.descricao = descricao;
            this.nivelMinimo = nivelMinimo;
            this.funcaoInstrumento = funcaoInstrumento;
        }
    }
}
