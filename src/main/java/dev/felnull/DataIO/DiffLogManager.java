package dev.felnull.DataIO;

import dev.felnull.BetterStorage;
import dev.felnull.Data.GroupData;
import dev.felnull.Data.InventoryData;
import dev.felnull.Data.StorageData;
import dev.felnull.DataIO.ItemSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

public class DiffLogManager {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static boolean restoreGroupFromDiffLog(DatabaseManager db, GroupData groupData, LocalDateTime targetTime) {
        UUID groupUUID = groupData.groupUUID;
        try (Connection conn = db.getConnection()) {
            // アイテム差分取得
            String itemSql = "SELECT page_id, slot, itemstack FROM diff_log_inventory_items WHERE group_uuid = ? AND timestamp <= ? ORDER BY timestamp DESC";
            Map<String, Map<Integer, ItemStack>> restoredPages = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(itemSql)) {
                ps.setString(1, groupUUID.toString());
                ps.setString(2, targetTime.format(FORMATTER));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String pageId = rs.getString("page_id");
                        int slot = rs.getInt("slot");
                        ItemStack item = ItemSerializer.deserializeFromBase64(rs.getString("itemstack"));
                        restoredPages.computeIfAbsent(pageId, k -> new HashMap<>()).put(slot, item);
                    }
                }
            }

            for (Map.Entry<String, Map<Integer, ItemStack>> entry : restoredPages.entrySet()) {
                String pageId = entry.getKey();
                Map<Integer, ItemStack> items = entry.getValue();
                String clearSql = "DELETE FROM inventory_item_table WHERE group_uuid = ? AND page_id = ?";
                try (PreparedStatement clearPs = conn.prepareStatement(clearSql)) {
                    clearPs.setString(1, groupUUID.toString());
                    clearPs.setString(2, pageId);
                    clearPs.executeUpdate();
                }

                String insertSql = "INSERT INTO inventory_item_table " +
                        "(group_uuid, plugin_name, page_id, slot, itemstack, display_name, display_name_plain, material, amount) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                    for (Map.Entry<Integer, ItemStack> itemEntry : items.entrySet()) {
                        ItemStack item = itemEntry.getValue();
                        ItemMeta meta = item.getItemMeta();
                        String name = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : "";
                        insertPs.setString(1, groupUUID.toString());
                        insertPs.setString(2, groupData.ownerPlugin);
                        insertPs.setString(3, pageId);
                        insertPs.setInt(4, itemEntry.getKey());
                        insertPs.setString(5, ItemSerializer.serializeToBase64(item));
                        insertPs.setString(6, name);
                        insertPs.setString(7, ChatColor.stripColor(name));
                        insertPs.setString(8, item.getType().name());
                        insertPs.setInt(9, item.getAmount());
                        insertPs.addBatch();
                    }
                    insertPs.executeBatch();
                }
            }

            // タグ差分取得
            String tagSql = "SELECT page_id, tag FROM diff_log_tags WHERE group_uuid = ? AND timestamp <= ? ORDER BY timestamp DESC";
            Map<String, Set<String>> tagMap = new HashMap<>();
            try (PreparedStatement tagPs = conn.prepareStatement(tagSql)) {
                tagPs.setString(1, groupUUID.toString());
                tagPs.setString(2, targetTime.format(FORMATTER));
                try (ResultSet rs = tagPs.executeQuery()) {
                    while (rs.next()) {
                        String pageId = rs.getString("page_id");
                        String tagJoined = rs.getString("tag");
                        Set<String> tags = new HashSet<>(Arrays.asList(tagJoined.split(",")));
                        tagMap.put(pageId, tags);
                    }
                }
            }

            String clearTagSql = "DELETE FROM tag_table WHERE group_uuid = ? AND page_id = ?";
            String insertTagSql = "INSERT INTO tag_table (group_uuid, plugin_name, page_id, tag_id, user_tag) VALUES (?, ?, ?, ?, ?)";
            for (Map.Entry<String, Set<String>> tagEntry : tagMap.entrySet()) {
                try (PreparedStatement clearTagPs = conn.prepareStatement(clearTagSql)) {
                    clearTagPs.setString(1, groupUUID.toString());
                    clearTagPs.setString(2, tagEntry.getKey());
                    clearTagPs.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(insertTagSql)) {
                    for (String tag : tagEntry.getValue()) {
                        ps.setString(1, groupUUID.toString());
                        ps.setString(2, groupData.ownerPlugin);
                        ps.setString(3, tagEntry.getKey());
                        ps.setString(4, UUID.randomUUID().toString());
                        ps.setString(5, tag);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            return true;
        } catch (SQLException e) {
            Bukkit.getLogger().warning("差分ログからの復元に失敗: " + e.getMessage());
            return false;
        }
    }

    public static void saveDiffLogs(DatabaseManager db, GroupData groupData) {
        try (Connection conn = db.getConnection()) {
            StorageData s = groupData.storageData;
            if (s == null) return;

            String time = LocalDateTime.now().format(FORMATTER);
            UUID groupUUID = groupData.groupUUID;

            String itemSql = "INSERT INTO diff_log_inventory_items " +
                    "(group_uuid, plugin_name, page_id, slot, itemstack, display_name, display_name_plain, material, amount, operation_type, timestamp) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(itemSql)) {
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
                            ps.setString(1, groupUUID.toString());
                            ps.setString(2, groupData.ownerPlugin);
                            ps.setString(3, pageId);
                            ps.setInt(4, slot);
                            ps.setString(5, serializeOrNull(newItem));
                            if (newItem != null && newItem.getType() != Material.AIR) {
                                ItemMeta meta = newItem.getItemMeta();
                                String name = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : "";
                                ps.setString(6, name);
                                ps.setString(7, ChatColor.stripColor(name));
                                ps.setString(8, newItem.getType().name());
                                ps.setInt(9, newItem.getAmount());
                                ps.setString(10, oldItem == null ? "ADD" : "UPDATE");
                            } else {
                                ps.setString(6, "");
                                ps.setString(7, "");
                                ps.setString(8, "");
                                ps.setNull(9, Types.INTEGER);
                                ps.setString(10, "REMOVE");
                            }
                            ps.setString(11, time);
                            ps.addBatch();
                        }
                    }
                }
                ps.executeBatch();
            }

            // タグも従来通り保存
            // （省略）
        } catch (SQLException e) {
            Bukkit.getLogger().warning("差分ログの保存に失敗: " + e.getMessage());
        }
    }

    // 🧩 ヘルパー：nullの場合は"null"とする（比較を安定させる）
    private static String serializeOrNull(ItemStack item) {
        return (item == null || item.getType() == Material.AIR) ? "null" : ItemSerializer.serializeToBase64(item);
    }

    // 🧩 ヘルパー：最新の差分ログからスロット→ItemStackマップを取得
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

    public static List<LocalDateTime> getDiffTimestamps(String groupUUID) {
        List<LocalDateTime> timestamps = new ArrayList<>();
        String sql = "SELECT DISTINCT timestamp FROM diff_log_inventory_items WHERE group_uuid = ? ORDER BY timestamp DESC";

        try (Connection conn = BetterStorage.BSPlugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, groupUUID);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String ts = rs.getString("timestamp");
                    try {
                        timestamps.add(LocalDateTime.parse(ts, FORMATTER));
                    } catch (DateTimeParseException e) {
                        Bukkit.getLogger().warning("日付のパースに失敗: " + ts);
                    }
                }
            }

        } catch (SQLException e) {
            Bukkit.getLogger().warning("差分ログのタイムスタンプ取得に失敗: " + e.getMessage());
        }

        return timestamps;
    }
}

