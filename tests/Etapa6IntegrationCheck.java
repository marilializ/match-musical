import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Etapa 6, tarefas 4 e 5: confere, no banco, o que a interface web usa.
 *
 * Parte 1 (so leitura): views e funcoes pelas consultas do ViewsFuncoesDAO.
 * Parte 2 (so leitura): mensagens do SIGNAL de sp_converter_match_em_participacao.
 *   O procedimento faz START TRANSACTION/COMMIT por conta propria, por isso
 *   nao entra na transacao da parte 3; os casos testados sao recusados antes
 *   de qualquer escrita.
 * Parte 3 (escrita com ROLLBACK): tr_swipe_cria_match e tr_match_log_situacao
 *   numa transacao unica, desfeita no fim. As contagens antes e depois tem
 *   de ser iguais.
 *
 * Conversao bem-sucedida e sp_sincronizar_situacao_grupos confirmam dados
 * (COMMIT interno): ficam no roteiro manual (docs/roteiro_teste.md, secao 7).
 */
public class Etapa6IntegrationCheck {

    public static void main(String[] args) throws Exception {
        long[] antes = contagens();

        viewsEFuncoes();
        System.out.println("Views e funcoes: recomendacao, filtros, indicador, integrantes e taxa OK");

        sinaisDoProcedimento();
        System.out.println("Procedimento: SIGNAL de funcao vazia e de match nao Ativo OK");

        triggersComRollback();
        exigir(java.util.Arrays.equals(antes, contagens()),
                "O ROLLBACK nao desfez tudo: contagens de Swipe/Match/Log_Match/Participa mudaram.");
        System.out.println("Triggers: match criado pelo like mutuo, log da mudanca de situacao e ROLLBACK OK");
    }

    // ---------------------------------------------------------------
    // Parte 1: views (vw_recomendacao_vagas, vw_integrantes_grupo) e
    // funcoes (fn_compatibilidade_vaga, fn_taxa_retribuicao_usuario)
    // ---------------------------------------------------------------
    private static void viewsEFuncoes() throws SQLException {
        ViewsFuncoesDAO dao = new ViewsFuncoesDAO();
        for (String objeto : List.of("vw_recomendacao_vagas", "vw_integrantes_grupo",
                "fn_compatibilidade_vaga", "fn_taxa_retribuicao_usuario")) {
            exigir(dao.objetoExiste(objeto), objeto + " nao esta instalado (rode sql/00_reset.sql).");
        }

        int musico = umInteiro("SELECT id_musico FROM vw_recomendacao_vagas ORDER BY id_musico LIMIT 1");
        List<ViewsFuncoesDAO.VagaRecomendada> todas = dao.vagasRecomendadas(musico, null, null);
        exigir(!todas.isEmpty(), "O musico " + musico + " deveria ter vagas recomendadas.");
        exigir(todas.stream().allMatch(v -> v.compatibilidade() != null && v.qtdGenerosEmComum() > 0),
                "Toda vaga recomendada deve ter compatibilidade e ao menos 1 genero em comum.");

        String nivel = todas.get(0).nivelMinimo();
        if (nivel != null) {
            List<ViewsFuncoesDAO.VagaRecomendada> porNivel = dao.vagasRecomendadas(musico, null, nivel);
            exigir(!porNivel.isEmpty() && porNivel.size() <= todas.size()
                    && porNivel.stream().allMatch(v -> nivel.equals(v.nivelMinimo())),
                    "O filtro de nivel nao restringiu o resultado.");
        }
        String trecho = todas.get(0).funcaoInstrumento().substring(0, 3);
        exigir(dao.vagasRecomendadas(musico, trecho, null).stream()
                        .allMatch(v -> v.funcaoInstrumento().toLowerCase().contains(trecho.toLowerCase())),
                "O filtro de funcao trouxe vaga que nao contem o texto digitado.");
        exigir(dao.vagasRecomendadas(musico, "%", null).isEmpty(),
                "'%' digitado deve ser procurado literalmente (LIKE com ESCAPE).");

        ViewsFuncoesDAO.IndicadorCompatibilidade indicador = dao.indicadorCompatibilidade(musico);
        exigir(indicador.compativeis() <= indicador.vagasAbertas(),
                "Mais vagas compativeis do que vagas abertas.");
        exigir(indicador.recomendadas() == todas.size(),
                "Indicador de recomendadas difere da lista da view.");

        int grupo = umInteiro("SELECT id_grupo FROM vw_integrantes_grupo ORDER BY id_grupo LIMIT 1");
        exigir(!dao.integrantesDoGrupo(grupo).isEmpty(), "O grupo " + grupo + " deveria ter integrantes.");
        exigir(dao.integrantesDoGrupo(-1).isEmpty(), "Grupo inexistente nao deve ter integrantes.");

        double taxa = dao.taxaRetribuicao(musico);
        exigir(taxa >= 0 && taxa <= 100, "Taxa de retribuicao fora de 0..100: " + taxa);
    }

    // ---------------------------------------------------------------
    // Parte 2: SIGNAL do procedimento chega ao Java como SQLSTATE 45000
    // ---------------------------------------------------------------
    private static void sinaisDoProcedimento() throws SQLException {
        int ativo = umInteiro("SELECT id_match FROM `Match` WHERE situacao = 'Ativo' ORDER BY id_match LIMIT 1");
        int naoAtivo = umInteiro(
                "SELECT id_match FROM `Match` WHERE situacao <> 'Ativo' ORDER BY id_match LIMIT 1");
        esperarSignal(ativo, "  ", "Informe a funcao");
        esperarSignal(naoAtivo, "Guitarrista", "So um match Ativo pode ser convertido");
    }

    private static void esperarSignal(int idMatch, String funcao, String trecho) throws SQLException {
        try (Connection connection = ConnectionFactory.getConnection();
             CallableStatement call = connection.prepareCall("{CALL sp_converter_match_em_participacao(?, ?)}")) {
            call.setInt(1, idMatch);
            call.setString(2, funcao);
            call.execute();
            throw new AssertionError("A conversao do match " + idMatch + " deveria ser recusada.");
        } catch (SQLException esperado) {
            exigir("45000".equals(esperado.getSQLState()) && esperado.getMessage().contains(trecho),
                    "Erro inesperado do procedimento: " + esperado.getSQLState() + " " + esperado.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // Parte 3: triggers numa transacao desfeita no fim
    // ---------------------------------------------------------------
    private static void triggersComRollback() throws SQLException {
        ProcedimentosTriggersDAO dao = new ProcedimentosTriggersDAO();
        try (Connection connection = ConnectionFactory.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int[] par = parSemSwipe(connection);
                int musico = par[0];
                int grupo = par[1];

                inserirSwipe(connection, musico, grupo, "Like");
                exigir(dao.matchDoPar(connection, musico, grupo) == null,
                        "Like de um lado so nao pode criar match.");

                inserirSwipe(connection, grupo, musico, "Super Like");
                Integer idMatch = dao.matchDoPar(connection, grupo, musico);
                exigir(idMatch != null, "O like mutuo deveria criar o match (tr_swipe_cria_match).");
                exigir("Ativo".equals(texto(connection,
                                "SELECT situacao FROM `Match` WHERE id_match = ?", idMatch)),
                        "O match criado pelo trigger deveria estar Ativo.");
                exigir(contar(connection, "SELECT COUNT(*) FROM `Match` WHERE LEAST(id_usuario_a, id_usuario_b) = LEAST(?, ?) "
                                + "AND GREATEST(id_usuario_a, id_usuario_b) = GREATEST(?, ?)",
                                musico, grupo, musico, grupo) == 1,
                        "O par deveria ter exatamente 1 match.");

                try {
                    inserirSwipe(connection, musico, grupo, "Talvez");
                    throw new AssertionError("Decisao fora do CHECK deveria falhar.");
                } catch (SQLException esperado) {
                    exigir(esperado.getErrorCode() == 3819, "Erro inesperado no CHECK: " + esperado.getMessage());
                }

                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE `Match` SET situacao = 'Encerrado' WHERE id_match = ?")) {
                    update.setInt(1, idMatch);
                    update.executeUpdate();
                }
                exigir(contar(connection, "SELECT COUNT(*) FROM Log_Match WHERE id_match = ? "
                                + "AND situacao_anterior = 'Ativo' AND situacao_nova = 'Encerrado' "
                                + "AND usuario_banco IS NOT NULL", idMatch) == 1,
                        "tr_match_log_situacao deveria gravar 1 linha Ativo -> Encerrado.");
            } finally {
                connection.rollback();
            }
        }
    }

    /** Primeiro musico e grupo ativo sem swipe entre eles e sem match. */
    private static int[] parSemSwipe(Connection connection) throws SQLException {
        String sql = """
                SELECT mu.id_usuario, g.id_usuario
                  FROM Musico mu
                 CROSS JOIN Grupo_Musical g
                 WHERE NOT EXISTS (SELECT 1 FROM Swipe s
                                    WHERE (s.id_usuario_realizou = mu.id_usuario AND s.id_usuario_encontrado = g.id_usuario)
                                       OR (s.id_usuario_realizou = g.id_usuario AND s.id_usuario_encontrado = mu.id_usuario))
                   AND NOT EXISTS (SELECT 1 FROM `Match` m
                                    WHERE LEAST(m.id_usuario_a, m.id_usuario_b) = LEAST(mu.id_usuario, g.id_usuario)
                                      AND GREATEST(m.id_usuario_a, m.id_usuario_b) = GREATEST(mu.id_usuario, g.id_usuario))
                 ORDER BY mu.id_usuario, g.id_usuario
                 LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            exigir(result.next(), "Nao ha par musico/grupo sem swipe para o teste.");
            return new int[]{result.getInt(1), result.getInt(2)};
        }
    }

    private static void inserirSwipe(Connection connection, int de, int para, String decisao) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO Swipe (tipo_decisao, id_usuario_realizou, id_usuario_encontrado) VALUES (?, ?, ?)")) {
            statement.setString(1, decisao);
            statement.setInt(2, de);
            statement.setInt(3, para);
            statement.executeUpdate();
        }
    }

    private static long[] contagens() throws SQLException {
        try (Connection connection = ConnectionFactory.getConnection()) {
            return new long[]{
                    contar(connection, "SELECT COUNT(*) FROM Swipe"),
                    contar(connection, "SELECT COUNT(*) FROM `Match`"),
                    contar(connection, "SELECT COUNT(*) FROM Log_Match"),
                    contar(connection, "SELECT COUNT(*) FROM Participa")};
        }
    }

    private static int umInteiro(String sql) throws SQLException {
        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            exigir(result.next(), "Consulta sem resultado: " + sql);
            return result.getInt(1);
        }
    }

    private static long contar(Connection connection, String sql, int... parametros) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) {
                statement.setInt(i + 1, parametros[i]);
            }
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static String texto(Connection connection, String sql, int parametro) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, parametro);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        }
    }

    private static void exigir(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new AssertionError(mensagem);
        }
    }
}
