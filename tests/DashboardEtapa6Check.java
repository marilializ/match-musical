import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Verifica a parte da Etapa 6 que fechou as frentes 3 e 6:
 *  - as 12 consultas (Etapas 3 e 4) executam, e os filtros por parametro filtram;
 *  - os indicadores e os graficos 4 a 6 batem com contagens feitas por SQL direto;
 *  - mediana/moda (Java) e Pearson (SQL) conferem com calculos independentes;
 *  - teste de aceitacao do enunciado: inserir um swipe muda o grafico ao recarregar.
 *
 * Executar apenas em banco de teste recriado pelo sql/00_reset.sql:
 * cria e apaga um Swipe (Dislike, que nao dispara o trigger de match).
 */
public class DashboardEtapa6Check {
    private static final DashboardDAO DASHBOARD = new DashboardDAO();

    public static void main(String[] args) throws Exception {
        consultas();
        indicadores();
        tendenciaSemanal();
        mensagensPorMatch();
        dispersaoEPearson();
        aceitacaoSwipeMudaGraficos();
        System.out.println("Etapa 6 (consultas 1-12, indicadores e graficos 4-6): OK");
    }

    private static void consultas() throws SQLException {
        exigir(ConsultaSQL.TODAS.size() == 12, "Deveriam ser 12 consultas.");
        for (int i = 1; i <= 12; i++) {
            exigir(ConsultaSQL.numero(i).numero() == i, "Consulta fora de ordem: " + i);
        }
        for (ConsultaSQL consulta : ConsultaSQL.TODAS) {
            Object valor = consulta.usaMusico() ? Long.valueOf(primeiroId("SELECT id_usuario FROM Musico")) : null;
            DashboardDAO.Resultado resultado = DASHBOARD.executar(consulta, valor);
            exigir(!resultado.colunas().isEmpty(), "Consulta " + consulta.numero() + " sem colunas.");
            if (consulta.colunaDestaque() != null) {
                exigir(resultado.colunas().contains(consulta.colunaDestaque()),
                        "Coluna de destaque inexistente na consulta " + consulta.numero());
            }
        }

        // 12: auditoria com filtro de diagnostico
        ConsultaSQL auditoria = ConsultaSQL.numero(12);
        int total = DASHBOARD.executar(auditoria, null).linhas().size();
        int somaPorDiagnostico = 0;
        for (String diagnostico : ConsultaSQL.DIAGNOSTICOS) {
            DashboardDAO.Resultado filtrado = DASHBOARD.executar(auditoria, diagnostico);
            int coluna = filtrado.indice("diagnostico");
            for (Object[] linha : filtrado.linhas()) {
                exigir(diagnostico.equals(linha[coluna]), "Filtro da auditoria deixou passar " + linha[coluna]);
            }
            somaPorDiagnostico += filtrado.linhas().size();
        }
        exigir(total == somaPorDiagnostico, "Auditoria filtrada nao soma o total (" + somaPorDiagnostico + " x " + total + ").");
        exigir(total == contar("SELECT COUNT(*) FROM `Match`")
                        + DASHBOARD.executar(auditoria, "Like mutuo sem match").linhas().size(),
                "Auditoria deveria ter 1 linha por Match + os likes mutuos sem Match.");

        // 10: subconsulta correlacionada filtrada por instrumento
        DashboardDAO.Opcao instrumento = DASHBOARD.instrumentos().get(0);
        DashboardDAO.Resultado porInstrumento = DASHBOARD.executar(ConsultaSQL.numero(10), instrumento.id());
        for (Object[] linha : porInstrumento.linhas()) {
            exigir(instrumento.nome().equals(linha[porInstrumento.indice("instrumento")]), "Filtro de instrumento falhou.");
            double anos = ((Number) linha[porInstrumento.indice("anos_experiencia")]).doubleValue();
            double media = ((Number) linha[porInstrumento.indice("media_do_instrumento")]).doubleValue();
            exigir(anos > media - 0.05, "Consulta 10 trouxe alguem abaixo da media.");
        }

        // 9: anti join filtrado por categoria
        String categoria = DASHBOARD.categorias().get(0);
        DashboardDAO.Resultado porCategoria = DASHBOARD.executar(ConsultaSQL.numero(9), categoria);
        for (Object[] linha : porCategoria.linhas()) {
            exigir(categoria.equals(linha[porCategoria.indice("categoria_principal")]), "Filtro de categoria falhou.");
        }
        exigir(DASHBOARD.executar(ConsultaSQL.numero(9), null).linhas().size()
                        == contar("SELECT COUNT(*) FROM Musico m WHERE NOT EXISTS "
                        + "(SELECT 1 FROM Participa p WHERE p.id_musico = m.id_usuario)"),
                "Anti join nao bate com NOT EXISTS.");
        System.out.println("12 consultas e filtros por parametro: OK");
    }

    private static void indicadores() throws SQLException {
        DashboardDAO.Painel painel = DASHBOARD.painel(null, null);
        exigir(painel.musicos() == contar("SELECT COUNT(*) FROM Musico"), "Total de musicos errado.");
        exigir(painel.grupos() == contar("SELECT COUNT(*) FROM Grupo_Musical"), "Total de grupos errado.");
        exigir(painel.matchesNoPeriodo() == contar("SELECT COUNT(*) FROM `Match`"), "Total de matches errado.");
        exigir(painel.matchesAtivos() == contar("SELECT COUNT(*) FROM `Match` WHERE situacao = 'Ativo'"),
                "Matches ativos errado.");
        double esperado = (double) contar("SELECT COUNT(*) FROM Mensagem") / contar("SELECT COUNT(*) FROM `Match`");
        exigir(Math.abs(painel.mediaMensagensPorMatch() - esperado) < 0.01, "Media de mensagens por match errada.");
        exigir(painel.taxaMediaRetribuicao() != null, "Taxa media de retribuicao deveria vir da funcao da Etapa 5.");
        DashboardDAO.Painel outubro = DASHBOARD.painel(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        exigir(outubro.matchesNoPeriodo() == contar("SELECT COUNT(*) FROM `Match` WHERE data_criacao >= '2026-10-01' "
                + "AND data_criacao < '2026-11-01'"), "Filtro de periodo dos indicadores falhou.");
        System.out.println("Indicadores (6 cartoes + taxa media): OK");
    }

    private static void tendenciaSemanal() throws SQLException {
        List<DashboardDAO.Semana> semanas = DASHBOARD.atividadeSemanal(null, null);
        exigir(semanas.size() >= 4, "Serie semanal curta demais para mostrar tendencia.");
        int swipes = 0, matches = 0;
        for (int i = 0; i < semanas.size(); i++) {
            swipes += semanas.get(i).swipes();
            matches += semanas.get(i).matches();
            if (i > 0) exigir(semanas.get(i).inicio().equals(semanas.get(i - 1).inicio().plusWeeks(1)),
                    "Semanas nao sao continuas.");
        }
        exigir(swipes == contar("SELECT COUNT(*) FROM Swipe"), "Soma semanal de swipes errada.");
        exigir(matches == contar("SELECT COUNT(*) FROM `Match`"), "Soma semanal de matches errada.");
        System.out.println("Grafico 4 (" + semanas.size() + " semanas): OK");
    }

    private static void mensagensPorMatch() throws SQLException {
        DashboardDAO.Distribuicao d = DASHBOARD.mensagensPorMatch(null, null, null);
        int n = 0;
        for (int[] faixa : d.frequencias()) n += faixa[1];
        exigir(n == d.n() && n == contar("SELECT COUNT(*) FROM `Match`"), "Histograma nao cobre todos os matches.");

        // Mediana e moda conferidas contra a lista crua ordenada
        List<Integer> crus = new ArrayList<>();
        try (Connection c = ConnectionFactory.getConnection();
             PreparedStatement s = c.prepareStatement("SELECT COUNT(me.id_mensagem) FROM `Match` m "
                     + "LEFT JOIN Mensagem me ON me.id_match = m.id_match GROUP BY m.id_match");
             ResultSet r = s.executeQuery()) {
            while (r.next()) crus.add(r.getInt(1));
        }
        Collections.sort(crus);
        double medianaEsperada = crus.size() % 2 == 1 ? crus.get(crus.size() / 2)
                : (crus.get(crus.size() / 2 - 1) + crus.get(crus.size() / 2)) / 2.0;
        exigir(d.mediana() == medianaEsperada, "Mediana errada: " + d.mediana() + " x " + medianaEsperada);
        int maior = 0;
        for (int v : crus) maior = Math.max(maior, Collections.frequency(crus, v));
        for (int moda : d.modas()) exigir(Collections.frequency(crus, moda) == maior, "Moda errada.");
        exigir(Math.abs(d.variancia() - d.desvioPadrao() * d.desvioPadrao()) < 1e-6, "Variancia != desvio^2.");

        String situacao = DASHBOARD.situacoesMatch().get(0);
        exigir(DASHBOARD.mensagensPorMatch(situacao, null, null).n()
                        == contar("SELECT COUNT(*) FROM `Match` WHERE situacao = '" + situacao.replace("'", "''") + "'"),
                "Filtro de situacao do grafico 5 falhou.");
        System.out.println("Grafico 5 (media " + String.format("%.2f", d.media()) + ", mediana " + d.mediana()
                + ", moda " + d.modas() + ", dp " + String.format("%.2f", d.desvioPadrao()) + "): OK");
    }

    private static void dispersaoEPearson() throws SQLException {
        List<DashboardDAO.PontoMusico> pontos = DASHBOARD.instrumentosVersusExperiencia(null);
        exigir(pontos.size() >= 10, "Poucos musicos no grafico de dispersao.");
        double r = DASHBOARD.pearsonInstrumentosExperiencia(null);
        double rJava = pearson(pontos);
        exigir(Math.abs(r - rJava) < 1e-6, "Pearson do SQL (" + r + ") difere do calculo em Java (" + rJava + ").");
        exigir(r >= -1 && r <= 1, "Pearson fora de [-1, 1].");

        DashboardDAO.Opcao instrumento = DASHBOARD.instrumentos().get(0);
        int esperados = contar("SELECT COUNT(DISTINCT id_musico) FROM Toca WHERE id_instrumento = " + instrumento.id()
                + " AND id_musico IN (SELECT id_musico FROM Toca GROUP BY id_musico HAVING AVG(tempo_experiencia) IS NOT NULL)");
        exigir(DASHBOARD.instrumentosVersusExperiencia(instrumento.id()).size() == esperados,
                "Filtro de instrumento do grafico 6 falhou.");
        System.out.println("Grafico 6 (r = " + String.format("%.3f", r) + ", n = " + pontos.size() + "): OK");
    }

    /** Teste de aceitacao do enunciado: um swipe novo muda os graficos ao recarregar. */
    private static void aceitacaoSwipeMudaGraficos() throws SQLException {
        int[] par = parLivre();
        SwipeMatchDAO crud = new SwipeMatchDAO();
        int antes = somaSwipes();
        int matchesAntes = DASHBOARD.painel(null, null).matchesNoPeriodo();
        int swipe = 0;
        try {
            swipe = crud.inserirSwipe("Dislike", par[0], par[1]);
            exigir(somaSwipes() == antes + 1, "Grafico 4 nao contou o swipe novo.");
            exigir(DASHBOARD.painel(null, null).matchesNoPeriodo() == matchesAntes, "Dislike nao deveria criar match.");
            crud.excluirSwipe(swipe);
            swipe = 0;
            exigir(somaSwipes() == antes, "Grafico 4 nao voltou depois de excluir o swipe.");
        } finally {
            if (swipe != 0) crud.excluirSwipe(swipe);
        }
        System.out.println("Aceitacao (swipe pelo CRUD muda o grafico): OK");
    }

    private static int somaSwipes() throws SQLException {
        int total = 0;
        for (DashboardDAO.Semana semana : DASHBOARD.atividadeSemanal(null, null)) total += semana.swipes();
        return total;
    }

    private static double pearson(List<DashboardDAO.PontoMusico> pontos) {
        int n = pontos.size();
        double mx = 0, my = 0;
        for (DashboardDAO.PontoMusico p : pontos) { mx += p.instrumentos(); my += p.mediaAnos(); }
        mx /= n; my /= n;
        double sxy = 0, sxx = 0, syy = 0;
        for (DashboardDAO.PontoMusico p : pontos) {
            double dx = p.instrumentos() - mx, dy = p.mediaAnos() - my;
            sxy += dx * dy; sxx += dx * dx; syy += dy * dy;
        }
        return sxy / Math.sqrt(sxx * syy);
    }

    private static int[] parLivre() throws SQLException {
        String sql = "SELECT m.id_usuario, g.id_usuario FROM Musico m CROSS JOIN Grupo_Musical g "
                + "WHERE g.situacao = 'Ativo' AND NOT EXISTS (SELECT 1 FROM Swipe s WHERE "
                + "(s.id_usuario_realizou = m.id_usuario AND s.id_usuario_encontrado = g.id_usuario) OR "
                + "(s.id_usuario_realizou = g.id_usuario AND s.id_usuario_encontrado = m.id_usuario)) "
                + "ORDER BY m.id_usuario, g.id_usuario LIMIT 1";
        try (Connection c = ConnectionFactory.getConnection();
             PreparedStatement s = c.prepareStatement(sql);
             ResultSet r = s.executeQuery()) {
            if (!r.next()) throw new AssertionError("Nao ha par livre para o teste.");
            return new int[] {r.getInt(1), r.getInt(2)};
        }
    }

    private static int contar(String sql) throws SQLException {
        try (Connection c = ConnectionFactory.getConnection();
             PreparedStatement s = c.prepareStatement(sql);
             ResultSet r = s.executeQuery()) {
            r.next();
            return r.getInt(1);
        }
    }

    private static int primeiroId(String sql) throws SQLException {
        return contar(sql + " ORDER BY 1 LIMIT 1");
    }

    private static void exigir(boolean condicao, String mensagem) {
        if (!condicao) throw new AssertionError(mensagem);
    }
}
