package dev.felnull.DataIO;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.felnull.BetterStorage;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {
    private HikariDataSource dataSource;

    String ip = BetterStorage.BSPlugin.getConfig().getString("database.ip");
    int port = BetterStorage.BSPlugin.getConfig().getInt("database.port");
    String dbName = BetterStorage.BSPlugin.getConfig().getString("database.name");
    String username = BetterStorage.BSPlugin.getConfig().getString("database.username");
    String password = BetterStorage.BSPlugin.getConfig().getString("database.password");

    public void connect() {
        String jdbcUrl = "jdbc:mariadb://" + ip + ":" + port + "/" + dbName
                + "?useUnicode=true&characterEncoding=utf8mb4";
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);

        // オプション設定（推奨）
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        dataSource = new HikariDataSource(config);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
