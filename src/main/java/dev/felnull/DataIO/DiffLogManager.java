package dev.felnull.DataIO;

import dev.felnull.Data.GroupData;
import dev.felnull.Data.InventoryData;
import dev.felnull.Data.StorageData;
import dev.felnull.DataIO.ItemSerializer;
import org.bukkit.Bukkit;
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
        String groupName = groupData.groupName;
        try (Connection conn = db.getConnection()) {
            // アイテム差分取得
            String itemSql = "SELECT page_id, slot, itemstack FROM diff_log_inventory_items WHERE group_name = ? AND timestamp <= ? ORDER BY timestamp DESC";
            Map<String, Map<Integer, ItemStack>> restoredPages = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(itemSql)) {
                ps.setString(1, groupName);
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
                String clearSql = "DELETE FROM inventory_item_table WHERE group_name = ? AND page_id = ?";
                try (PreparedStatement clearPs = conn.prepareStatement(clearSql)) {
                    clearPs.setString(1, groupName);
                    clearPs.setString(2, pageId);
                    clearPs.executeUpdate();
                }

                String insertSql = "INSERT INTO inventory_item_table (group_name, plugin_name, page_id, slot, itemstack, display_name, material, amount) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                    for (Map.Entry<Integer, ItemStack> itemEntry : items.entrySet()) {
                        ItemStack item = itemEntry.getValue();
                        insertPs.setString(1, groupName);
                        insertPs.setString(2, groupData.ownerPlugin); // 必要に応じて変更
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
            String tagSql = "SELECT page_id, tag FROM diff_log_tags WHERE group_name = ? AND timestamp <= ? ORDER BY timestamp DESC";
            Map<String, Set<String>> tagMap = new HashMap<>();
            try (PreparedStatement tagPs = conn.prepareStatement(tagSql)) {
                tagPs.setString(1, groupName);
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

            String clearTagSql = "DELETE FROM tag_table WHERE group_name = ? AND page_id = ?";
            String insertTagSql = "INSERT INTO tag_table (group_name, plugin_name, page_id, tag_id, user_tag) VALUES (?, ?, ?, ?, ?)";
            for (Map.Entry<String, Set<String>> tagEntry : tagMap.entrySet()) {
                try (PreparedStatement clearTagPs = conn.prepareStatement(clearTagSql)) {
                    clearTagPs.setString(1, groupName);
                    clearTagPs.setString(2, tagEntry.getKey());
                    clearTagPs.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(insertTagSql)) {
                    for (String tag : tagEntry.getValue()) {
                        ps.setString(1, groupName);
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

            // アイテム差分
            String itemSql = "INSERT INTO diff_log_inventory_items (group_name, page_id, slot, itemstack, timestamp) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(itemSql)) {
                for (Map.Entry<String, InventoryData> entry : s.storageInventory.entrySet()) {
                    String pageId = entry.getKey();
                    InventoryData inv = entry.getValue();
                    for (Map.Entry<Integer, ItemStack> itemEntry : inv.itemStackSlot.entrySet()) {
                        ps.setString(1, groupData.groupName);
                        ps.setString(2, pageId);
                        ps.setInt(3, itemEntry.getKey());
                        ps.setString(4, ItemSerializer.serializeToBase64(itemEntry.getValue()));
                        ps.setString(5, time);
                        ps.addBatch();
                    }
                }
                ps.executeBatch();
            }

            // タグ差分
            String tagSql = "INSERT INTO diff_log_tags (group_name, page_id, tag, timestamp) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(tagSql)) {
                for (Map.Entry<String, InventoryData> entry : s.storageInventory.entrySet()) {
                    String pageId = entry.getKey();
                    InventoryData inv = entry.getValue();
                    if (inv.userTag != null && inv.userTag.length > 0) {
                        String tagJoined = String.join(",", inv.userTag);
                        ps.setString(1, groupData.groupName);
                        ps.setString(2, pageId);
                        ps.setString(3, tagJoined);
                        ps.setString(4, time);
                        ps.addBatch();
                    }
                }
                ps.executeBatch();
            }

        } catch (SQLException e) {
            Bukkit.getLogger().warning("差分ログの保存に失敗: " + e.getMessage());
        }
    }
}
