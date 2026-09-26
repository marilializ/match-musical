import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class ConnectionFactory {
    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/match_musical";

    private ConnectionFactory() {
    }

    public static Connection getConnection() throws SQLException {
        String url = System.getenv().getOrDefault("SYNC_DB_URL", DEFAULT_URL);
        String user = System.getenv().getOrDefault("SYNC_DB_USER", "sync_app");
        String password = System.getenv().getOrDefault("SYNC_DB_PASSWORD", "");
        return DriverManager.getConnection(url, user, password);
    }
}
