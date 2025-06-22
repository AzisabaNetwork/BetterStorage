package dev.felnull.DataIO;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.felnull.BetterStorage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {
    private volatile HikariDataSource dataSource;
    private volatile boolean isDbConnected = false;

    private String ip;
    private int port;
    private String dbName;
    private String username;
    private String password;
    private BetterStorage plugin = BetterStorage.BSPlugin;
    private boolean isRetrying = false;
    public BukkitTask retryTask;

    public DatabaseManager() {
        this.ip = plugin.getConfig().getString("database.ip");
        this.port = plugin.getConfig().getInt("database.port");
        this.dbName = plugin.getConfig().getString("database.name");
        this.username = plugin.getConfig().getString("database.username");
        this.password = plugin.getConfig().getString("database.password");
        connectWithRetry();
    }

    // DB接続＆再接続ロジック
    public void connectWithRetry() {
        if (isRetrying) return; // ここを外に出す！
        isRetrying = true;

        retryTask = new BukkitRunnable() {
            @Override
            public void run() {
                while (!isDbConnected) {
                    try {
                        connect();
                        isDbConnected = true;
                        TableInitializer.initTables();
                        Bukkit.getLogger().info("[BetterStorage] DB接続に成功しました！");
                    } catch (Exception e) {
                        isDbConnected = false;
                        Bukkit.getLogger().warning("[BetterStorage] DB接続失敗: " + e.getMessage() + " 10秒後に再試行します");
                        try {
                            Thread.sleep(10000);
                        } catch (InterruptedException ignored) {}
                    }
                }
                isRetrying = false; // ループが終わったあとに戻す
            }
        }.runTaskAsynchronously(plugin);
    }

    // 実際の接続処理（1回のみ）
    private void connect() throws Exception {
        this.ip = plugin.getConfig().getString("database.ip");
        this.port = plugin.getConfig().getInt("database.port");
        this.dbName = plugin.getConfig().getString("database.name");
        this.username = plugin.getConfig().getString("database.username");
        this.password = plugin.getConfig().getString("database.password");
        Bukkit.getLogger().info("[BetterStorage] 接続先 → " + ip + ":" + port + "/" + dbName);
        String jdbcUrl = "jdbc:mariadb://" + ip + ":" + port + "/" + dbName
                + "?useUnicode=true&characterEncoding=utf8mb4";
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }

        dataSource = new HikariDataSource(config);

        // 疎通確認（失敗したら外に投げる）
        try (Connection conn = dataSource.getConnection()) {
            // 成功
        }
    }
    // DB利用可否判定
    public boolean isConnected() {
        return isDbConnected;
    }

    // コネクション取得
    public Connection getConnection() throws SQLException {
        if (!isDbConnected) throw new SQLException("DB未接続です");
        return dataSource.getConnection();
    }

    // 切断
    public void disconnect() {
        isDbConnected = false;
        if (retryTask != null && !retryTask.isCancelled()) {
            retryTask.cancel();
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}