package dev.felnull.DataIO;

import com.google.gson.Gson;
import dev.felnull.BetterStorage;
import dev.felnull.Data.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class UnifiedLogManager {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Gson gson = new Gson();

    // === 1. スナップショット保存（過去の状態をGZIP+JSONで保存） ===
    public static boolean saveBackupSnapshot(GroupData groupData) {
        Logger logger = Bukkit.getLogger();

        StorageData storage = groupData.storageData;
        if (storage == null) {
            logger.warning("[BetterStorage] スナップショット保存失敗: storageData が null");
            return false;
        }

        try (Connection conn = BetterStorage.BSPlugin.getDatabaseManager().getConnection()) {
            String sql = "INSERT INTO rollback_log (group_uuid, timestamp, json_data) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                storage.detach();
                DeletedGroupBackup backup = new DeletedGroupBackup(groupData, new StorageDataBackup(storage));

                String json = gson.toJson(backup);
                byte[] compressed = compress(json.getBytes());

                ps.setString(1, groupData.groupUUID.toString());
                ps.setString(2, LocalDateTime.now().format(FORMATTER));
                ps.setBytes(3, compressed);
                ps.executeUpdate();
            }
            return true;
        } catch (Exception e) {
            logger.warning("バックアップスナップショット保存失敗: " + e.getMessage());
            return false;
        }
    }

    public static List<LocalDateTime> getDiffLogTimestamps(UUID groupUUID) {
        List<LocalDateTime> timestamps = new ArrayList<>();
        String sql = "SELECT DISTINCT timestamp FROM diff_log_inventory_items WHERE group_uuid = ? ORDER BY timestamp DESC";

        try (Connection conn = BetterStorage.BSPlugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, groupUUID.toString());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String ts = rs.getString("timestamp");
                    try {
                        timestamps.add(LocalDateTime.parse(ts, FORMATTER));
                    } catch (DateTimeParseException e) {
                        Bukkit.getLogger().warning("[BetterStorage] 差分ログタイムスタンプのパース失敗: " + ts);
                    }
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().warning("[BetterStorage] 差分ログタイムスタンプ取得に失敗: " + e.getMessage());
        }

        return timestamps;
    }



    private static byte[] compress(byte[] input) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(byteStream)) {
            gzip.write(input);
        }
        return byteStream.toByteArray();
    }

    private static byte[] decompress(byte[] compressed) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzip.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return out.toByteArray();
        }
    }

    private static String decompressToString(byte[] compressed) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
             InputStreamReader reader = new InputStreamReader(gzip, StandardCharsets.UTF_8);
             BufferedReader in = new BufferedReader(reader)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    public static @Nullable DeletedGroupBackup getLatestSnapshot(DatabaseManager db, UUID groupUUID, LocalDateTime beforeTime) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT json_data FROM rollback_log WHERE group_uuid = ? AND timestamp <= ? ORDER BY timestamp DESC LIMIT 1")) {
            ps.setString(1, groupUUID.toString());
            ps.setString(2, beforeTime.format(FORMATTER));

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    byte[] compressed = rs.getBytes("json_data"); // ← カラム名ここ注意！"data" → "json_data"
                    byte[] decompressed = decompress(compressed);
                    String json = new String(decompressed, StandardCharsets.UTF_8);
                    return gson.fromJson(json, DeletedGroupBackup.class);
                }
            }
        } catch (SQLException | IOException e) {
            Bukkit.getLogger().warning("[BetterStorage] rollbackログの取得に失敗: " + e.getMessage());
        }
        return null;
    }


    public static void restoreSnapshot(GroupData group, DeletedGroupBackup backup) {
        if (group == null || backup == null || backup.storageData == null) {
            return;
        }

        // 古い StorageData を復元
        StorageData restored = backup.storageData.toStorageData();
        restored.attach(group);   // GroupData に紐付け
        group.storageData = restored;

        // 復元したストレージを永続化
        DataIO.saveGroupData(group, null);
    }


    public static boolean restoreGroupToTimestamp(UUID groupUUID, LocalDateTime targetTime) {
        try (Connection conn = BetterStorage.BSPlugin.getDatabaseManager().getConnection()) {

            //todoここに適用中の時にインベントリ開けなくする処理入れるべき

            // 1. スナップショットを取得
            String sql = "SELECT timestamp, json_data FROM rollback_log " +
                    "WHERE group_uuid = ? AND timestamp <= ? ORDER BY timestamp DESC LIMIT 1";
            LocalDateTime snapshotTime = null;
            DeletedGroupBackup backup = null;

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, groupUUID.toString());
                ps.setString(2, targetTime.format(FORMATTER));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        snapshotTime = LocalDateTime.parse(rs.getString("timestamp"), FORMATTER);
                        byte[] compressed = rs.getBytes("json_data");
                        String json = decompressToString(compressed);
                        backup = gson.fromJson(json, DeletedGroupBackup.class);
                    } else {
                        Bukkit.getLogger().warning("[BetterStorage] ロールバックスナップショットが見つかりません: " + groupUUID);
                        return false;
                    }
                }
            }

            // 2. 現在のGroupDataを取得（なければ backup のメタ情報で再構築）
            GroupData group = GroupData.resolveByUUID(groupUUID);
            if (group == null) {
                group = backup.toGroupData(); // 完全復元
            } else {
                // storageData だけ置き換える
                StorageData restored = backup.storageData.toStorageData();
                restored.attach(group);
                group.storageData = restored;
            }

            // 3. 差分ログを snapshot → targetTime に適用
            boolean success = applyForwardDiffs(group, snapshotTime, targetTime);

            if (success) {
                // ✅ ロールバック操作を別テーブルに記録
                logRollbackOperation(groupUUID, group.ownerPlugin, targetTime);
            }

            return success;

        } catch (Exception e) {
            Bukkit.getLogger().warning("[BetterStorage] 巻き戻しに失敗: " + e.getMessage());
            return false;
        }
    }

    public static void logRollbackOperation(UUID groupUUID, String pluginName, LocalDateTime targetTime) {
        try (Connection conn = BetterStorage.BSPlugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO rollback_operation_log (group_uuid, plugin_name, target_time) VALUES (?, ?, ?)")
        ) {
            ps.setString(1, groupUUID.toString());
            ps.setString(2, pluginName);
            ps.setString(3, targetTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            ps.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().warning("[BetterStorage] ロールバック操作ログの保存に失敗: " + e.getMessage());
        }
    }






    public static Set<String> getAllGroupUUIDs() {
        Set<String> groupUUIDs = new HashSet<>();
        String sql = "SELECT DISTINCT group_uuid FROM rollback_log";

        try (Connection conn = BetterStorage.BSPlugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                groupUUIDs.add(rs.getString("group_uuid"));
            }
        } catch (SQLException e) {
            Bukkit.getLogger().warning("[BetterStorage] グループUUID一覧取得失敗: " + e.getMessage());
        }

        return groupUUIDs;
    }


    public static boolean applyForwardDiffs(GroupData groupData, LocalDateTime from, LocalDateTime to) {
        DatabaseManager db = BetterStorage.BSPlugin.getDatabaseManager();
        UUID groupUUID = groupData.groupUUID;

        try (Connection conn = db.getConnection()) {
            // ----- アイテムの差分適用 -----
            Set<LocalDateTime> appliedTimestamps = new LinkedHashSet<>();
            String itemSql = "SELECT page_id, slot, itemstack, timestamp FROM diff_log_inventory_items " +
                    "WHERE group_uuid = ? AND timestamp > ? AND timestamp <= ? " +
                    "ORDER BY timestamp ASC";
            try (PreparedStatement ps = conn.prepareStatement(itemSql)) {
                ps.setString(1, groupUUID.toString());
                ps.setString(2, from.format(FORMATTER));
                ps.setString(3, to.format(FORMATTER));

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String pageId = rs.getString("page_id");
                        int slot = rs.getInt("slot");
                        String base64 = rs.getString("itemstack");
                        String timestampStr = rs.getString("timestamp");

                        ItemStack item = ItemSerializer.deserializeFromBase64(base64);

                        InventoryData inv = groupData.storageData.storageInventory.get(pageId);
                        if (inv != null && inv.itemStackSlot != null) {
                            if (item == null || item.getType() == Material.AIR) {
                                inv.itemStackSlot.remove(slot);
                            } else {
                                inv.itemStackSlot.put(slot, item);
                            }
                        }

                        // 差分適用された timestamp を記録
                        try {
                            LocalDateTime appliedTime = LocalDateTime.parse(timestampStr, FORMATTER);
                            appliedTimestamps.add(appliedTime);
                        } catch (Exception e) {
                            Bukkit.getLogger().warning("timestampのパースに失敗: " + timestampStr);
                        }
                    }
                }
            }

            // 終了後にログ出力（必要なら）
            if (!appliedTimestamps.isEmpty()) {
                Bukkit.getLogger().info("[BetterStorage] 差分適用完了: " +
                        appliedTimestamps.stream().map(t -> "[" + t.format(FORMATTER) + "]").collect(Collectors.joining(", ")));
            } else {
                Bukkit.getLogger().info("[BetterStorage] 適用された差分はありませんでした。");
            }


            // ----- タグの差分適用 -----
            String tagSql = "SELECT page_id, tag FROM diff_log_tags " +
                    "WHERE group_uuid = ? AND timestamp > ? AND timestamp <= ? " +
                    "ORDER BY timestamp ASC";
            try (PreparedStatement ps = conn.prepareStatement(tagSql)) {
                ps.setString(1, groupUUID.toString());
                ps.setString(2, from.format(FORMATTER));
                ps.setString(3, to.format(FORMATTER));

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String pageId = rs.getString("page_id");
                        String tagJoined = rs.getString("tag");

                        InventoryData inv = groupData.storageData.storageInventory.get(pageId);
                        if (inv != null) {
                            List<String> tags = Arrays.asList(tagJoined.split(","));
                            inv.userTags.clear();
                            inv.userTags.addAll(tags);
                        }
                    }
                }
            }

            return DataIO.saveGroupDataIgnoringVersion(groupData, null);
        } catch (SQLException e) {
            Bukkit.getLogger().warning("[BetterStorage] applyForwardDiffs に失敗: " + e.getMessage());
            return false;
        }
    }


    public static void saveDiffLogs(DatabaseManager db, GroupData groupData) {
        try (Connection conn = db.getConnection()) {
            StorageData s = groupData.storageData;
            if (s == null) return;

            String now = LocalDateTime.now().format(FORMATTER);

            boolean changed = false;

            for (Map.Entry<String, InventoryData> entry : s.storageInventory.entrySet()) {
                String pageId = entry.getKey();
                InventoryData inv = entry.getValue();

                boolean itemChanged = saveItemDiffs(conn, groupData, inv, pageId, now);
                boolean tagChanged = saveTagDiffs(conn, groupData, inv, pageId, now);

                if (itemChanged || tagChanged) {
                    changed = true;
                }
            }

            if (changed) {
                // GroupData.version は更新しない（全体保存じゃないので）
            }

        } catch (SQLException e) {
            Bukkit.getLogger().warning("[BetterStorage] 差分ログの保存に失敗: " + e.getMessage());
        }
    }


    private static boolean saveItemDiffs(Connection conn, GroupData groupData, InventoryData currentInv, String pageId, String timestamp) throws SQLException {
        boolean hasChanges = false;
        UUID groupUUID = groupData.groupUUID;

        String sql = "INSERT INTO diff_log_inventory_items " +
                "(group_uuid, plugin_name, page_id, slot, itemstack, old_itemstack, display_name, display_name_plain, material, amount, operation_type, timestamp) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            Map<Integer, ItemStack> oldItems = loadLatestLoggedItems(conn, groupUUID.toString(), pageId);
            Set<Integer> allSlots = new HashSet<>();
            allSlots.addAll(oldItems.keySet());
            allSlots.addAll(currentInv.itemStackSlot.keySet());

            for (int slot : allSlots) {
                ItemStack oldItem = oldItems.get(slot);
                ItemStack newItem = currentInv.itemStackSlot.get(slot);

                // 両方nullまたはAIRなら無視
                if ((oldItem == null || oldItem.getType() == Material.AIR) &&
                        (newItem == null || newItem.getType() == Material.AIR)) {
                    continue;
                }

                if (!Objects.equals(serializeOrNull(oldItem), serializeOrNull(newItem))) {
                    hasChanges = true;

                    ps.setString(1, groupUUID.toString());
                    ps.setString(2, groupData.ownerPlugin);
                    ps.setString(3, pageId);
                    ps.setInt(4, slot);

                    // itemstack
                    if (newItem != null && newItem.getType() != Material.AIR) {
                        ps.setString(5, ItemSerializer.serializeToBase64(newItem));
                    } else {
                        ps.setNull(5, Types.VARCHAR);
                    }

                    if (oldItem != null && oldItem.getType() != Material.AIR) {
                        ps.setString(6, ItemSerializer.serializeToBase64(oldItem));
                    } else {
                        ps.setNull(6, Types.VARCHAR);
                    }

                    // display nameなど
                    if (newItem != null && newItem.getType() != Material.AIR) {
                        ItemMeta meta = newItem.getItemMeta();
                        String name = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : "";
                        ps.setString(7, name);
                        ps.setString(8, ChatColor.stripColor(name));
                        ps.setString(9, newItem.getType().name());
                        ps.setInt(10, newItem.getAmount());
                        ps.setString(11, oldItem == null ? "ADD" : "UPDATE");
                    } else {
                        ps.setString(7, "");
                        ps.setString(8, "");
                        ps.setString(9, "");
                        ps.setNull(10, Types.INTEGER);
                        ps.setString(11, "REMOVE");
                    }

                    ps.setString(12, timestamp);
                    ps.addBatch();
                }
            }

            ps.executeBatch();
        }

        return hasChanges;
    }


    private static boolean saveTagDiffs(Connection conn, GroupData groupData, InventoryData inv, String pageId, String timestamp) throws SQLException {
        boolean hasChanges = false;
        UUID groupUUID = groupData.groupUUID;
        List<String> currentTags = inv.getUserTags();

        Set<String> oldTags = loadLatestLoggedTags(conn, groupUUID.toString(), pageId);

        Set<String> allTags = new HashSet<>();
        allTags.addAll(currentTags);
        allTags.addAll(oldTags);

        String sql = "INSERT INTO diff_log_tags (group_uuid, plugin_name, page_id, tag, operation_type, timestamp) VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String tag : allTags) {
                boolean isAdded = currentTags.contains(tag) && !oldTags.contains(tag);
                boolean isRemoved = !currentTags.contains(tag) && oldTags.contains(tag);

                if (isAdded || isRemoved) {
                    hasChanges = true;
                    ps.setString(1, groupUUID.toString());
                    ps.setString(2, groupData.ownerPlugin);
                    ps.setString(3, pageId);
                    ps.setString(4, tag);
                    ps.setString(5, isAdded ? "ADD" : "REMOVE");
                    ps.setString(6, timestamp);
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }

        return hasChanges;
    }


    private static Set<String> loadLatestLoggedTags(Connection conn, String groupUUID, String pageId) throws SQLException {
        Set<String> result = new HashSet<>();
        String sql = "SELECT tag FROM diff_log_tags " +
                "WHERE group_uuid = ? AND page_id = ? AND timestamp = (" +
                "SELECT MAX(timestamp) FROM diff_log_tags WHERE group_uuid = ? AND page_id = ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupUUID);
            ps.setString(2, pageId);
            ps.setString(3, groupUUID);
            ps.setString(4, pageId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString("tag"));
                }
            }
        }
        return result;
    }


    private static String serializeOrNull(ItemStack item) {
        return (item == null || item.getType() == Material.AIR) ? null : ItemSerializer.serializeToBase64(item);
    }

    private static Map<Integer, ItemStack> loadLatestLoggedItems(Connection conn, String groupUUID, String pageId) throws SQLException {
        Map<Integer, ItemStack> result = new HashMap<>();
        Set<Integer> seenSlots = new HashSet<>();

        String sql = "SELECT slot, itemstack, operation_type FROM diff_log_inventory_items " +
                "WHERE group_uuid = ? AND page_id = ? " +
                "ORDER BY timestamp DESC";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupUUID);
            ps.setString(2, pageId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int slot = rs.getInt("slot");
                    String operationType = rs.getString("operation_type");

                    if (seenSlots.contains(slot)) {
                        continue;
                    }
                    seenSlots.add(slot);
                    String base64 = rs.getString("itemstack");

                    // null + REMOVE はスキップ
                    if ("REMOVE".equalsIgnoreCase(operationType) && base64 == null) {
                        continue;
                    }

                    if (base64 == null || base64.trim().isEmpty() || "null".equalsIgnoreCase(base64)) {
                        Bukkit.getLogger().warning("[BetterStorage] 無効なitemstack(Base64)が読み込まれました → slot=" + slot +
                                ", group=" + groupUUID + ", page=" + pageId + ", base64=[" + base64 + "]");
                        continue;
                    }

                    try {
                        result.put(slot, ItemSerializer.deserializeFromBase64(base64));
                    } catch (Exception e) {
                        Bukkit.getLogger().warning("[BetterStorage] Base64復元中に例外発生 → slot=" + slot + ": " + e.getMessage());
                    }
                }
            }
        }

        return result;
    }



    public static List<LocalDateTime> getRollbackTimestamps(UUID groupUUID) {
        List<LocalDateTime> timestamps = new ArrayList<>();
        String sql = "SELECT DISTINCT timestamp FROM rollback_log WHERE group_uuid = ? ORDER BY timestamp DESC";

        try (Connection conn = BetterStorage.BSPlugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, groupUUID.toString());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String ts = rs.getString("timestamp");
                    try {
                        timestamps.add(LocalDateTime.parse(ts, FORMATTER));
                    } catch (DateTimeParseException e) {
                        Bukkit.getLogger().warning("[BetterStorage] タイムスタンプのパースに失敗: " + ts);
                    }
                }
            }

        } catch (SQLException e) {
            Bukkit.getLogger().warning("[BetterStorage] ロールバックのタイムスタンプ取得に失敗: " + e.getMessage());
        }

        return timestamps;
    }

    //==============================================================
    //ロールバック/削除管理セクション
    //==============================================================
    public static GroupData loadDeletedGroupBackup(UUID groupUUID) {
        try (Connection conn = BetterStorage.BSPlugin.getDatabaseManager().getConnection()) {
            String sql = "SELECT json_data FROM group_deleted_backup WHERE group_uuid = ? ORDER BY timestamp DESC LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, groupUUID.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        byte[] compressed = rs.getBytes("json_data");
                        byte[] decompressed = decompress(compressed);
                        String json = new String(decompressed, StandardCharsets.UTF_8);
                        DeletedGroupBackup backup = gson.fromJson(json, DeletedGroupBackup.class);

                        return backup.toGroupData(); // ← ここでGroupDataへ変換
                    }
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[BetterStorage] グループバックアップの復元に失敗: " + e.getMessage());
        }
        return null;
    }



    public static boolean saveDeleteHistory(GroupData groupData, String executedBy) {
        try (Connection conn = BetterStorage.BSPlugin.getDatabaseManager().getConnection()) {
            String sql = "INSERT INTO deleted_group_history (group_uuid, group_name, deletion_timestamp, executed_by) " +
                    "VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, groupData.groupUUID.toString());
                ps.setString(2, groupData.groupName);
                ps.setString(3, LocalDateTime.now().format(FORMATTER));
                ps.setString(4, executedBy);
                ps.executeUpdate();
            }

            DeletedGroupBackup backup = new DeletedGroupBackup(groupData, new StorageDataBackup(groupData.storageData));
            saveGroupBackup(backup);

            return true;
        } catch (SQLException e) {
            Bukkit.getLogger().warning("[BetterStorage] グループ削除履歴の保存に失敗: " + e.getMessage());
            return false;
        }
    }


    public static void saveGroupBackup(DeletedGroupBackup backup) {
        try (Connection conn = BetterStorage.BSPlugin.getDatabaseManager().getConnection()) {
            UUID groupUUID = backup.groupUUID;
            String pluginName = backup.ownerPlugin;

            // 全ページの fullyLoaded を確認し、false ならロードしてからバックアップに含める
            if (backup.storageData != null) {
                for (Map.Entry<String, InventoryDataBackup> entry : backup.storageData.storageInventory.entrySet()) {
                    String pageId = entry.getKey();
                    InventoryDataBackup invBackup = entry.getValue();

                    InventoryData inv = invBackup.toInventoryData();
                    if (!inv.isFullyLoaded()) {
                        DataIO.loadPageItems(conn, groupUUID, pluginName, inv, pageId);
                        backup.storageData.storageInventory.put(pageId, new InventoryDataBackup(inv));
                    }
                }
            }

            // JSONとして保存
            try {
                String json = gson.toJson(backup);
                byte[] compressed = compress(json.getBytes());

                String sql = "INSERT INTO group_deleted_backup (group_uuid, timestamp, json_data) VALUES (?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, groupUUID.toString());
                    ps.setString(2, LocalDateTime.now().format(FORMATTER));
                    ps.setBytes(3, compressed);
                    ps.executeUpdate();
                }
            } catch (IOException e) {
                throw new SQLException("JSON圧縮中にエラー", e);
            }

        } catch (SQLException e) {
            Bukkit.getLogger().warning("[BetterStorage] グループバックアップの保存に失敗: " + e.getMessage());
        }
    }

    public static List<DeletedGroupInfo> getDeletedGroupList() {
        List<DeletedGroupInfo> list = new ArrayList<>();
        String sql = "SELECT group_uuid, timestamp, json_data FROM group_deleted_backup ORDER BY timestamp DESC";

        try (Connection conn = BetterStorage.BSPlugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                byte[] compressed = rs.getBytes("json_data");
                byte[] decompressed = decompress(compressed);
                String json = new String(decompressed, StandardCharsets.UTF_8);
                DeletedGroupBackup backup = gson.fromJson(json, DeletedGroupBackup.class);

                UUID groupUUID = rs.getString("group_uuid") != null ? UUID.fromString(rs.getString("group_uuid")) : null;
                LocalDateTime ts = LocalDateTime.parse(rs.getString("timestamp"), FORMATTER);

                list.add(new DeletedGroupInfo(groupUUID, backup.groupName, backup.displayName, ts));
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[BetterStorage] 削除グループ一覧の取得に失敗: " + e.getMessage());
        }

        return list;
    }




}

