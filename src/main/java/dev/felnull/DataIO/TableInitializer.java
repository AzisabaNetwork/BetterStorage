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

            // --- 骨格だけ作る（各テーブル 最小1列。制約は後段で保証） ---

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS group_table (group_uuid VARCHAR(255) PRIMARY KEY)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS group_member_table (group_uuid VARCHAR(255) NULL)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS storage_table (group_uuid VARCHAR(255) NULL)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS inventory_table (group_uuid VARCHAR(255) NULL)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS inventory_item_table (group_uuid VARCHAR(255) NULL)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS tag_table (group_uuid VARCHAR(255) NULL)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS inventory_item_log (group_uuid VARCHAR(255) NULL)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS diff_log_inventory_items (group_uuid VARCHAR(255) NULL)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS diff_log_tags (group_uuid VARCHAR(255) NULL)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS inventory_item_summary (date DATE NULL)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS rollback_log (group_uuid VARCHAR(255) NULL)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS group_deleted_backup (group_uuid VARCHAR(255) NULL)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS rollback_operation_log (group_uuid VARCHAR(255) NULL)");
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS deleted_group_history (group_uuid VARCHAR(255) NULL)");

            // --- 不足カラムを“追加のみ”で補完（元の定義どおり） ---

            // group_table
            addColumnIfNotExists(conn, "group_table", "group_name",   "VARCHAR(255) NOT NULL");
            addColumnIfNotExists(conn, "group_table", "display_name", "VARCHAR(255)");
            addColumnIfNotExists(conn, "group_table", "is_private",   "BOOLEAN NOT NULL");
            addColumnIfNotExists(conn, "group_table", "owner_plugin", "VARCHAR(255)");
            addColumnIfNotExists(conn, "group_table", "ecp_imported", "TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'ECPからインポート済みかどうか'");

            // group_member_table
            addColumnIfNotExists(conn, "group_member_table", "member_uuid", "VARCHAR(36) NOT NULL");
            addColumnIfNotExists(conn, "group_member_table", "role",        "VARCHAR(255) NOT NULL");

            // storage_table
            addColumnIfNotExists(conn, "storage_table", "plugin_name",             "VARCHAR(255) NOT NULL");
            addColumnIfNotExists(conn, "storage_table", "bank_money",              "DOUBLE NOT NULL");
            addColumnIfNotExists(conn, "storage_table", "require_bank_permission", "TEXT");

            // inventory_table
            addColumnIfNotExists(conn, "inventory_table", "plugin_name",        "VARCHAR(255) NOT NULL");
            addColumnIfNotExists(conn, "inventory_table", "page_id",            "VARCHAR(255) NOT NULL");
            addColumnIfNotExists(conn, "inventory_table", "display_name",       "VARCHAR(255)");
            addColumnIfNotExists(conn, "inventory_table", "row_count",          "INT NOT NULL");
            addColumnIfNotExists(conn, "inventory_table", "require_permission", "TEXT");
            addColumnIfNotExists(conn, "inventory_table", "version",            "BIGINT NOT NULL DEFAULT 0");

            // inventory_item_table
            addColumnIfNotExists(conn, "inventory_item_table", "plugin_name",        "VARCHAR(255) NOT NULL");
            addColumnIfNotExists(conn, "inventory_item_table", "page_id",            "VARCHAR(255) NOT NULL");
            addColumnIfNotExists(conn, "inventory_item_table", "slot",               "INT NOT NULL");
            addColumnIfNotExists(conn, "inventory_item_table", "itemstack",          "TEXT NOT NULL");
            addColumnIfNotExists(conn, "inventory_item_table", "display_name",       "VARCHAR(255)");
            addColumnIfNotExists(conn, "inventory_item_table", "display_name_plain", "VARCHAR(255)");
            addColumnIfNotExists(conn, "inventory_item_table", "material",           "VARCHAR(255)");
            addColumnIfNotExists(conn, "inventory_item_table", "amount",             "INT");

            // tag_table
            addColumnIfNotExists(conn, "tag_table", "plugin_name", "VARCHAR(255) NOT NULL");
            addColumnIfNotExists(conn, "tag_table", "page_id",     "VARCHAR(255) NOT NULL");
            addColumnIfNotExists(conn, "tag_table", "user_tag",    "VARCHAR(255)");

            // inventory_item_log
            addColumnIfNotExists(conn, "inventory_item_log", "plugin_name",        "VARCHAR(255) NOT NULL");
            addColumnIfNotExists(conn, "inventory_item_log", "page_id",            "VARCHAR(255) NOT NULL");
            addColumnIfNotExists(conn, "inventory_item_log", "slot",               "INT NOT NULL");
            addColumnIfNotExists(conn, "inventory_item_log", "operation_type",     "VARCHAR(32) NOT NULL");
            addColumnIfNotExists(conn, "inventory_item_log", "player_uuid",        "VARCHAR(36)");
            addColumnIfNotExists(conn, "inventory_item_log", "itemstack",          "TEXT");
            addColumnIfNotExists(conn, "inventory_item_log", "display_name",       "VARCHAR(255)");
            addColumnIfNotExists(conn, "inventory_item_log", "display_name_plain", "VARCHAR(255)");
            addColumnIfNotExists(conn, "inventory_item_log", "material",           "VARCHAR(255)");
            addColumnIfNotExists(conn, "inventory_item_log", "amount",             "INT");
            addColumnIfNotExists(conn, "inventory_item_log", "timestamp",          "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");

            // diff_log_inventory_items
            addColumnIfNotExists(conn, "diff_log_inventory_items", "plugin_name",        "VARCHAR(255) NOT NULL");
            addColumnIfNotExists(conn, "diff_log_inventory_items", "page_id",            "VARCHAR(255) NOT NULL");
            addColumnIfNotExists(conn, "diff_log_inventory_items", "slot",               "INT NOT NULL");
            addColumnIfNotExists(conn, "diff_log_inventory_items", "itemstack",          "TEXT");
            addColumnIfNotExists(conn, "diff_log_inventory_items", "old_itemstack",      "TEXT");
            addColumnIfNotExists(conn, "diff_log_inventory_items", "display_name",       "VARCHAR(255)");
            addColumnIfNotExists(conn, "diff_log_inventory_items", "display_name_plain", "VARCHAR(255)");
            addColumnIfNotExists(conn, "diff_log_inventory_items", "material",           "VARCHAR(255)");
            addColumnIfNotExists(conn, "diff_log_inventory_items", "amount",             "INT");
            addColumnIfNotExists(conn, "diff_log_inventory_items", "operation_type",     "VARCHAR(32)");
            addColumnIfNotExists(conn, "diff_log_inventory_items", "timestamp",          "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");

            // diff_log_tags
            addColumnIfNotExists(conn, "diff_log_tags", "plugin_name",    "VARCHAR(255) NOT NULL");
            addColumnIfNotExists(conn, "diff_log_tags", "page_id",        "VARCHAR(255) NOT NULL");
            addColumnIfNotExists(conn, "diff_log_tags", "tag",            "TEXT");
            addColumnIfNotExists(conn, "diff_log_tags", "operation_type", "VARCHAR(32)");
            addColumnIfNotExists(conn, "diff_log_tags", "timestamp",      "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");

            // inventory_item_summary（PKは後段で付与）
            addColumnIfNotExists(conn, "inventory_item_summary", "player_uuid",        "VARCHAR(36)");
            addColumnIfNotExists(conn, "inventory_item_summary", "group_uuid",         "VARCHAR(128)");
            addColumnIfNotExists(conn, "inventory_item_summary", "plugin_name",        "VARCHAR(64)");
            addColumnIfNotExists(conn, "inventory_item_summary", "page_id",            "VARCHAR(64)");
            addColumnIfNotExists(conn, "inventory_item_summary", "material",           "VARCHAR(64)");
            addColumnIfNotExists(conn, "inventory_item_summary", "display_name",       "VARCHAR(128)");
            addColumnIfNotExists(conn, "inventory_item_summary", "display_name_plain", "VARCHAR(128)");
            addColumnIfNotExists(conn, "inventory_item_summary", "operation_type",     "VARCHAR(16)");
            addColumnIfNotExists(conn, "inventory_item_summary", "total_amount",       "INT");

            // rollback_log（複合PKは後段で付与）
            addColumnIfNotExists(conn, "rollback_log", "timestamp", "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");
            addColumnIfNotExists(conn, "rollback_log", "json_data", "LONGBLOB NOT NULL");

            // group_deleted_backup（複合PKは後段で付与）
            addColumnIfNotExists(conn, "group_deleted_backup", "timestamp", "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");
            addColumnIfNotExists(conn, "group_deleted_backup", "json_data", "LONGBLOB NOT NULL");

            // rollback_operation_log
            addColumnIfNotExists(conn, "rollback_operation_log", "plugin_name", "VARCHAR(255) NOT NULL");
            addColumnIfNotExists(conn, "rollback_operation_log", "target_time", "TIMESTAMP NOT NULL");
            addColumnIfNotExists(conn, "rollback_operation_log", "timestamp",   "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");

            // deleted_group_history（複合PKは後段で付与）
            addColumnIfNotExists(conn, "deleted_group_history", "group_name",         "VARCHAR(255)");
            addColumnIfNotExists(conn, "deleted_group_history", "deletion_timestamp", "DATETIME NOT NULL");
            addColumnIfNotExists(conn, "deleted_group_history", "executed_by",        "VARCHAR(64)");

            // --- 制約を後段で付与（内容は元の定義を維持） ---
            ensurePrimaryKey(conn, "inventory_item_summary",
                    new String[]{"date","player_uuid","group_uuid","plugin_name","page_id","material","display_name_plain","operation_type"});

            ensurePrimaryKey(conn, "rollback_log",
                    new String[]{"group_uuid","timestamp"});

            ensurePrimaryKey(conn, "group_deleted_backup",
                    new String[]{"group_uuid","timestamp"});

            ensurePrimaryKey(conn, "deleted_group_history",
                    new String[]{"group_uuid","deletion_timestamp"});

            // UNIQUE(group_table.group_name) は ensureIndexes 側にある想定（なければここで↓）
            ensureUniqueIndex(conn, "group_table", "unique_group_name", new String[]{"group_name"});

            // UNIQUE(inventory_table(group_uuid,page_id)) はもともと制約だったので UNIQUE INDEX で維持
            ensureUniqueIndex(conn, "inventory_table", "unique_inventory_page", new String[]{"group_uuid","page_id"});

            LOGGER.info("[BetterStorage] 全テーブルの初期化（骨格→列補完→制約付与）完了にゃ");

        } catch (SQLException e) {
            LOGGER.warning("[BetterStorage] テーブル初期化中にエラー: " + e.getMessage());
        }
    }



    public static void ensureIndexes(DatabaseManager db) {

        try (Connection conn = db.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();

            // 明示的に管理するインデックス定義
            Map<String, String> indexes = new LinkedHashMap<>();
            indexes.put("idx_group_tag", "CREATE INDEX idx_group_tag ON tag_table(group_uuid, user_tag)");
            indexes.put("idx_group_member", "CREATE INDEX idx_group_member ON group_member_table(group_uuid)");
            indexes.put("idx_inventory_page", "CREATE INDEX idx_inventory_page ON inventory_table(group_uuid, page_id)");
            indexes.put("idx_diff_log_time", "CREATE INDEX idx_diff_log_time ON diff_log_inventory_items(timestamp)");
            indexes.put("idx_item_log_time", "CREATE INDEX idx_item_log_time ON inventory_item_log(timestamp)");
            indexes.put("idx_summary_date", "CREATE INDEX idx_summary_date ON inventory_item_summary(date)");
            indexes.put("idx_summary_player", "CREATE INDEX idx_summary_player ON inventory_item_summary(player_uuid)");
            indexes.put("idx_summary_group", "CREATE INDEX idx_summary_group ON inventory_item_summary(group_uuid)");
            indexes.put("idx_item_table_display_plain", "CREATE INDEX idx_item_table_display_plain ON inventory_item_table(display_name_plain)");
            indexes.put("idx_summary_display_plain", "CREATE INDEX idx_summary_display_plain ON inventory_item_summary(display_name_plain)");
            indexes.put("idx_group_name", "CREATE UNIQUE INDEX idx_group_name ON group_table(group_name)");
            indexes.put("idx_rollback_op_time", "CREATE INDEX idx_rollback_op_time ON rollback_operation_log(timestamp)");


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
        switch (indexName) {
            case "idx_group_tag":
                return "tag_table";
            case "idx_group_member":
                return "group_member_table";
            case "idx_inventory_page":
                return "inventory_table";
            case "idx_diff_log_time":
                return "diff_log_inventory_items";
            case "idx_item_log_time":
                   return "inventory_item_log";
            case "idx_item_table_display_plain":
                return "inventory_item_table";
            case "idx_summary_date":
            case "idx_summary_player":
            case "idx_summary_group":
            case "idx_summary_display_plain":
                return "inventory_item_summary";
            case "idx_group_name":
                return "group_table";
            case "idx_rollback_op_time":
                return "rollback_operation_log";

            default:
                throw new IllegalArgumentException("Unknown index name: " + indexName);
        }
    }

    /**
     * 指定したテーブルに指定カラムが存在しない場合、自動で追加するにゃ。
     *
     * @param conn DB接続
     * @param tableName 対象テーブル名（例: "group_table"）
     * @param columnName 追加したいカラム名（例: "ecp_imported"）
     * @param columnDefinition カラムの定義（例: "BOOLEAN NOT NULL DEFAULT FALSE"）
     */
    private static void addColumnIfNotExists(Connection conn, String tableName, String columnName, String columnDefinition) {
        try {
            boolean exists = false;
            try (ResultSet rs = conn.getMetaData().getColumns(null, null, tableName, columnName)) {
                exists = rs.next();
            }
            if (!exists) {
                try (ResultSet rs = conn.getMetaData().getColumns(null, null, tableName.toUpperCase(), columnName.toUpperCase())) {
                    exists = rs.next();
                }
            }
            if (exists) return;

            String sql = "ALTER TABLE `" + tableName + "` ADD COLUMN `" + columnName + "` " + columnDefinition;
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(sql);
                LOGGER.info("[BetterStorage] " + tableName + " にカラム '" + columnName + "' を追加したにゃ！");
            }
        } catch (SQLException e) {
            LOGGER.warning("[BetterStorage] " + tableName + " のカラム '" + columnName + "' チェック中にエラーが起きたにゃ: " + e.getMessage());
        }
    }

    // 既にあるPK名はDB依存で取れないこともあるので、存在判定は getPrimaryKeys で列集合一致をざっくりチェック
    private static void ensurePrimaryKey(Connection conn, String table, String[] columns) {
        try {
            // 既存PK列集合を取得
            Set<String> existing = new LinkedHashSet<>();
            try (ResultSet rs = conn.getMetaData().getPrimaryKeys(null, null, table)) {
                while (rs.next()) existing.add(rs.getString("COLUMN_NAME").toLowerCase());
            }
            Set<String> target = Arrays.stream(columns).map(String::toLowerCase).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (existing.equals(target)) return; // 既に一致

            // 既に別PKがある場合は触らない（構造変更禁止のため）。無ければ付与。
            if (!existing.isEmpty()) {
                LOGGER.warning("[BetterStorage] " + table + " に既存のPRIMARY KEYがあり、想定と異なるため変更しないにゃ。");
                return;
            }
            String cols = Arrays.stream(columns).map(c -> "`"+c+"`").collect(java.util.stream.Collectors.joining(","));
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("ALTER TABLE `"+table+"` ADD PRIMARY KEY ("+cols+")");
                LOGGER.info("[BetterStorage] " + table + " にPRIMARY KEYを付与したにゃ: " + Arrays.toString(columns));
            }
        } catch (SQLException e) {
            LOGGER.warning("[BetterStorage] PRIMARY KEY付与エラー ("+table+"): " + e.getMessage());
        }
    }

    private static void ensureUniqueIndex(Connection conn, String table, String indexName, String[] columns) {
        try {
            boolean exists = false;
            try (ResultSet rs = conn.getMetaData().getIndexInfo(null, null, table, true, false)) {
                while (rs.next()) {
                    String idx = rs.getString("INDEX_NAME");
                    if (indexName.equalsIgnoreCase(idx)) { exists = true; break; }
                }
            }
            if (exists) return;

            String cols = Arrays.stream(columns).map(c -> "`"+c+"`").collect(java.util.stream.Collectors.joining(","));
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("CREATE UNIQUE INDEX `"+indexName+"` ON `"+table+"`("+cols+")");
                LOGGER.info("[BetterStorage] " + table + " に UNIQUE INDEX " + indexName + " を作成したにゃ");
            }
        } catch (SQLException e) {
            LOGGER.warning("[BetterStorage] UNIQUE INDEX作成エラー ("+table+"/"+indexName+"): " + e.getMessage());
        }
    }



}