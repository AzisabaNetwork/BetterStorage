package dev.felnull.DataIO;

import dev.felnull.Data.GroupData;
import dev.felnull.Data.InventoryData;
import dev.felnull.Data.StorageData;
import dev.felnull.DataIO.ItemSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

                String insertSql = "INSERT INTO inventory_item_table (group_uuid, plugin_name, page_id, slot, itemstack, display_name, material, amount) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                    for (Map.Entry<Integer, ItemStack> itemEntry : items.entrySet()) {
                        ItemStack item = itemEntry.getValue();
                        insertPs.setString(1, groupUUID.toString());
                        insertPs.setString(2, groupData.ownerPlugin);
                        insertPs.setString(3, pageId);
                        insertPs.setInt(4, itemEntry.getKey());
                        insertPs.setString(5, ItemSerializer.serializeToBase64(item));
                        insertPs.setString(6, item.getItemMeta() != null ? item.getItemMeta().getDisplayName() : "");
                        insertPs.setString(7, item.getType().name());
                        insertPs.setInt(8, item.getAmount());
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

            String itemSql = "INSERT INTO diff_log_inventory_items (group_uuid, plugin_name, page_id, slot, itemstack, timestamp) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(itemSql)) {

                for (Map.Entry<String, InventoryData> entry : s.storageInventory.entrySet()) {
                    String pageId = entry.getKey();
                    InventoryData currentInv = entry.getValue();

                    // 🔁 1. 前回保存されたアイテム状態をロードする（復元用に）
                    Map<Integer, ItemStack> oldItems = loadLatestLoggedItems(conn, groupUUID.toString(), pageId);

                    // 🔍 2. 差分判定（スロット単位）
                    Set<Integer> allSlots = new HashSet<>();
                    allSlots.addAll(oldItems.keySet());
                    allSlots.addAll(currentInv.itemStackSlot.keySet());

                    for (int slot : allSlots) {
                        ItemStack oldItem = oldItems.get(slot);
                        ItemStack newItem = currentInv.itemStackSlot.get(slot);

                        if (!Objects.equals(serializeOrNull(oldItem), serializeOrNull(newItem))) {
                            // 差分があるスロットだけログに保存
                            ps.setString(1, groupUUID.toString());
                            ps.setString(2, groupData.ownerPlugin);
                            ps.setString(3, pageId);
                            ps.setInt(4, slot);
                            ps.setString(5, serializeOrNull(newItem)); // nullでも保存（削除の記録になる）
                            ps.setString(6, time);
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
}

