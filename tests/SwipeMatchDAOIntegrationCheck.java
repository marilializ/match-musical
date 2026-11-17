import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/** Executar apenas em banco de teste; cria e remove um Swipe e um Match. */
public class SwipeMatchDAOIntegrationCheck {
    public static void main(String[] args) throws Exception {
        int[] par = parLivre();
        SwipeMatchDAO dao = new SwipeMatchDAO();
        DashboardDAO dashboard = new DashboardDAO();
        exigir(soma(dashboard.niveisPorCategoria("Cantora"), 1) == 2,
                "Filtro de categoria do grafico 1 nao retornou duas cantoras.");
        exigir(soma(dashboard.instrumentosPorHabilidade("Basico"), 1) > 0,
                "Filtro de habilidade do grafico 2 nao retornou musicos.");
        exigir(soma(dashboard.decisoesPorPeriodo(LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31)), 1) == 8,
                "Filtro de outubro do grafico 3 deveria retornar oito swipes.");
        int antes = totalDecisoes(dashboard);
        int swipe = 0, match = 0;
        try {
            swipe = dao.inserirSwipe("Like", par[0], par[1]);
            int swipeCriado = swipe;
            exigir(dao.listarSwipes().stream().anyMatch(s -> s.id() == swipeCriado), "Swipe inserido nao listado.");
            exigir(totalDecisoes(dashboard) == antes + 1, "Grafico de decisoes nao atualizou.");
            dao.alterarSwipe(swipe, "Dislike");
            exigir(dao.listarSwipes().stream().anyMatch(s -> s.id() == swipeCriado && s.decisao().equals("Dislike")),
                    "Alteracao do Swipe nao persistiu.");
            dao.excluirSwipe(swipe); swipe = 0;
            exigir(totalDecisoes(dashboard) == antes, "Grafico de decisoes nao voltou apos exclusao.");

            match = dao.inserirMatch(par[0], par[1], "Ativo");
            int matchCriado = match;
            exigir(dao.listarMatches().stream().anyMatch(m -> m.id() == matchCriado), "Match inserido nao listado.");
            dao.alterarMatch(match, "Encerrado");
            exigir(dao.listarMatches().stream().anyMatch(m -> m.id() == matchCriado && m.situacao().equals("Encerrado")),
                    "Situacao do Match nao persistiu.");
            exigir(!dao.listarLog(match).isEmpty(), "Trigger nao gravou o log da situacao.");
            dao.excluirMatch(match); match = 0;
            System.out.println("CRUD Swipe/Match, filtros dos graficos 1-3 e log: OK");
        } finally {
            if (swipe != 0) dao.excluirSwipe(swipe);
            if (match != 0) dao.excluirMatch(match);
        }
    }

    private static int totalDecisoes(DashboardDAO dashboard) throws SQLException {
        return soma(dashboard.decisoesPorPeriodo(null, null), 1);
    }

    private static int soma(DashboardDAO.Resultado resultado, int coluna) {
        int total = 0;
        for (Object[] linha : resultado.linhas()) total += ((Number) linha[coluna]).intValue();
        return total;
    }

    private static int[] parLivre() throws SQLException {
        String sql = "SELECT m.id_usuario, g.id_usuario FROM Musico m CROSS JOIN Grupo_Musical g "
                + "WHERE NOT EXISTS (SELECT 1 FROM Swipe s WHERE "
                + "(s.id_usuario_realizou = m.id_usuario AND s.id_usuario_encontrado = g.id_usuario) OR "
                + "(s.id_usuario_realizou = g.id_usuario AND s.id_usuario_encontrado = m.id_usuario)) "
                + "AND NOT EXISTS (SELECT 1 FROM `Match` x WHERE "
                + "(x.id_usuario_a = m.id_usuario AND x.id_usuario_b = g.id_usuario) OR "
                + "(x.id_usuario_a = g.id_usuario AND x.id_usuario_b = m.id_usuario)) "
                + "ORDER BY m.id_usuario, g.id_usuario LIMIT 1";
        try (Connection c = ConnectionFactory.getConnection();
             PreparedStatement s = c.prepareStatement(sql);
             ResultSet r = s.executeQuery()) {
            if (!r.next()) throw new AssertionError("Nao ha par livre para o teste.");
            return new int[] {r.getInt(1), r.getInt(2)};
        }
    }

    private static void exigir(boolean condicao, String mensagem) {
        if (!condicao) throw new AssertionError(mensagem);
    }
}
