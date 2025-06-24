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

            // グループの基本情報（UUID主キー＋表示名＋バージョンなど）
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS group_table (" +
                            "group_uuid VARCHAR(255) PRIMARY KEY, " +                       // 内部識別子（UUID）
                            "group_name VARCHAR(255) UNIQUE NOT NULL, " +                   // 論理名（内部参照に使う）
                            "display_name VARCHAR(255), " +                                 // 表示名（ユーザー向け）
                            "is_private BOOLEAN NOT NULL, " +                               // 非公開グループかどうか
                            "owner_plugin VARCHAR(255), " +                                 // このグループを扱うプラグイン名
                            "version BIGINT NOT NULL" +                                     // 差分保存や整合性確認用のバージョン
                            ");"
            );

            // グループに所属するメンバー情報
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS group_member_table (" +
                            "group_uuid VARCHAR(255) NOT NULL, " +                          // 所属グループ
                            "member_uuid VARCHAR(36) NOT NULL, " +                          // プレイヤーのUUID
                            "role VARCHAR(255) NOT NULL" +                                  // 権限（OWNER, MEMBERなど）
                            ");"
            );

            // ストレージ情報（銀行/お金関係）
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS storage_table (" +
                            "group_uuid VARCHAR(255) NOT NULL, " +                          // 所属グループ
                            "plugin_name VARCHAR(255) NOT NULL, " +                         // 使用しているプラグイン
                            "bank_money DOUBLE NOT NULL, " +                                // 所持金
                            "require_bank_permission TEXT" +                                // アクセス権限
                            ");"
            );

            // 各ページ単位のインベントリ設定
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS inventory_table (" +
                            "group_uuid VARCHAR(255) NOT NULL, " +                          // 所属グループ
                            "plugin_name VARCHAR(255) NOT NULL, " +                         // プラグイン名
                            "page_id VARCHAR(255) NOT NULL, " +                             // ページ識別子（例: "main"）
                            "display_name VARCHAR(255), " +                                 // GUIの見た目名
                            "row_count INT NOT NULL, " +                                    // GUIの行数（1～6）
                            "require_permission TEXT" +                                     // アクセス制限
                            ");"
            );

            // インベントリページ内のアイテム情報
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS inventory_item_table (" +
                            "group_uuid VARCHAR(255) NOT NULL, " +                  // 所属グループ
                            "plugin_name VARCHAR(255) NOT NULL, " +                 // プラグイン名
                            "page_id VARCHAR(255) NOT NULL, " +                     // ページ識別子
                            "slot INT NOT NULL, " +                                 // スロット番号（0～53）
                            "itemstack TEXT NOT NULL, " +                           // シリアライズされたアイテム
                            "display_name VARCHAR(255), " +                         // 表示名（任意）
                            "display_name_plain VARCHAR(255), " +                   // 色コード除去済み表示名
                            "material VARCHAR(255), " +                             // 材質（Material名）    これらは検索用
                            "amount INT" +                                          // 数量
                            ");"
            );

            // ページに設定されたタグ情報
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS tag_table (" +
                            "group_uuid VARCHAR(255) NOT NULL, " +                          // 所属グループ
                            "plugin_name VARCHAR(255) NOT NULL, " +                         // プラグイン名
                            "page_id VARCHAR(255) NOT NULL, " +                             // 対象ページ
                            "user_tag VARCHAR(255)" +                                       // ユーザー定義のタグ（例: "大切なアイテム,AssaultRifle"）
                            ");"
            );

            // アイテム操作ログ（操作種別＋日時）
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS inventory_item_log (" +
                            "group_uuid VARCHAR(255) NOT NULL, " +                          // 所属グループ
                            "plugin_name VARCHAR(255) NOT NULL, " +                         // プラグイン名
                            "page_id VARCHAR(255) NOT NULL, " +                             // ページ
                            "slot INT NOT NULL, " +                                         // スロット
                            "operation_type VARCHAR(32) NOT NULL, " +                       // 操作種別（ADD/REMOVE/UPDATEなど）
                            "player_uuid VARCHAR(36), " +                                   // 操作したプレイヤー
                            "itemstack TEXT, " +                                            // アイテム
                            "display_name VARCHAR(255), " +                                 // 表示名
                            "display_name_plain VARCHAR(255), " +                           // 色コード除去済み表示名
                            "material VARCHAR(255), " +                                     // 材質
                            "amount INT, " +                                                // 数量
                            "timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +      // ログ記録時刻
                            ");"
            );

            // 差分ログ（主にロールバックの復元用）
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS diff_log_inventory_items (" +
                            "group_uuid VARCHAR(255) NOT NULL, " +                          // 所属グループ
                            "plugin_name VARCHAR(255) NOT NULL, " +                         // プラグイン名
                            "page_id VARCHAR(255) NOT NULL, " +                             // 対象ページ
                            "slot INT NOT NULL, " +                                         // スロット番号
                            "itemstack TEXT, " +                                            // 差分のアイテム
                            "display_name VARCHAR(255), " +                                 // 差分にも表示名を残す
                            "display_name_plain VARCHAR(255), " +                           // 検索用に色なし名
                            "material VARCHAR(255), " +                                     // 検索用
                            "amount INT, " +                                                // 検索用
                            "operation_type VARCHAR(32), " +                                // 操作種別
                            "timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +      // 記録時刻
                            ");"
            );

            // タグの変更に関する差分ログ
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS diff_log_tags (" +
                            "group_uuid VARCHAR(255) NOT NULL, " +                          // 所属グループ
                            "plugin_name VARCHAR(255) NOT NULL, " +                         // プラグイン名
                            "page_id VARCHAR(255) NOT NULL, " +                             // 対象ページ
                            "tag TEXT, " +                                                  // タグ内容
                            "timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +      // 記録時刻
                            ");"
            );

            // ロールバック用の完全バックアップ
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS rollback_log (" +
                            "group_uuid VARCHAR(255) NOT NULL, " +                          // 所属グループ
                            "timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +    // バックアップ時刻
                            "json_data LONGTEXT NOT NULL, " +                               // グループ全体のシリアライズJSON
                            "PRIMARY KEY (group_uuid, timestamp)" +                         // 時刻単位で識別
                            ");"
            );

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS inventory_item_summary (" +
                            "date DATE NOT NULL, " +                                      // 操作日
                            "player_uuid VARCHAR(36), " +                                // プレイヤーUUID（任意）
                            "group_uuid VARCHAR(255), " +                                // グループUUID
                            "plugin_name VARCHAR(255), " +                               // プラグイン名
                            "page_id VARCHAR(255), " +                                   // ページID
                            "material VARCHAR(255), " +                                  // 材質
                            "display_name VARCHAR(255), " +                              // 表示名（カラー付き）
                            "display_name_plain VARCHAR(255), " +                        // 表示名（カラーなし）
                            "operation_type VARCHAR(32), " +                             // 操作種別
                            "total_amount INT, " +                                       // 合計数量
                            "PRIMARY KEY (date, player_uuid, group_uuid, plugin_name, page_id, material, display_name_plain, operation_type)" +
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
            indexes.put("idx_diff_log_time", "CREATE INDEX idx_diff_log_time ON diff_log_inventory_items(timestamp)");
            indexes.put("idx_item_log_time", "CREATE INDEX idx_item_log_time ON inventory_item_log(timestamp)");
            indexes.put("idx_display_name_plain", "CREATE INDEX idx_display_name_plain ON inventory_item_table(display_name_plain)");
            indexes.put("idx_summary_date", "CREATE INDEX idx_summary_date ON inventory_item_summary(date)");
            indexes.put("idx_summary_player", "CREATE INDEX idx_summary_player ON inventory_item_summary(player_uuid)");
            indexes.put("idx_summary_group", "CREATE INDEX idx_summary_group ON inventory_item_summary(group_uuid)");
            indexes.put("idx_summary_display_plain", "CREATE INDEX idx_summary_display_plain ON inventory_item_summary(display_name_plain)");


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
        if (indexName.contains("summary")) return "inventory_item_summary"; // ← 追加
        if (indexName.contains("tag")) return "tag_table";
        if (indexName.contains("member")) return "group_member_table";
        if (indexName.contains("inventory")) return "inventory_table";
        if (indexName.contains("diff_log")) return "diff_log_inventory_items";
        if (indexName.contains("item_log")) return "inventory_item_log";
        return "";
    }


}