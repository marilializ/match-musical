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

/**
 * Interface web local do SYNC. Escuta apenas em 127.0.0.1.
 * A selecao do musico e demonstrativa; nao substitui autenticacao.
 */
public final class WebMain {
    private static final String MATCH_TABLE = (char) 96 + "Match" + (char) 96;
    private static final Path WEB_ROOT = Path.of(System.getProperty("sync.web.dir", "web")).toAbsolutePath().normalize();
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("SYNC_WEB_PORT", "8080"));

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
        server.createContext("/", WebMain::staticFile);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("SYNC Web: http://127.0.0.1:" + PORT);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
    }

    private static void bootstrap(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, map("error", "Metodo nao permitido."));
            return;
        }
        try (Connection connection = ConnectionFactory.getConnection()) {
            List<Map<String, Object>> musicians = musicians(connection);
            int requested = integer(form(exchange.getRequestURI().getRawQuery()).get("user"), 0);
            int user = musicians.stream().anyMatch(m -> ((Number) m.get("id")).intValue() == requested)
                    ? requested : musicians.isEmpty() ? 0 : ((Number) musicians.get(0).get("id")).intValue();
            Map<String, Object> body = map(
                    "musicians", musicians,
                    "groups", groups(connection, user),
                    "vacancies", vacancies(connection, user),
                    "matches", matches(connection, user),
                    "stats", stats(connection, user),
                    "matchLog", matchLog(connection)
            );
            sendJson(exchange, 200, body);
        } catch (SQLException error) {
            System.err.println("Falha ao carregar interface web: " + error.getMessage());
            sendJson(exchange, 503, map("error", "Banco de dados indisponivel. Confira a conexao SYNC_DB_* e o seed."));
        }
    }

    private static void swipe(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, map("error", "Metodo nao permitido."));
            return;
        }
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && !origin.equals("http://127.0.0.1:" + PORT)) {
            sendJson(exchange, 403, map("error", "Origem nao permitida."));
            return;
        }
        byte[] rawBody = exchange.getRequestBody().readNBytes(2049);
        if (rawBody.length > 2048) {
            sendJson(exchange, 413, map("error", "Requisicao muito grande."));
            return;
        }
        Map<String, String> form;
        try {
            form = form(new String(rawBody, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException error) {
            sendJson(exchange, 400, map("error", "Formulario invalido."));
            return;
        }
        int user = integer(form.get("user"), 0);
        int target = integer(form.get("target"), 0);
        String decision = form.getOrDefault("decision", "");
        if (user <= 0 || target <= 0 || user == target ||
                !(decision.equals("Like") || decision.equals("Super Like") || decision.equals("Dislike"))) {
            sendJson(exchange, 400, map("error", "Swipe invalido."));
            return;
        }
        try (Connection connection = ConnectionFactory.getConnection()) {
            connection.setAutoCommit(false);
            try {
                // Trava a linha do musico para impedir dois swipes simultaneos na interface local.
                if (!exists(connection, "SELECT 1 FROM Musico WHERE id_usuario = ? FOR UPDATE", user) ||
                    !exists(connection, "SELECT 1 FROM Grupo_Musical WHERE id_usuario = ? AND situacao = 'Ativo'", target)) {
                    connection.rollback();
                    sendJson(exchange, 404, map("error", "Musico nao encontrado ou grupo inativo."));
                    return;
                }
                try (PreparedStatement check = connection.prepareStatement(
                        "SELECT 1 FROM Swipe WHERE id_usuario_realizou = ? AND id_usuario_encontrado = ? LIMIT 1")) {
                    check.setInt(1, user);
                    check.setInt(2, target);
                    try (ResultSet rows = check.executeQuery()) {
                        if (rows.next()) {
                            connection.rollback();
                            sendJson(exchange, 409, map("error", "Voce ja avaliou este grupo."));
                            return;
                        }
                    }
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO Swipe (tipo_decisao, id_usuario_realizou, id_usuario_encontrado) VALUES (?, ?, ?)")) {
                    insert.setString(1, decision);
                    insert.setInt(2, user);
                    insert.setInt(3, target);
                    insert.executeUpdate();
                }
                connection.commit();
                sendJson(exchange, 201, map("ok", true));
            } catch (SQLException error) {
                connection.rollback();
                throw error;
            }
        } catch (SQLException error) {
            System.err.println("Falha ao registrar swipe: " + error.getMessage());
            sendJson(exchange, 503, map("error", "Nao foi possivel registrar o swipe."));
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
            sendJson(exchange, 400, map("error", "Match invalido."));
            return;
        }
        if (role.isEmpty() || role.length() > 100) {
            sendJson(exchange, 400, map("error", "Informe a sua funcao no grupo (ate 100 caracteres)."));
            return;
        }
        try (Connection connection = ConnectionFactory.getConnection()) {
            // So converte um match do musico selecionado na tela.
            try (PreparedStatement own = connection.prepareStatement(
                    "SELECT 1 FROM " + MATCH_TABLE + " WHERE id_match = ? AND (id_usuario_a = ? OR id_usuario_b = ?)")) {
                own.setInt(1, match); own.setInt(2, user); own.setInt(3, user);
                try (ResultSet r = own.executeQuery()) {
                    if (!r.next()) {
                        sendJson(exchange, 404, map("error", "Este match nao pertence ao musico selecionado."));
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
            sendJson(exchange, 405, map("error", "Metodo nao permitido."));
            return null;
        }
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && !origin.equals("http://127.0.0.1:" + PORT)) {
            sendJson(exchange, 403, map("error", "Origem nao permitida."));
            return null;
        }
        byte[] rawBody = exchange.getRequestBody().readNBytes(2049);
        if (rawBody.length > 2048) {
            sendJson(exchange, 413, map("error", "Requisicao muito grande."));
            return null;
        }
        try {
            return form(new String(rawBody, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException error) {
            sendJson(exchange, 400, map("error", "Formulario invalido."));
            return null;
        }
    }

    /** Traduz os erros das rotinas da Etapa 5 em mensagens para a tela. */
    private static void routineError(HttpExchange exchange, SQLException error, String action) throws IOException {
        if ("45000".equals(error.getSQLState())) {
            sendJson(exchange, 400, map("error", error.getMessage()));
        } else if (error.getErrorCode() == 1305) {
            sendJson(exchange, 501, map("error", "O procedimento da Etapa 5 nao esta instalado no banco."));
        } else if (error.getErrorCode() == 1370) {
            sendJson(exchange, 403, map("error", "O usuario do banco nao tem permissao EXECUTE (GRANT EXECUTE ON match_musical.*)."));
        } else {
            System.err.println("Falha ao " + action + ": " + error.getMessage());
            sendJson(exchange, 503, map("error", "Nao foi possivel " + action + "."));
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
     */
    private static List<Map<String, Object>> vacancies(Connection c, int user) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        String sql = """
            SELECT v.id_vaga, v.funcao_instrumento, v.id_grupo_musical,
                   g.nome_grupo, g.cidade_atuacao, v.nivel_minimo, v.descricao,
                   fn_compatibilidade_vaga(?, v.id_vaga) AS compatibilidade
              FROM Vaga v JOIN Grupo_Musical g ON g.id_usuario = v.id_grupo_musical
             WHERE v.situacao = 'Aberta'
             ORDER BY v.id_vaga
            """;
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setInt(1, user);
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

    private static Map<String, Object> stats(Connection c, int user) throws SQLException {
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
        double rate = 0;
        try (PreparedStatement s = c.prepareStatement(
                "SELECT COUNT(*), COALESCE(SUM(CASE WHEN sw.id_usuario_encontrado <> sw.id_usuario_realizou " +
                "AND EXISTS (SELECT 1 FROM Swipe reply WHERE reply.id_usuario_realizou = sw.id_usuario_encontrado " +
                "AND reply.id_usuario_encontrado = sw.id_usuario_realizou " +
                "AND reply.tipo_decisao IN ('Like', 'Super Like')) THEN 1 ELSE 0 END), 0) " +
                "FROM Swipe sw WHERE sw.id_usuario_realizou = ? AND sw.tipo_decisao IN ('Like', 'Super Like')")) {
            s.setInt(1, user);
            try (ResultSet r = s.executeQuery()) {
                r.next(); long total = r.getLong(1); if (total > 0) rate = Math.round(10000.0 * r.getLong(2) / total) / 100.0;
            }
        }
        // Usa a funcao da Etapa 5 quando ela estiver instalada no banco.
        try (PreparedStatement available = c.prepareStatement(
                "SELECT 1 FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = DATABASE() " +
                "AND ROUTINE_TYPE = 'FUNCTION' AND ROUTINE_NAME = 'fn_taxa_retribuicao_usuario'")) {
            try (ResultSet routine = available.executeQuery()) {
                if (routine.next()) {
                    try (PreparedStatement function = c.prepareStatement("SELECT fn_taxa_retribuicao_usuario(?)")) {
                        function.setInt(1, user);
                        try (ResultSet result = function.executeQuery()) {
                            if (result.next()) rate = result.getDouble(1);
                        }
                    }
                }
            }
        }
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
