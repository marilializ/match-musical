import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Interface web local do SYNC. Escuta apenas em 127.0.0.1.
 * A selecao do musico e demonstrativa; nao substitui autenticacao.
 */
public final class WebMain {
    private static final String MATCH_TABLE = (char) 96 + "Match" + (char) 96;
    private static final Path WEB_ROOT = Path.of(System.getProperty("sync.web.dir", "web")).toAbsolutePath().normalize();
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("SYNC_WEB_PORT", "8080"));
    private static final ViewsFuncoesDAO VIEWS_FUNCOES = new ViewsFuncoesDAO();
    private static final ProcedimentosTriggersDAO PROCEDIMENTOS_TRIGGERS = new ProcedimentosTriggersDAO();
    private static final DashboardDAO DASHBOARD = new DashboardDAO();
    private static final DateTimeFormatter DATA_HORA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<String> SITUACOES_MATCH = List.of("Ativo", "Encerrado", "Convertido", "Pendente");
    private static final List<String> NIVEIS =
            List.of("Iniciante", "Basico", "Intermediario", "Avancado", "Profissional");

    private WebMain() {}

    public static void main(String[] args) throws IOException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException error) {
            throw new IllegalStateException("Driver MySQL nao encontrado. Execute com Maven ou inclua mysql-connector-j no classpath.", error);
        }
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
        server.createContext("/api/bootstrap", WebMain::bootstrap);
        server.createContext("/api/swipes", WebMain::swipe);
        server.createContext("/api/matches/convert", WebMain::convertMatch);
        server.createContext("/api/groups/sync", WebMain::syncGroups);
        server.createContext("/api/vacancies/recommended", WebMain::recommendedVacancies);
        server.createContext("/api/groups/members", WebMain::groupMembers);
        server.createContext("/api/matches/log", WebMain::matchLogFiltered);
        server.createContext("/api/dashboard", WebMain::dashboard);
        server.createContext("/api/consultas", WebMain::listarConsultas);
        server.createContext("/api/consultas/executar", WebMain::executarConsulta);
        server.createContext("/", WebMain::staticFile);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("SYNC Web: http://127.0.0.1:" + PORT);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
    }

    private static void bootstrap(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, map("error", "Método não permitido."));
            return;
        }
        try (Connection connection = ConnectionFactory.getConnection()) {
            List<Map<String, Object>> musicians = musicians(connection);
            int requested = integer(form(exchange.getRequestURI().getRawQuery()).get("user"), 0);
            int user = musicians.stream().anyMatch(m -> ((Number) m.get("id")).intValue() == requested)
                    ? requested : musicians.isEmpty() ? 0 : ((Number) musicians.get(0).get("id")).intValue();
            // Cada secao confere se o seu objeto da Etapa 4/5 esta instalado:
            // se faltar um, so aquela secao mostra o aviso.
            Map<String, Object> objects = map(
                    "fn_compatibilidade_vaga", VIEWS_FUNCOES.objetoExiste("fn_compatibilidade_vaga"),
                    "fn_taxa_retribuicao_usuario", VIEWS_FUNCOES.objetoExiste("fn_taxa_retribuicao_usuario"),
                    "vw_recomendacao_vagas", VIEWS_FUNCOES.objetoExiste("vw_recomendacao_vagas"),
                    "vw_integrantes_grupo", VIEWS_FUNCOES.objetoExiste("vw_integrantes_grupo")
            );
            Map<String, Object> body = map(
                    "musicians", musicians,
                    "groups", groups(connection, user),
                    "vacancies", vacancies(connection, user, (Boolean) objects.get("fn_compatibilidade_vaga")),
                    "matches", matches(connection, user),
                    "stats", stats(connection, user, (Boolean) objects.get("fn_taxa_retribuicao_usuario")),
                    "matchLog", matchLog(connection),
                    "objects", objects
            );
            sendJson(exchange, 200, body);
        } catch (SQLException error) {
            System.err.println("Falha ao carregar interface web: " + error.getMessage());
            sendJson(exchange, 503, map("error", "Banco de dados indisponível. Confira a conexão SYNC_DB_* e o seed."));
        }
    }

    private static void swipe(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, map("error", "Método não permitido."));
            return;
        }
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && !origin.equals("http://127.0.0.1:" + PORT)) {
            sendJson(exchange, 403, map("error", "Origem não permitida."));
            return;
        }
        byte[] rawBody = exchange.getRequestBody().readNBytes(2049);
        if (rawBody.length > 2048) {
            sendJson(exchange, 413, map("error", "Requisição muito grande."));
            return;
        }
        Map<String, String> form;
        try {
            form = form(new String(rawBody, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException error) {
            sendJson(exchange, 400, map("error", "Formulário inválido."));
            return;
        }
        int user = integer(form.get("user"), 0);
        int target = integer(form.get("target"), 0);
        String decision = form.getOrDefault("decision", "");
        if (user <= 0 || target <= 0 || user == target ||
                !(decision.equals("Like") || decision.equals("Super Like") || decision.equals("Dislike"))) {
            sendJson(exchange, 400, map("error", "Swipe inválido."));
            return;
        }
        try (Connection connection = ConnectionFactory.getConnection()) {
            connection.setAutoCommit(false);
            try {
                // Trava a linha do musico para impedir dois swipes simultaneos na interface local.
                if (!exists(connection, "SELECT 1 FROM Musico WHERE id_usuario = ? FOR UPDATE", user) ||
                    !exists(connection, "SELECT 1 FROM Grupo_Musical WHERE id_usuario = ? AND situacao = 'Ativo'", target)) {
                    connection.rollback();
                    sendJson(exchange, 404, map("error", "Músico não encontrado ou grupo inativo."));
                    return;
                }
                try (PreparedStatement check = connection.prepareStatement(
                        "SELECT 1 FROM Swipe WHERE id_usuario_realizou = ? AND id_usuario_encontrado = ? LIMIT 1")) {
                    check.setInt(1, user);
                    check.setInt(2, target);
                    try (ResultSet rows = check.executeQuery()) {
                        if (rows.next()) {
                            connection.rollback();
                            sendJson(exchange, 409, map("error", "Você já avaliou este grupo."));
                            return;
                        }
                    }
                }
                // Etapa 6, tarefa 5: o tr_swipe_cria_match cria o Match no like
                // mutuo. Comparar o match do par antes e depois do INSERT, na
                // mesma transacao, mostra se foi este swipe que o criou.
                Integer matchBefore = PROCEDIMENTOS_TRIGGERS.matchDoPar(connection, user, target);
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO Swipe (tipo_decisao, id_usuario_realizou, id_usuario_encontrado) VALUES (?, ?, ?)")) {
                    insert.setString(1, decision);
                    insert.setInt(2, user);
                    insert.setInt(3, target);
                    insert.executeUpdate();
                }
                Integer matchAfter = PROCEDIMENTOS_TRIGGERS.matchDoPar(connection, user, target);
                connection.commit();
                boolean matched = matchBefore == null && matchAfter != null;
                sendJson(exchange, 201, map("ok", true, "matched", matched, "matchId", matched ? matchAfter : null));
            } catch (SQLException error) {
                connection.rollback();
                throw error;
            }
        } catch (SQLException error) {
            swipeError(exchange, error);
        }
    }

    /** Erros de integridade do swipe viram mensagem clara, sem a pilha. */
    private static void swipeError(HttpExchange exchange, SQLException error) throws IOException {
        if ("23000".equals(error.getSQLState())) {
            sendJson(exchange, 409, map("error", "Não foi possível registrar o swipe: usuário inexistente ou avaliação repetida."));
        } else if (error.getErrorCode() == 3819) {
            sendJson(exchange, 400, map("error", "Decisão de swipe inválida."));
        } else {
            System.err.println("Falha ao registrar swipe: " + error.getMessage());
            sendJson(exchange, 503, map("error", "Não foi possível registrar o swipe."));
        }
    }

    // ------------------------------------------------------------ Etapa 6

    /**
     * Botao "Converter em participacao" (Etapa 5, pessoa 4).
     * Chama sp_converter_match_em_participacao. Quando o procedimento recusa
     * (SIGNAL SQLSTATE '45000'), a mensagem dele e devolvida para a tela.
     * O trigger tr_match_log_situacao (pessoa 2) grava o log sozinho.
     */
    private static void convertMatch(HttpExchange exchange) throws IOException {
        Map<String, String> form = postForm(exchange);
        if (form == null) return;
        int user = integer(form.get("user"), 0);
        int match = integer(form.get("match"), 0);
        String role = form.getOrDefault("role", "").trim();
        if (user <= 0 || match <= 0) {
            sendJson(exchange, 400, map("error", "Match inválido."));
            return;
        }
        if (role.isEmpty() || role.length() > 100) {
            sendJson(exchange, 400, map("error", "Informe a sua função no grupo (até 100 caracteres)."));
            return;
        }
        try (Connection connection = ConnectionFactory.getConnection()) {
            // So converte um match do musico selecionado na tela.
            try (PreparedStatement own = connection.prepareStatement(
                    "SELECT 1 FROM " + MATCH_TABLE + " WHERE id_match = ? AND (id_usuario_a = ? OR id_usuario_b = ?)")) {
                own.setInt(1, match); own.setInt(2, user); own.setInt(3, user);
                try (ResultSet r = own.executeQuery()) {
                    if (!r.next()) {
                        sendJson(exchange, 404, map("error", "Este match não pertence ao músico selecionado."));
                        return;
                    }
                }
            }
            try (CallableStatement call = connection.prepareCall("{CALL sp_converter_match_em_participacao(?, ?)}")) {
                call.setInt(1, match);
                call.setString(2, role);
                call.execute();
            }
            sendJson(exchange, 200, map("ok", true, "message", "Match convertido em participação no grupo."));
        } catch (SQLException error) {
            routineError(exchange, error, "converter o match");
        }
    }

    /**
     * Botao "Sincronizar grupos" (Etapa 5, pessoa 5).
     * Chama sp_sincronizar_situacao_grupos e devolve os dois contadores OUT.
     */
    private static void syncGroups(HttpExchange exchange) throws IOException {
        if (postForm(exchange) == null) return;
        try (Connection connection = ConnectionFactory.getConnection();
             CallableStatement call = connection.prepareCall("{CALL sp_sincronizar_situacao_grupos(?, ?)}")) {
            call.registerOutParameter(1, Types.INTEGER);
            call.registerOutParameter(2, Types.INTEGER);
            call.execute();
            sendJson(exchange, 200, map("groups", call.getInt(1), "vacancies", call.getInt(2)));
        } catch (SQLException error) {
            routineError(exchange, error, "sincronizar os grupos");
        }
    }

    /**
     * Vagas recomendadas (Etapa 6, tarefa 4): view vw_recomendacao_vagas com
     * a compatibilidade de fn_compatibilidade_vaga e o indicador da tela.
     * Parametros: user (obrigatorio), funcao e nivel (filtros opcionais).
     */
    private static void recommendedVacancies(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, map("error", "Método não permitido."));
            return;
        }
        Map<String, String> query;
        try {
            query = form(exchange.getRequestURI().getRawQuery());
        } catch (IllegalArgumentException error) {
            sendJson(exchange, 400, map("error", "Filtro inválido."));
            return;
        }
        int user = integer(query.get("user"), 0);
        String role = blankToNull(query.get("funcao"));
        String level = blankToNull(query.get("nivel"));
        if (user <= 0) {
            sendJson(exchange, 400, map("error", "Escolha um músico."));
            return;
        }
        if (role != null && role.length() > 100) {
            sendJson(exchange, 400, map("error", "A função pode ter até 100 caracteres."));
            return;
        }
        if (level != null && !NIVEIS.contains(level)) {
            sendJson(exchange, 400, map("error", "Nível mínimo inválido."));
            return;
        }
        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (ViewsFuncoesDAO.VagaRecomendada v : VIEWS_FUNCOES.vagasRecomendadas(user, role, level)) {
                rows.add(map("id", v.idVaga(), "groupId", v.idGrupo(), "group", v.grupo(),
                        "city", v.cidade(), "title", v.funcaoInstrumento(), "level", v.nivelMinimo(),
                        "genres", v.generosEmComum(), "sharedGenres", v.qtdGenerosEmComum(),
                        "compatibility", v.compatibilidade()));
            }
            ViewsFuncoesDAO.IndicadorCompatibilidade indicator = VIEWS_FUNCOES.indicadorCompatibilidade(user);
            sendJson(exchange, 200, map("rows", rows, "indicator", map(
                    "open", indicator.vagasAbertas(),
                    "compatible", indicator.compativeis(),
                    "recommended", indicator.recomendadas())));
        } catch (SQLException error) {
            objectError(exchange, error, "carregar as vagas recomendadas");
        }
    }

    /** Integrantes ativos de um grupo (view vw_integrantes_grupo, Etapa 4). */
    private static void groupMembers(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, map("error", "Método não permitido."));
            return;
        }
        int group;
        try {
            group = integer(form(exchange.getRequestURI().getRawQuery()).get("group"), 0);
        } catch (IllegalArgumentException error) {
            group = 0;
        }
        if (group <= 0) {
            sendJson(exchange, 400, map("error", "Grupo inválido."));
            return;
        }
        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (ViewsFuncoesDAO.Integrante i : VIEWS_FUNCOES.integrantesDoGrupo(group)) {
                rows.add(map("id", i.idMusico(), "name", i.musico(), "role", i.funcao(), "since", i.dataEntrada()));
            }
            sendJson(exchange, 200, map("rows", rows));
        } catch (SQLException error) {
            objectError(exchange, error, "carregar os integrantes");
        }
    }

    /**
     * Historico de mudancas de situacao de match com filtros (Etapa 6,
     * tarefa 5): efeito do trigger tr_match_log_situacao, so dos matches
     * do usuario. Parametros: user (obrigatorio), match (id) e situacao
     * (nova), opcionais.
     */
    private static void matchLogFiltered(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, map("error", "Método não permitido."));
            return;
        }
        Map<String, String> query;
        try {
            query = form(exchange.getRequestURI().getRawQuery());
        } catch (IllegalArgumentException error) {
            sendJson(exchange, 400, map("error", "Filtro inválido."));
            return;
        }
        int user = integer(query.get("user"), 0);
        String matchText = blankToNull(query.get("match"));
        String status = blankToNull(query.get("situacao"));
        Integer match = matchText == null ? null : integer(matchText, 0);
        if (user <= 0) {
            sendJson(exchange, 400, map("error", "Escolha um músico."));
            return;
        }
        if (match != null && match <= 0) {
            sendJson(exchange, 400, map("error", "Conexão inválida."));
            return;
        }
        if (status != null && !SITUACOES_MATCH.contains(status)) {
            sendJson(exchange, 400, map("error", "Situação inválida."));
            return;
        }
        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (ProcedimentosTriggersDAO.LinhaLog l : PROCEDIMENTOS_TRIGGERS.logFiltrado(user, match, status)) {
                rows.add(map("match", l.idMatch(), "from", l.situacaoAnterior(), "to", l.situacaoNova(),
                        "when", l.quando()));
            }
            sendJson(exchange, 200, map("rows", rows));
        } catch (SQLException error) {
            objectError(exchange, error, "carregar o histórico");
        }
    }

    // ------------------------------------------------------------ Painel (dashboard na web)

    /**
     * Dashboard da Etapa 6 na interface web: o mesmo DashboardDAO do Swing
     * (mesmos SELECTs), devolvido em JSON. Parametros opcionais: inicio e fim
     * (AAAA-MM-DD), categoria (grafico 1), habilidade (2), situacao (5) e
     * instrumento (6, id). Sem filtro = tudo.
     */
    private static void dashboard(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, map("error", "Método não permitido."));
            return;
        }
        Map<String, String> query;
        LocalDate inicio, fim;
        Integer instrumento;
        try {
            query = form(exchange.getRequestURI().getRawQuery());
            inicio = dataOuNulo(query.get("inicio"));
            fim = dataOuNulo(query.get("fim"));
            instrumento = blankToNull(query.get("instrumento")) == null ? null : Integer.valueOf(query.get("instrumento").trim());
        } catch (IllegalArgumentException | DateTimeParseException error) {
            sendJson(exchange, 400, map("error", "Filtro inválido. Use datas no formato AAAA-MM-DD."));
            return;
        }
        if (inicio != null && fim != null && fim.isBefore(inicio)) {
            sendJson(exchange, 400, map("error", "A data final deve ser igual ou posterior à inicial."));
            return;
        }
        String categoria = textoCurto(query.get("categoria"));
        String habilidade = textoCurto(query.get("habilidade"));
        String situacao = textoCurto(query.get("situacao"));
        try {
            DashboardDAO.Painel painel = DASHBOARD.painel(inicio, fim);
            Map<String, Object> indicadores = map(
                    "musicos", painel.musicos(), "grupos", painel.grupos(), "vagasAbertas", painel.vagasAbertas(),
                    "matchesNoPeriodo", painel.matchesNoPeriodo(), "matchesAtivos", painel.matchesAtivos(),
                    "matchesConvertidos", painel.matchesConvertidos(),
                    "percentualConvertidos", painel.percentualConvertidos(),
                    "mediaMensagens", painel.mediaMensagensPorMatch(),
                    "taxaMediaRetribuicao", painel.taxaMediaRetribuicao());

            List<Map<String, Object>> niveis = new ArrayList<>();
            for (Object[] linha : DASHBOARD.niveisPorCategoria(categoria).linhas()) {
                niveis.add(map("nivel", linha[0], "qtd", linha[1]));
            }
            List<Map<String, Object>> instrumentos = new ArrayList<>();
            for (Object[] linha : DASHBOARD.instrumentosPorHabilidade(habilidade).linhas()) {
                if (((Number) linha[1]).intValue() == 0) continue;
                instrumentos.add(map("instrumento", linha[0], "musicos", linha[1], "mediaAnos", linha[2]));
            }
            List<Map<String, Object>> decisoes = new ArrayList<>();
            for (Object[] linha : DASHBOARD.decisoesPorPeriodo(inicio, fim).linhas()) {
                decisoes.add(map("decisao", linha[0], "qtd", linha[1]));
            }
            List<Map<String, Object>> semanas = new ArrayList<>();
            for (DashboardDAO.Semana semana : DASHBOARD.atividadeSemanal(inicio, fim)) {
                semanas.add(map("inicio", semana.inicio().toString(), "swipes", semana.swipes(), "matches", semana.matches()));
            }
            DashboardDAO.Distribuicao distribuicao = DASHBOARD.mensagensPorMatch(situacao, inicio, fim);
            List<Map<String, Object>> frequencias = new ArrayList<>();
            for (int[] faixa : distribuicao.frequencias()) frequencias.add(map("valor", faixa[0], "qtd", faixa[1]));
            List<Map<String, Object>> pontos = new ArrayList<>();
            for (DashboardDAO.PontoMusico ponto : DASHBOARD.instrumentosVersusExperiencia(instrumento)) {
                pontos.add(map("id", ponto.id(), "nome", ponto.nome(), "instrumentos", ponto.instrumentos(),
                        "mediaAnos", ponto.mediaAnos()));
            }
            List<Map<String, Object>> opcoesInstrumento = new ArrayList<>();
            for (DashboardDAO.Opcao opcao : DASHBOARD.instrumentos()) opcoesInstrumento.add(map("id", opcao.id(), "nome", opcao.nome()));

            sendJson(exchange, 200, map(
                    "indicadores", indicadores,
                    "niveis", niveis,
                    "instrumentos", instrumentos,
                    "decisoes", decisoes,
                    "semanas", semanas,
                    "mensagens", map("frequencias", frequencias, "n", distribuicao.n(), "media", distribuicao.media(),
                            "mediana", distribuicao.mediana(), "modas", distribuicao.modas(),
                            "variancia", distribuicao.variancia(), "desvioPadrao", distribuicao.desvioPadrao()),
                    "dispersao", map("pontos", pontos,
                            "pearson", DASHBOARD.pearsonInstrumentosExperiencia(instrumento)),
                    "filtros", map("categorias", DASHBOARD.categorias(), "habilidades", DASHBOARD.habilidades(),
                            "situacoes", DASHBOARD.situacoesMatch(), "instrumentos", opcoesInstrumento)));
        } catch (SQLException error) {
            objectError(exchange, error, "carregar o painel");
        }
    }

    /** As 12 consultas (Etapas 3 e 4): titulo, pergunta, parametro, destaque e SQL. */
    private static void listarConsultas(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, map("error", "Método não permitido."));
            return;
        }
        List<Map<String, Object>> consultas = new ArrayList<>();
        for (ConsultaSQL consulta : ConsultaSQL.TODAS) {
            consultas.add(map("numero", consulta.numero(), "origem", consulta.origem(), "titulo", consulta.titulo(),
                    "pergunta", consulta.pergunta(), "parametro", consulta.parametro().name(),
                    "rotulo", consulta.parametro().rotulo, "destaque", consulta.colunaDestaque(),
                    "sql", consulta.sql().strip()));
        }
        try {
            List<Map<String, Object>> instrumentos = new ArrayList<>();
            for (DashboardDAO.Opcao opcao : DASHBOARD.instrumentos()) instrumentos.add(map("id", opcao.id(), "nome", opcao.nome()));
            sendJson(exchange, 200, map("consultas", consultas, "opcoes", map(
                    "categorias", DASHBOARD.categorias(), "instrumentos", instrumentos,
                    "diagnosticos", ConsultaSQL.DIAGNOSTICOS)));
        } catch (SQLException error) {
            objectError(exchange, error, "carregar as consultas");
        }
    }

    /**
     * Executa uma consulta: numero (1 a 12) e valor do parametro, se houver.
     * O valor vai sempre por PreparedStatement (DashboardDAO.executar).
     */
    private static void executarConsulta(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, map("error", "Método não permitido."));
            return;
        }
        Map<String, String> query;
        try {
            query = form(exchange.getRequestURI().getRawQuery());
        } catch (IllegalArgumentException error) {
            sendJson(exchange, 400, map("error", "Parâmetro inválido."));
            return;
        }
        int numero = integer(query.get("numero"), 0);
        if (numero < 1 || numero > ConsultaSQL.TODAS.size()) {
            sendJson(exchange, 400, map("error", "Consulta inexistente."));
            return;
        }
        ConsultaSQL consulta = ConsultaSQL.numero(numero);
        String bruto = textoCurto(query.get("valor"));
        Object valor;
        switch (consulta.parametro()) {
            case MUSICO, INSTRUMENTO -> {
                if (bruto != null && integer(bruto, 0) <= 0) {
                    sendJson(exchange, 400, map("error", "Valor inválido para " + consulta.parametro().rotulo + "."));
                    return;
                }
                valor = bruto == null ? null : (Object) Long.valueOf(bruto);
            }
            case DIAGNOSTICO -> {
                if (bruto != null && !ConsultaSQL.DIAGNOSTICOS.contains(bruto)) {
                    sendJson(exchange, 400, map("error", "Diagnóstico inválido."));
                    return;
                }
                valor = bruto;
            }
            case CATEGORIA -> valor = bruto;
            default -> valor = null;
        }
        if (consulta.usaMusico() && valor == null) {
            sendJson(exchange, 400, map("error", "Escolha um músico."));
            return;
        }
        try {
            DashboardDAO.Resultado resultado = DASHBOARD.executar(consulta, valor);
            List<List<Object>> linhas = new ArrayList<>();
            for (Object[] linha : resultado.linhas()) {
                List<Object> celulas = new ArrayList<>();
                for (Object celula : linha) celulas.add(celulaJson(celula));
                linhas.add(celulas);
            }
            sendJson(exchange, 200, map("colunas", resultado.colunas(), "linhas", linhas));
        } catch (SQLException error) {
            objectError(exchange, error, "executar a consulta");
        }
    }

    /** Datas e horas do banco viram texto no padrao brasileiro; o resto passa como esta. */
    private static Object celulaJson(Object celula) {
        if (celula instanceof java.sql.Timestamp t) return t.toLocalDateTime().format(DATA_HORA_BR);
        if (celula instanceof java.time.LocalDateTime t) return t.format(DATA_HORA_BR);
        if (celula instanceof java.sql.Date d) return d.toLocalDate().format(DATA_BR);
        if (celula instanceof LocalDate d) return d.format(DATA_BR);
        if (celula instanceof Number || celula instanceof Boolean || celula == null) return celula;
        return String.valueOf(celula);
    }

    private static LocalDate dataOuNulo(String texto) {
        return blankToNull(texto) == null ? null : LocalDate.parse(texto.trim());
    }

    /** Texto de filtro: vazio vira null (sem filtro) e mais de 100 caracteres e cortado. */
    private static String textoCurto(String texto) {
        String valor = blankToNull(texto);
        return valor == null ? null : valor.substring(0, Math.min(valor.length(), 100));
    }

    /** Traduz a falta de view (1146) ou funcao (1305) em aviso para a secao. */
    private static void objectError(HttpExchange exchange, SQLException error, String action) throws IOException {
        if (error.getErrorCode() == 1146 || error.getErrorCode() == 1305) {
            sendJson(exchange, 501, map("error", "Este recurso está indisponível no banco no momento."));
        } else if (error.getErrorCode() == 1370) {
            sendJson(exchange, 403, map("error", "O usuário do banco não tem permissão para esta ação."));
        } else {
            System.err.println("Falha ao " + action + ": " + error.getMessage());
            sendJson(exchange, 503, map("error", "Não foi possível " + action + "."));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Ultimas mudancas de situacao de match (tabela Log_Match, Etapa 5,
     * pessoa 2). Devolve null se a tabela ainda nao existir no banco.
     */
    private static List<Map<String, Object>> matchLog(Connection c) throws SQLException {
        try (PreparedStatement available = c.prepareStatement(
                "SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Log_Match'");
             ResultSet r = available.executeQuery()) {
            if (!r.next()) return null;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        String sql = """
            SELECT id_match, situacao_anterior, situacao_nova,
                   DATE_FORMAT(data_hora, '%d/%m/%Y %H:%i'), usuario_banco
              FROM Log_Match
             ORDER BY data_hora DESC, id_log DESC
             LIMIT 10
            """;
        try (PreparedStatement s = c.prepareStatement(sql); ResultSet r = s.executeQuery()) {
            while (r.next()) rows.add(map("match", r.getObject(1), "from", r.getString(2),
                    "to", r.getString(3), "when", r.getString(4), "who", r.getString(5)));
        }
        return rows;
    }

    /** Le um formulario POST pequeno, com a mesma protecao de origem do swipe. */
    private static Map<String, String> postForm(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, map("error", "Método não permitido."));
            return null;
        }
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && !origin.equals("http://127.0.0.1:" + PORT)) {
            sendJson(exchange, 403, map("error", "Origem não permitida."));
            return null;
        }
        byte[] rawBody = exchange.getRequestBody().readNBytes(2049);
        if (rawBody.length > 2048) {
            sendJson(exchange, 413, map("error", "Requisição muito grande."));
            return null;
        }
        try {
            return form(new String(rawBody, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException error) {
            sendJson(exchange, 400, map("error", "Formulário inválido."));
            return null;
        }
    }

    /** Traduz os erros das rotinas da Etapa 5 em mensagens para a tela. */
    private static void routineError(HttpExchange exchange, SQLException error, String action) throws IOException {
        if ("45000".equals(error.getSQLState())) {
            sendJson(exchange, 400, map("error", error.getMessage()));
        } else if (error.getErrorCode() == 1305) {
            sendJson(exchange, 501, map("error", "Esta ação está indisponível no banco no momento."));
        } else if (error.getErrorCode() == 1370) {
            sendJson(exchange, 403, map("error", "O usuário do banco não tem permissão para esta ação."));
        } else {
            System.err.println("Falha ao " + action + ": " + error.getMessage());
            sendJson(exchange, 503, map("error", "Não foi possível " + action + "."));
        }
    }

    private static boolean exists(Connection c, String sql, int id) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setInt(1, id);
            try (ResultSet r = s.executeQuery()) { return r.next(); }
        }
    }

    private static List<Map<String, Object>> musicians(Connection c) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        String sql = "SELECT m.id_usuario, m.nome, m.nome_artistico, m.nivel_experiencia, " +
                "m.categoria_principal, m.biografia FROM Musico m ORDER BY m.id_usuario";
        try (PreparedStatement s = c.prepareStatement(sql); ResultSet r = s.executeQuery()) {
            while (r.next()) rows.add(map(
                    "id", r.getInt(1), "name", r.getString(2), "artist", r.getString(3),
                    "level", r.getString(4), "category", r.getString(5), "bio", r.getString(6)));
        }
        return rows;
    }

    private static List<Map<String, Object>> groups(Connection c, int user) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        String sql = """
            SELECT g.id_usuario, g.nome_grupo, g.cidade_atuacao, g.descricao, g.situacao,
                   CASE WHEN b.id_grupo_musical IS NOT NULL THEN 'Banda'
                        WHEN o.id_grupo_musical IS NOT NULL THEN 'Orquestra'
                        WHEN co.id_grupo_musical IS NOT NULL THEN 'Coral'
                        ELSE 'Grupo' END AS tipo,
                   (SELECT GROUP_CONCAT(gm.nome ORDER BY gm.nome SEPARATOR ', ')
                      FROM Grupo_Genero gg JOIN Genero_Musical gm ON gm.id_genero_musical = gg.id_genero_musical
                     WHERE gg.id_grupo_musical = g.id_usuario) AS generos,
                   (SELECT COUNT(*) FROM Vaga v
                     WHERE v.id_grupo_musical = g.id_usuario AND v.situacao = 'Aberta') AS vagas,
                   EXISTS (SELECT 1 FROM Swipe sw
                            WHERE sw.id_usuario_realizou = ? AND sw.id_usuario_encontrado = g.id_usuario) AS avaliado
              FROM Grupo_Musical g
              LEFT JOIN Banda b ON b.id_grupo_musical = g.id_usuario
              LEFT JOIN Orquestra o ON o.id_grupo_musical = g.id_usuario
              LEFT JOIN Coral co ON co.id_grupo_musical = g.id_usuario
             ORDER BY vagas DESC, g.nome_grupo
            """;
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setInt(1, user);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) rows.add(map(
                        "id", r.getInt(1), "name", r.getString(2), "city", r.getString(3),
                        "description", r.getString(4), "status", r.getString(5),
                        "type", r.getString(6), "genres", r.getString(7),
                        "openVacancies", r.getInt(8), "swiped", r.getBoolean(9)));
            }
        }
        return rows;
    }

    /**
     * Vagas abertas com a compatibilidade do musico selecionado (Etapa 5,
     * funcao fn_compatibilidade_vaga). O "?" recebe o id do musico.
     * Sem a funcao instalada, a lista sai sem a coluna de compatibilidade.
     */
    private static List<Map<String, Object>> vacancies(Connection c, int user, boolean withCompatibility)
            throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        String sql = """
            SELECT v.id_vaga, v.funcao_instrumento, v.id_grupo_musical,
                   g.nome_grupo, g.cidade_atuacao, v.nivel_minimo, v.descricao,
                   %s AS compatibilidade
              FROM Vaga v JOIN Grupo_Musical g ON g.id_usuario = v.id_grupo_musical
             WHERE v.situacao = 'Aberta'
             ORDER BY v.id_vaga
            """.formatted(withCompatibility ? "fn_compatibilidade_vaga(?, v.id_vaga)" : "NULL");
        try (PreparedStatement s = c.prepareStatement(sql)) {
            if (withCompatibility) s.setInt(1, user);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) rows.add(map(
                        "id", r.getInt(1), "title", r.getString(2), "groupId", r.getInt(3),
                        "group", r.getString(4), "city", r.getString(5),
                        "level", r.getString(6), "description", r.getString(7),
                        "compatibility", r.getString(8)));
            }
        }
        return rows;
    }

    private static List<Map<String, Object>> matches(Connection c, int user) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        String sql = "SELECT ma.id_match, ma.situacao, DATE_FORMAT(ma.data_criacao, '%d/%m/%Y'), " +
                "CASE WHEN ma.id_usuario_a = ? THEN ma.id_usuario_b ELSE ma.id_usuario_a END AS outro, " +
                "COALESCE(g.nome_grupo, mu.nome_artistico, mu.nome, u.nome_usuario) AS nome, " +
                "g.id_usuario IS NOT NULL AS outro_e_grupo " +
                "FROM " + MATCH_TABLE + " ma " +
                "JOIN Usuario u ON u.id_usuario = CASE WHEN ma.id_usuario_a = ? THEN ma.id_usuario_b ELSE ma.id_usuario_a END " +
                "LEFT JOIN Grupo_Musical g ON g.id_usuario = u.id_usuario " +
                "LEFT JOIN Musico mu ON mu.id_usuario = u.id_usuario " +
                "WHERE ma.id_usuario_a = ? OR ma.id_usuario_b = ? ORDER BY ma.data_criacao DESC";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setInt(1, user); s.setInt(2, user); s.setInt(3, user); s.setInt(4, user);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) rows.add(map("id", r.getInt(1), "status", r.getString(2),
                        "created", r.getString(3), "otherId", r.getInt(4), "name", r.getString(5),
                        "otherIsGroup", r.getBoolean(6)));
            }
        }
        return rows;
    }

    private static Map<String, Object> stats(Connection c, int user, boolean withRateFunction) throws SQLException {
        int groupCount, vacancyCount, matchCount;
        try (PreparedStatement s = c.prepareStatement(
                "SELECT (SELECT COUNT(*) FROM Grupo_Musical), " +
                "(SELECT COUNT(*) FROM Vaga WHERE situacao = 'Aberta'), " +
                "(SELECT COUNT(*) FROM " + MATCH_TABLE + " WHERE id_usuario_a = ? OR id_usuario_b = ?)")) {
            s.setInt(1, user); s.setInt(2, user);
            try (ResultSet r = s.executeQuery()) {
                r.next(); groupCount = r.getInt(1); vacancyCount = r.getInt(2); matchCount = r.getInt(3);
            }
        }
        // A regra da taxa fica so na funcao da Etapa 5 (sem copia em Java).
        // Sem a funcao instalada, rate vai null e a tela avisa.
        Double rate = withRateFunction ? VIEWS_FUNCOES.taxaRetribuicao(user) : null;
        return map("groups", groupCount, "vacancies", vacancyCount, "matches", matchCount, "rate", rate);
    }

    private static void staticFile(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1); exchange.close(); return;
        }
        String path = exchange.getRequestURI().getPath();
        Path file = WEB_ROOT.resolve(path.equals("/") ? "index.html" : path.substring(1)).normalize();
        if (!file.startsWith(WEB_ROOT) || !Files.isRegularFile(file)) {
            exchange.sendResponseHeaders(404, -1); exchange.close(); return;
        }
        String type = path.endsWith(".css") ? "text/css" : path.endsWith(".js") ? "text/javascript" : "text/html";
        byte[] body = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, body.length);
        try (var out = exchange.getResponseBody()) { out.write(body); }
    }

    private static Map<String, String> form(String encoded) {
        Map<String, String> values = new LinkedHashMap<>();
        if (encoded == null || encoded.isEmpty()) return values;
        for (String pair : encoded.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(parts.length == 2 ? parts[1] : "", StandardCharsets.UTF_8);
            values.put(key, value);
        }
        return values;
    }

    private static int integer(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (NumberFormatException error) { return fallback; }
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }

    private static void sendJson(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] body = json(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (var out = exchange.getResponseBody()) { out.write(body); }
    }

    private static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Double d && (d.isNaN() || d.isInfinite())) return "null";
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        if (value instanceof Map<?, ?> map) {
            List<String> parts = new ArrayList<>();
            map.forEach((key, item) -> parts.add(json(String.valueOf(key)) + ":" + json(item)));
            return "{" + String.join(",", parts) + "}";
        }
        if (value instanceof List<?> list) {
            List<String> parts = new ArrayList<>();
            list.forEach(item -> parts.add(json(item)));
            return "[" + String.join(",", parts) + "]";
        }
        String text = String.valueOf(value);
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> { if (ch < 0x20) result.append(String.format("\\u%04x", (int) ch)); else result.append(ch); }
            }
        }
        return result.append('"').toString();
    }
}
