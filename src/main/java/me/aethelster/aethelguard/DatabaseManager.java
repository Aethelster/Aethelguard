package me.aethelster.aethelguard;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final AethelGuard plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(AethelGuard plugin) {
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
                "password_usable BOOLEAN DEFAULT TRUE," +
                "auth_mode VARCHAR(20) DEFAULT 'PASSWORD'," +
                "pin_hash VARCHAR(255) DEFAULT NULL," +
                "auth_type VARCHAR(20) DEFAULT 'TEXT'," +
                "pin_code VARCHAR(6) DEFAULT NULL," +
                "totp_secret VARCHAR(32) DEFAULT NULL," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "last_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "registration_ip VARCHAR(45) DEFAULT NULL," +
                "last_ip VARCHAR(45) DEFAULT NULL," +
                "last_world VARCHAR(64) DEFAULT NULL," +
                "last_x DOUBLE DEFAULT NULL," +
                "last_y DOUBLE DEFAULT NULL," +
                "last_z DOUBLE DEFAULT NULL," +
                "login_count INT DEFAULT 0," +
                "wrong_attempts_total INT DEFAULT 0," +
                "last_wrong_attempt VARCHAR(32) DEFAULT NULL," +
                "security_question_id VARCHAR(64) DEFAULT NULL," +
                "security_question_text VARCHAR(255) DEFAULT NULL," +
                "security_question_hash VARCHAR(255) DEFAULT NULL," +
                "recovery_method VARCHAR(32) DEFAULT 'question'," +
                "backup_code_hashes TEXT DEFAULT NULL," +
                "security_cooldowns TEXT DEFAULT NULL" +
                ");";

        try (Connection conn = getConnection()) {
            if (conn == null) return;

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(authTable);
                migrateAuthTable(stmt);
                plugin.logInfo("§aVeri tabanı tabloları kontrol edildi ve hazırlandı.", "§aDatabase tables checked and verified.");
            }
        } catch (SQLException e) {
            plugin.logWarning("Tablolar oluşturulurken SQL hatası çıktı!", "SQL error occurred while creating tables!");
            e.printStackTrace();
        }
    }

    private void migrateAuthTable(Statement stmt) {
        String table = plugin.getAuthTableName();
        addColumnIfMissing(stmt, table, "password_usable BOOLEAN DEFAULT TRUE");
        addColumnIfMissing(stmt, table, "auth_mode VARCHAR(20) DEFAULT 'PASSWORD'");
        addColumnIfMissing(stmt, table, "pin_hash VARCHAR(255) DEFAULT NULL");
        addColumnIfMissing(stmt, table, "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        addColumnIfMissing(stmt, table, "registration_ip VARCHAR(45) DEFAULT NULL");
        addColumnIfMissing(stmt, table, "last_ip VARCHAR(45) DEFAULT NULL");
        addColumnIfMissing(stmt, table, "last_world VARCHAR(64) DEFAULT NULL");
        addColumnIfMissing(stmt, table, "last_x DOUBLE DEFAULT NULL");
        addColumnIfMissing(stmt, table, "last_y DOUBLE DEFAULT NULL");
        addColumnIfMissing(stmt, table, "last_z DOUBLE DEFAULT NULL");
        addColumnIfMissing(stmt, table, "login_count INT DEFAULT 0");
        addColumnIfMissing(stmt, table, "wrong_attempts_total INT DEFAULT 0");
        addColumnIfMissing(stmt, table, "last_wrong_attempt VARCHAR(32) DEFAULT NULL");
        addColumnIfMissing(stmt, table, "security_question_id VARCHAR(64) DEFAULT NULL");
        addColumnIfMissing(stmt, table, "security_question_text VARCHAR(255) DEFAULT NULL");
        addColumnIfMissing(stmt, table, "security_question_hash VARCHAR(255) DEFAULT NULL");
        addColumnIfMissing(stmt, table, "recovery_method VARCHAR(32) DEFAULT 'question'");
        addColumnIfMissing(stmt, table, "backup_code_hashes TEXT DEFAULT NULL");
        addColumnIfMissing(stmt, table, "security_cooldowns TEXT DEFAULT NULL");
    }

    private void addColumnIfMissing(Statement stmt, String table, String columnSql) {
        try {
            stmt.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + columnSql + ";");
        } catch (SQLException ignored) {
            // Existing columns are expected after the first migration run.
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
