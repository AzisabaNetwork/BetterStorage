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
        String sql = "DELETE FROM inventory_item_log WHERE timestamp < NOW() - INTERVAL 30 DAY";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            int deleted = ps.executeUpdate();
            Bukkit.getLogger().info("[BetterStorage] 古い巻き戻しログを " + deleted + " 件削除しました。");
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