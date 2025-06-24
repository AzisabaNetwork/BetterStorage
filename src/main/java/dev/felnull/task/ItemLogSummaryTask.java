package dev.felnull.task;

import dev.felnull.DataIO.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ItemLogSummaryTask extends BukkitRunnable {

    private final DatabaseManager db;

    public ItemLogSummaryTask(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void run() {
        try (Connection conn = db.getConnection()) {
            String sql =
                    "INSERT INTO inventory_item_summary " +
                            "(date, player_uuid, group_uuid, plugin_name, page_id, material, display_name, display_name_plain, operation_type, total_amount) " +
                            "SELECT " +
                            "    DATE(timestamp), " +
                            "    player_uuid, " +
                            "    group_uuid, " +
                            "    plugin_name, " +
                            "    page_id, " +
                            "    material, " +
                            "    display_name, " +
                            "    display_name_plain, " +
                            "    operation_type, " +
                            "    SUM(amount) " +
                            "FROM inventory_item_log " +
                            "WHERE timestamp >= CURDATE() - INTERVAL 1 DAY AND timestamp < CURDATE() " +
                            "GROUP BY " +
                            "    DATE(timestamp), " +
                            "    player_uuid, " +
                            "    group_uuid, " +
                            "    plugin_name, " +
                            "    page_id, " +
                            "    material, " +
                            "    display_name, " +
                            "    display_name_plain, " +
                            "    operation_type " +
                            "ON DUPLICATE KEY UPDATE total_amount = VALUES(total_amount)";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int updated = ps.executeUpdate();
                Bukkit.getLogger().info("[BetterStorage] inventory_item_summary 日次集計完了: " + updated + "件");
            }

        } catch (SQLException e) {
            Bukkit.getLogger().warning("[BetterStorage] 日次集計中にエラー: " + e.getMessage());
        }
    }
}