package dev.felnull.DataIO;

import dev.felnull.BetterStorage;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.logging.Logger;

public class TableInitializer {

    private static final Logger LOGGER = Logger.getLogger("BetterStorage");

    public static void initTables() {
        try (Connection conn = BetterStorage.BSPlugin.getDatabaseManager().getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS group_table (" +
                            "group_name VARCHAR(255) PRIMARY KEY, " +
                            "display_name VARCHAR(255), " +
                            "is_private BOOLEAN NOT NULL, " +
                            "owner_plugin VARCHAR(255), " +
                            "version BIGINT NOT NULL" +
                            ");"
            );

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS group_member_table (" +
                            "group_uuid VARCHAR(255) NOT NULL, " +
                            "member_uuid VARCHAR(36) NOT NULL, " +
                            "role VARCHAR(255) NOT NULL" +
                            ");"
            );

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS storage_table (" +
                            "group_name VARCHAR(255) NOT NULL, " +
                            "plugin_name VARCHAR(255) NOT NULL, " +
                            "bank_money DOUBLE NOT NULL, " +
                            "require_bank_permission TEXT" +
                            ");"
            );

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS inventory_table (" +
                            "group_name VARCHAR(255) NOT NULL, " +
                            "plugin_name VARCHAR(255) NOT NULL, " +
                            "page_id VARCHAR(255) NOT NULL, " +
                            "display_name VARCHAR(255), " +
                            "row_count INT NOT NULL, " +
                            "require_permission TEXT" +
                            ");"
            );

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS inventory_item_table (" +
                            "group_name VARCHAR(255) NOT NULL, " +
                            "plugin_name VARCHAR(255) NOT NULL, " +
                            "page_id VARCHAR(255) NOT NULL, " +
                            "slot INT NOT NULL, " +
                            "itemstack TEXT NOT NULL, " +
                            "display_name VARCHAR(255), " +
                            "material VARCHAR(255), " +
                            "amount INT" +
                            ");"
            );

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS tag_table (" +
                            "group_name VARCHAR(255) NOT NULL, " +
                            "plugin_name VARCHAR(255) NOT NULL, " +
                            "page_id VARCHAR(255) NOT NULL, " +
                            "user_tag VARCHAR(255)" +
                            ");"
            );

            LOGGER.info("[BetterStorage] 全テーブルの初期化が完了しました。");

        } catch (SQLException e) {
            LOGGER.warning("[BetterStorage] テーブル初期化中にエラーが発生しました: " + e.getMessage());
        }
    }

    public static void ensureIndex(DatabaseManager db) {
        String sql = "CREATE INDEX IF NOT EXISTS idx_group_tag ON tag_table(group_uuid, user_tag)";

        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(sql);
            Bukkit.getLogger().info("[BetterStorage] インデックス idx_group_tag を確認・作成しましたにゃ");

        } catch (SQLException e) {
            Bukkit.getLogger().warning("[BetterStorage] インデックス作成失敗: " + e.getMessage());
        }
    }
}