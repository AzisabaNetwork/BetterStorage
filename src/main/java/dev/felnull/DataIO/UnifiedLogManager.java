package dev.felnull.DataIO;

import com.google.gson.Gson;
import dev.felnull.BetterStorage;
import dev.felnull.Data.GroupData;
import dev.felnull.Data.GroupStorageBackup;
import dev.felnull.Data.InventoryData;
import dev.felnull.Data.StorageData;
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
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

    public class UnifiedLogManager {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        private static final Gson gson = new Gson();

        // === 1. スナップショット保存（過去の状態をGZIP+JSONで保存） ===
        public static void saveBackupSnapshot(GroupData groupData) {
            try (Connection conn = BetterStorage.BSPlugin.getDatabaseManager().getConnection()) {
                String sql = "INSERT INTO rollback_log (group_uuid, timestamp, json_data) VALUES (?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    GroupStorageBackup backup = new GroupStorageBackup(
                            groupData.groupUUID, groupData.storageData, groupData.version
                    );
                    String json = gson.toJson(backup);
                    byte[] compressed = compress(json.getBytes());
                    ps.setString(1, groupData.groupUUID.toString());
                    ps.setString(2, LocalDateTime.now().format(FORMATTER));
                    ps.setBytes(3, compressed);
                    ps.executeUpdate();
                }
            } catch (Exception e) {
                Bukkit.getLogger().warning("バックアップスナップショット保存失敗: " + e.getMessage());
            }
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

        public static @Nullable GroupStorageBackup getLatestSnapshot(DatabaseManager db, UUID groupUUID, LocalDateTime beforeTime) {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT data FROM rollback_log WHERE group_uuid = ? AND timestamp <= ? ORDER BY timestamp DESC LIMIT 1")) {
                ps.setString(1, groupUUID.toString());
                ps.setString(2, beforeTime.format(FORMATTER));

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        byte[] compressed = rs.getBytes("data");
                        byte[] decompressed = decompress(compressed);
                        String json = new String(decompressed, StandardCharsets.UTF_8);
                        return gson.fromJson(json, GroupStorageBackup.class);
                    }
                }
            } catch (SQLException | IOException e) {
                Bukkit.getLogger().warning("[BetterStorage] rollbackログの取得に失敗: " + e.getMessage());
            }
            return null;
        }

        public static void restoreSnapshot(GroupData group, GroupStorageBackup backup) {
            if (group == null || backup == null || backup.storageData == null) {
                return;
            }

            // バージョンも復元
            group.version = backup.version;

            // 古いStorageDataを復元
            StorageData restored = backup.storageData;
            restored.attach(group); // GroupDataと結び付け
            group.storageData = restored;

            // ストレージデータを書き戻す
            DataIO.saveGroupDataWithoutVersionCheck(group);
        }

        public static boolean restoreGroupToTimestamp(UUID groupUUID, LocalDateTime targetTime) {
            try (Connection conn = BetterStorage.BSPlugin.getDatabaseManager().getConnection()) {
                // 1. スナップショットを取得
                String sql = "SELECT timestamp, json_data FROM rollback_log " +
                        "WHERE group_uuid = ? AND timestamp <= ? ORDER BY timestamp DESC LIMIT 1";
                LocalDateTime snapshotTime = null;
                GroupStorageBackup backup = null;

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, groupUUID.toString());
                    ps.setString(2, targetTime.format(FORMATTER));
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            snapshotTime = LocalDateTime.parse(rs.getString("timestamp"), FORMATTER);
                            byte[] compressed = rs.getBytes("json_data"); // ← byte[] として取得
                            String json = decompressToString(compressed);         // ← String に展開
                            backup = gson.fromJson(json, GroupStorageBackup.class);
                        } else {
                            Bukkit.getLogger().warning("[BetterStorage] ロールバックスナップショットが見つかりません: " + groupUUID);
                            return false;
                        }
                    }
                }

                // 2. GroupData を取得して適用
                GroupData group = GroupData.resolveByUUID(groupUUID);
                if (group == null) {
                    Bukkit.getLogger().warning("[BetterStorage] グループが存在しません: " + groupUUID);
                    return false;
                }

                backup.storageData.attach(group);
                group.storageData = backup.storageData;
                group.version = backup.version;

                // 3. スナップショット以降の差分を適用
                applyForwardDiffs(group, snapshotTime, targetTime);

                // 4. 保存
                return DataIO.saveGroupData(group, group.version);
            } catch (Exception e) {
                Bukkit.getLogger().warning("[BetterStorage] 巻き戻しに失敗: " + e.getMessage());
                return false;
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
                String itemSql = "SELECT page_id, slot, itemstack FROM diff_log_inventory_items " +
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
                            ItemStack item = ItemSerializer.deserializeFromBase64(base64);

                            InventoryData inv = groupData.storageData.storageInventory.get(pageId);
                            if (inv != null && inv.itemStackSlot != null) {
                                if (item == null || item.getType() == Material.AIR) {
                                    inv.itemStackSlot.remove(slot); // REMOVE
                                } else {
                                    inv.itemStackSlot.put(slot, item); // ADD/UPDATE
                                }
                            }
                        }
                    }
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

                return true;
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
                UUID groupUUID = groupData.groupUUID;

                boolean changed = saveItemDiffs(conn, groupData, now) | saveTagDiffs(conn, groupData, now);
                if (changed) {
                    groupData.version++;
                }

            } catch (SQLException e) {
                Bukkit.getLogger().warning("[BetterStorage] 差分ログの保存に失敗: " + e.getMessage());
            }
        }


        private static boolean saveItemDiffs(Connection conn, GroupData groupData, String timestamp) throws SQLException {
            boolean hasChanges = false;
            UUID groupUUID = groupData.groupUUID;
            StorageData s = groupData.storageData;

            String sql = "INSERT INTO diff_log_inventory_items " +
                    "(group_uuid, plugin_name, page_id, slot, itemstack, old_itemstack, display_name, display_name_plain, material, amount, operation_type, timestamp) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Map.Entry<String, InventoryData> entry : s.storageInventory.entrySet()) {
                    String pageId = entry.getKey();
                    InventoryData currentInv = entry.getValue();

                    Map<Integer, ItemStack> oldItems = loadLatestLoggedItems(conn, groupUUID.toString(), pageId);
                    Set<Integer> allSlots = new HashSet<>();
                    allSlots.addAll(oldItems.keySet());
                    allSlots.addAll(currentInv.itemStackSlot.keySet());

                    for (int slot : allSlots) {
                        ItemStack oldItem = oldItems.get(slot);
                        ItemStack newItem = currentInv.itemStackSlot.get(slot);
                        if (!Objects.equals(serializeOrNull(oldItem), serializeOrNull(newItem))) {
                            hasChanges = true;

                            ps.setString(1, groupUUID.toString());
                            ps.setString(2, groupData.ownerPlugin);
                            ps.setString(3, pageId);
                            ps.setInt(4, slot);

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
                }
                ps.executeBatch();
            }

            return hasChanges;
        }


        private static boolean saveTagDiffs(Connection conn, GroupData groupData, String timestamp) throws SQLException {
            boolean hasChanges = false;
            UUID groupUUID = groupData.groupUUID;
            StorageData s = groupData.storageData;

            String sql = "INSERT INTO diff_log_tags (group_uuid, plugin_name, page_id, tag, operation_type, timestamp) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Map.Entry<String, InventoryData> entry : s.storageInventory.entrySet()) {
                    String pageId = entry.getKey();
                    InventoryData inv = entry.getValue();
                    List<String> currentTags = inv.getUserTags();

                    Set<String> oldTags = loadLatestLoggedTags(conn, groupUUID.toString(), pageId);

                    Set<String> allTags = new HashSet<>();
                    allTags.addAll(currentTags);
                    allTags.addAll(oldTags);

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
            String sql = "SELECT slot, itemstack FROM diff_log_inventory_items " +
                    "WHERE group_uuid = ? AND page_id = ? AND timestamp = (" +
                    "SELECT MAX(timestamp) FROM diff_log_inventory_items WHERE group_uuid = ? AND page_id = ?)";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, groupUUID);
                ps.setString(2, pageId);
                ps.setString(3, groupUUID);
                ps.setString(4, pageId);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int slot = rs.getInt("slot");
                        String base64 = rs.getString("itemstack");
                        if (!"null".equals(base64)) {
                            result.put(slot, ItemSerializer.deserializeFromBase64(base64));
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

        public static LocalDateTime getTimestampForVersion(UUID groupUUID, long version) {
            try (Connection conn = BetterStorage.BSPlugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT timestamp FROM rollback_log WHERE group_uuid = ? AND version = ?")) {
                ps.setString(1, groupUUID.toString());
                ps.setLong(2, version);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getTimestamp("timestamp").toLocalDateTime();
                    }
                }
            } catch (SQLException e) {
                Bukkit.getLogger().warning("[BetterStorage] バージョンからtimestamp取得に失敗: " + e.getMessage());
            }
            return null;
        }



    }

