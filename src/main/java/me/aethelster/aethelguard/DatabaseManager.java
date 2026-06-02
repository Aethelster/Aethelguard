package me.aethelster.aethelguard;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final Aethelguard plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(Aethelguard plugin) {
        this.plugin = plugin;
    }

    public void connect(String host, int port, String database, String username, String password) {
        try {
            HikariConfig config = new HikariConfig();

            boolean useSsl = plugin.getConfig().getBoolean("database.use-ssl", false);
            boolean allowPublicKeyRetrieval = plugin.getConfig().getBoolean("database.allow-public-key-retrieval", true);
            String serverTimezone = plugin.getConfig().getString("database.server-timezone", "UTC");

            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=" + useSsl
                    + "&allowPublicKeyRetrieval=" + allowPublicKeyRetrieval
                    + "&serverTimezone=" + serverTimezone);
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");


            config.setMaximumPoolSize(plugin.getConfig().getInt("database.pool.maximum-size", 10));
            config.setMinimumIdle(plugin.getConfig().getInt("database.pool.minimum-idle", 2));
            config.setConnectionTimeout(plugin.getConfig().getLong("database.pool.connection-timeout-ms", 10000L));
            config.setIdleTimeout(plugin.getConfig().getLong("database.pool.idle-timeout-ms", 600000L));
            config.setMaxLifetime(plugin.getConfig().getLong("database.pool.max-lifetime-ms", 1800000L));

            this.dataSource = new HikariDataSource(config);

            plugin.logInfo("§aVeri tabanı bağlantısı başarıyla kuruldu!", "§aDatabase connection established successfully!");

            createTables();

        } catch (Exception e) {
            plugin.logWarning("Veri tabanına bağlanırken hata oluştu! Ayarları ve şifreyi kontrol edin.",
                    "An error occurred while connecting to the database! Check settings and password.");
            e.printStackTrace();
        }
    }

    private void createTables() {
        String authTable = "CREATE TABLE IF NOT EXISTS " + plugin.getAuthTableName() + " (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "uuid VARCHAR(36) NOT NULL UNIQUE," +
                "username VARCHAR(16) NOT NULL," +
                "password VARCHAR(255) NOT NULL," +
                "auth_type VARCHAR(20) DEFAULT 'TEXT'," +
                "pin_code VARCHAR(6) DEFAULT NULL," +
                "totp_secret VARCHAR(32) DEFAULT NULL," +
                "last_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ");";

        try (Connection conn = getConnection()) {
            if (conn == null) return;

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(authTable);
                plugin.logInfo("§aVeri tabanı tabloları kontrol edildi ve hazırlandı.", "§aDatabase tables checked and verified.");
            }
        } catch (SQLException e) {
            plugin.logWarning("Tablolar oluşturulurken SQL hatası çıktı!", "SQL error occurred while creating tables!");
            e.printStackTrace();
        }
    }

    public Connection getConnection() throws SQLException {

        if (dataSource == null) {
            return null;
        }


        if (dataSource.isClosed()) {
            throw new SQLException("Veri tabanı bağlantı havuzu kapalı!");
        }

        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.logInfo("§cVeri tabanı bağlantı havuzu güvenli bir şekilde kapatıldı.", "§cDatabase connection pool closed safely.");
        }
    }
}
