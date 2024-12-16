import java.sql.*;

public class SQLiteManager {
    private static final String DATABASE_URL = "jdbc:sqlite:game_history.db";

    public static void main(String[] args) {
        SQLiteManager dbManager = new SQLiteManager();

        // Test database operations
        dbManager.connect();
        dbManager.createTable();
        dbManager.printGameHistory();
        dbManager.disconnect();
    }

    private Connection connection;

    // Connect to the database
    public void connect() {
        try {
            connection = DriverManager.getConnection(DATABASE_URL);
            System.out.println("Connection to SQLite has been established.");
        } catch (SQLException e) {
            System.err.println("Error connecting to SQLite: " + e.getMessage());
        }
    }

    // Create the game history table if not exist
    public void createTable() {
        String createTableSQL = """
            
                CREATE TABLE IF NOT EXISTS game_history (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  player_name TEXT NOT NULL,
                  score INTEGER,
                  played_at TEXT
              );
            """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL);
            System.out.println("Table 'game_history' is ready.");
        } catch (SQLException e) {
            System.err.println("Error creating table: " + e.getMessage());
        }
    }

    // Print all game records
    public void printGameHistory() {
        String selectSQL = "SELECT * FROM game_history";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(selectSQL)) {

            System.out.println("Game History:");
            while (rs.next()) {
                int id = rs.getInt("id");
                String playerName = rs.getString("player_name");
                int score = rs.getInt("score");
                String playedAt = rs.getString("played_at");
                System.out.printf("ID: %d, Player: %s, Score: %d, Played At: %s%n", id, playerName, score, playedAt);
            }
        } catch (SQLException e) {
            System.err.println("Error reading records: " + e.getMessage());
        }
    }

    // Disconnect from the database
    public void disconnect() {
        try {
            if (connection != null) {
                connection.close();
                System.out.println("Disconnected from SQLite.");
            }
        } catch (SQLException e) {
            System.err.println("Error disconnecting: " + e.getMessage());
        }
    }
}
