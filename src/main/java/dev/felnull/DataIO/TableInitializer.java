package dev.felnull.DataIO;

import dev.felnull.BetterStorage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

public class TableInitializer {

    private static final Logger LOGGER = Logger.getLogger("BetterStorage");

    public static void initTables() {
        try (Connection conn = BetterStorage.BSPlugin.getDatabaseManager().getConnection();
             Statement stmt = conn.createStatement()) {

            // group_table（UUID主キー＋group_nameにUNIQUE制約）
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS group_table (" +
                            "group_uuid VARCHAR(255) PRIMARY KEY, " +
                            "group_name VARCHAR(255) UNIQUE NOT NULL, " +
                            "display_name VARCHAR(255), " +
                            "is_private BOOLEAN NOT NULL, " +
                            "owner_plugin VARCHAR(255), " +
                            "version BIGINT NOT NULL" +
                            ");"
            );

            // group_member_table（group_uuidを参照）
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS group_member_table (" +
                            "group_uuid VARCHAR(255) NOT NULL, " +
                            "member_uuid VARCHAR(36) NOT NULL, " +
                            "role VARCHAR(255) NOT NULL" +
                            ");"
            );

            // storage_table（group_uuidベースに変更）
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS storage_table (" +
                            "group_uuid VARCHAR(255) NOT NULL, " +
                            "plugin_name VARCHAR(255) NOT NULL, " +
                            "bank_money DOUBLE NOT NULL, " +
                            "require_bank_permission TEXT" +
                            ");"
            );

            // inventory_table（group_uuidベースに変更）
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS inventory_table (" +
                            "group_uuid VARCHAR(255) NOT NULL, " +
                            "plugin_name VARCHAR(255) NOT NULL, " +
                            "page_id VARCHAR(255) NOT NULL, " +
                            "display_name VARCHAR(255), " +
                            "row_count INT NOT NULL, " +
                            "require_permission TEXT" +
                            ");"
            );

            // inventory_item_table（group_uuidベースに変更）
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS inventory_item_table (" +
                            "group_uuid VARCHAR(255) NOT NULL, " +
                            "plugin_name VARCHAR(255) NOT NULL, " +
                            "page_id VARCHAR(255) NOT NULL, " +
                            "slot INT NOT NULL, " +
                            "itemstack TEXT NOT NULL, " +
                            "display_name VARCHAR(255), " +
                            "material VARCHAR(255), " +
                            "amount INT" +
                            ");"
            );

            // tag_table（group_uuidベースに変更）
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS tag_table (" +
                            "group_uuid VARCHAR(255) NOT NULL, " +
                            "plugin_name VARCHAR(255) NOT NULL, " +
                            "page_id VARCHAR(255) NOT NULL, " +
                            "user_tag VARCHAR(255)" +
                            ");"
            );

            // inventory_item_log（UUID対応版）
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS inventory_item_log (" +
                            "group_uuid VARCHAR(255) NOT NULL, " +
                            "plugin_name VARCHAR(255) NOT NULL, " +
                            "page_id VARCHAR(255) NOT NULL, " +
                            "slot INT NOT NULL, " +
                            "operation_type VARCHAR(32) NOT NULL, " +
                            "itemstack TEXT, " +
                            "display_name VARCHAR(255), " +
                            "material VARCHAR(255), " +
                            "amount INT, " +
                            "timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                            ");"
            );

            // diff_log_inventory_items（差分ログ、UUID対応版）
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS diff_log_inventory_items (" +
                            "group_uuid VARCHAR(255) NOT NULL, " +
                            "plugin_name VARCHAR(255) NOT NULL, " +
                            "page_id VARCHAR(255) NOT NULL, " +
                            "slot INT NOT NULL, " +
                            "itemstack TEXT, " +
                            "operation_type VARCHAR(32), " +
                            "timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                            ");"
            );

            //diff_log_tags
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS diff_log_tags (" +
                            "group_uuid VARCHAR(255) NOT NULL, " +
                            "plugin_name VARCHAR(255) NOT NULL, " +
                            "page_id VARCHAR(255) NOT NULL, " +
                            "tag TEXT, " +
                            "timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                            ");"
            );

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS rollback_log (" +
                            "group_uuid VARCHAR(255) NOT NULL, " +
                            "timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                            "json_data LONGTEXT NOT NULL, " +
                            "PRIMARY KEY (group_uuid, timestamp)" +
                            ");"
            );

            LOGGER.info("[BetterStorage] 全テーブルの初期化が完了しました。");

        } catch (SQLException e) {
            LOGGER.warning("[BetterStorage] テーブル初期化中にエラーが発生しました: " + e.getMessage());
        }
    }


    public static void ensureIndexes(DatabaseManager db) {
        try (Connection conn = db.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();

            // チェックすべきインデックス定義
            Map<String, String> indexes = new LinkedHashMap<>();
            indexes.put("idx_group_tag", "CREATE INDEX idx_group_tag ON tag_table(group_uuid, user_tag)");
            indexes.put("idx_group_member", "CREATE INDEX idx_group_member ON group_member_table(group_uuid)");
            indexes.put("idx_inventory_page", "CREATE INDEX idx_inventory_page ON inventory_table(group_uuid, page_id)");

            for (Map.Entry<String, String> entry : indexes.entrySet()) {
                String indexName = entry.getKey();
                String createSql = entry.getValue();

                boolean exists = false;
                try (ResultSet rs = meta.getIndexInfo(null, null, getTableNameFromIndex(indexName), false, false)) {
                    while (rs.next()) {
                        String existing = rs.getString("INDEX_NAME");
                        if (indexName.equalsIgnoreCase(existing)) {
                            exists = true;
                            break;
                        }
                    }
                }

                if (!exists) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.executeUpdate(createSql);
                        Bukkit.getLogger().info("[BetterStorage] インデックス " + indexName + " を作成しましたにゃ");
                    }
                } else {
                    Bukkit.getLogger().info("[BetterStorage] インデックス " + indexName + " は既に存在しますにゃ");
                }
            }

        } catch (SQLException e) {
            Bukkit.getLogger().warning("[BetterStorage] インデックス確認・作成中にエラー: " + e.getMessage());
        }
    }

    // 補助関数: インデックス名からテーブル名を推定
    private static String getTableNameFromIndex(String indexName) {
        if (indexName.contains("tag")) return "tag_table";
        if (indexName.contains("member")) return "group_member_table";
        if (indexName.contains("inventory")) return "inventory_table";
        return "";
    }


}