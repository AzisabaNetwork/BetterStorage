package dev.felnull.task;

import dev.felnull.Data.GroupData;
import dev.felnull.DataIO.DataIO;
import dev.felnull.DataIO.DatabaseManager;
import dev.felnull.DataIO.RollbackLogManager;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public class LogCleanerTask extends BukkitRunnable {

    private final DatabaseManager db;

    public LogCleanerTask(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void run() {
        cleanOldLogs();
        backupAllGroupData();
    }

    private void cleanOldLogs() {
        try (Connection conn = db.getConnection()) {
            int deleted1 = 0, deleted2 = 0, deleted3 = 0;

            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM inventory_item_log WHERE timestamp < NOW() - INTERVAL 6 MONTH")) {
                deleted1 = ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM diff_log_inventory_items WHERE timestamp < NOW() - INTERVAL 30 DAY")) {
                deleted2 = ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM diff_log_tags WHERE timestamp < NOW() - INTERVAL 30 DAY")) {
                deleted3 = ps.executeUpdate();
            }

            Bukkit.getLogger().info("[BetterStorage] 古いログ削除: " + deleted1 + "件 (rollback), " + deleted2 + "件 (diff_items), " + deleted3 + "件 (diff_tags)");
        } catch (SQLException e) {
            Bukkit.getLogger().warning("[BetterStorage] ログクリーンアップに失敗: " + e.getMessage());
        }
    }


    private void backupAllGroupData() {
        List<GroupData> allGroupData = DataIO.loadAllGroups();
        allGroupData.forEach(RollbackLogManager::saveRollbackLog);
        Bukkit.getLogger().info("[BetterStorage] すべてのグループデータをバックアップしました。");
    }
}