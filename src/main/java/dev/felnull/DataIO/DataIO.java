package dev.felnull.DataIO;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.felnull.BetterStorage;
import dev.felnull.Data.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static dev.felnull.DataIO.UnifiedLogManager.saveGroupBackup;

public class DataIO {
    private static final Gson gson = new Gson();
    public static DatabaseManager db = BetterStorage.BSPlugin.getDatabaseManager();

    // ===========================================================
    // ===== 1. SAVE ============================================
    // ===========================================================

    /**
     * グループ全体を保存（各テーブルへの分割保存）
     */
    public static boolean saveGroupData(GroupData g, UUID playerUUID) {
        return saveGroupDataInternal(g, playerUUID, false);
    }

    public static boolean saveGroupDataIgnoringVersion(GroupData g, UUID playerUUID) {
        return saveGroupDataInternal(g, playerUUID, true);
    }

    private static boolean saveGroupDataInternal(GroupData g, UUID playerUUID, boolean ignoreVersion) {
        try (Connection conn = db.getConnection()) {
            saveGroupTable(conn, g);
            saveGroupMembers(conn, g);

            if (g.storageData != null) {
                saveStorageData(conn, g);
                for (Map.Entry<String, InventoryData> entry : g.storageData.storageInventory.entrySet()) {
                    String pageId = entry.getKey();
                    InventoryData inv = entry.getValue();

                    if (!inv.isFullyLoaded()) {
                        Bukkit.getLogger().info("[BetterStorage] スキップ: " + g.groupName + "/" + pageId + " は未完全のため保存されません");
                        continue;
                    }

                    if (!saveSinglePage(conn, g, pageId, inv, playerUUID, ignoreVersion)) {
                        return false;
                    }
                }
            }

            UnifiedLogManager.saveDiffLogs(BetterStorage.BSPlugin.getDatabaseManager(), g);
            return true;
        } catch (SQLException e) {
            Bukkit.getLogger().warning("GroupDataの保存に失敗: " + e.getMessage());
            return false;
        }
    }

    /**
     *
     * @param conn
     * @param g
     * @param pageId
     * @param inv
     * @param playerUUID 操作したプレイヤーのuuid　プレイヤーではないならnull
     * @param ignoreVersion 楽観ロックのversionを無視するか true=無視
     * @return
     * @throws SQLException
     */
    private static boolean saveSinglePage(Connection conn, GroupData g, String pageId, InventoryData inv, UUID playerUUID, boolean ignoreVersion) throws SQLException {
        // 🔁 versionチェック
        long dbPageVersion = getInventoryPageVersion(conn, g.groupUUID, g.ownerPlugin, pageId);
        if (!ignoreVersion) {
            //Bukkit.getLogger().info("[Debug]保存前最終チェック pageId=" + pageId + ", client=" + inv.version + ", db=" + dbPageVersion);
            if (dbPageVersion != inv.version) {
                Bukkit.getLogger().warning("[BetterStorage] ページバージョン不一致: " + pageId);
                return false;
            }

            if (isInventoryDataSame(conn, g, pageId, inv)) {
                //Bukkit.getLogger().info("[BetterStorage][debug] データが変更されていないため更新しません。");
                return true;
            }

            inv.version++;
        } else {
            // ロック無視モードならログ出すだけ
            inv.version = dbPageVersion + 1;
            //Bukkit.getLogger().info("[BetterStorage] ⚠ 楽観ロック無視モードで保存中 pageId=" + pageId + ", clientVersion=" + inv.version);
        }

        // ---------- inventory_table ----------
        String invSql = "REPLACE INTO inventory_table " +
                "(group_uuid, plugin_name, page_id, display_name, row_count, require_permission, version) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(invSql)) {
            ps.setString(1, g.groupUUID.toString());
            ps.setString(2, g.ownerPlugin);
            ps.setString(3, pageId);
            ps.setString(4, inv.displayName);
            ps.setInt(5, inv.rows);
            ps.setString(6, gson.toJson(inv.requirePermission));
            ps.setLong(7, inv.version); // 新しいバージョンで保存
            ps.executeUpdate();
        }

        // ---------- inventory_item_table ----------
        Map<Integer, String> oldSlotBase64Map = new HashMap<>();
        String fetchSql = "SELECT slot, itemstack FROM inventory_item_table WHERE group_uuid = ? AND page_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(fetchSql)) {
            ps.setString(1, g.groupUUID.toString());
            ps.setString(2, pageId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    oldSlotBase64Map.put(rs.getInt("slot"), rs.getString("itemstack"));
                }
            }
        }

        Set<Integer> currentSlots = inv.itemStackSlot.keySet();

        // 削除されたスロット
        Set<Integer> slotsToDelete = new HashSet<>(oldSlotBase64Map.keySet());
        slotsToDelete.removeAll(currentSlots);
        if (!slotsToDelete.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("DELETE FROM inventory_item_table WHERE group_uuid = ? AND page_id = ? AND slot IN (");
            sb.append(slotsToDelete.stream().map(s -> "?").collect(Collectors.joining(",")));
            sb.append(")");
            try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
                ps.setString(1, g.groupUUID.toString());
                ps.setString(2, pageId);
                int index = 3;
                for (Integer slot : slotsToDelete) {
                    ps.setInt(index++, slot);
                }
                ps.executeUpdate();
            }

            for (int slot : slotsToDelete) {
                ItemStack dummy = new ItemStack(Material.AIR); // ログ用にダミー
                logInventoryItemChangeAsync(BetterStorage.BSPlugin.getDatabaseManager(),
                        g.groupUUID, g.ownerPlugin, pageId, slot, OperationType.REMOVE, dummy, playerUUID );
            }
        }

        // 追加・更新スロット
        String itemSql = "REPLACE INTO inventory_item_table (group_uuid, plugin_name, page_id, slot, itemstack, display_name, display_name_plain, material, amount) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(itemSql)) {
            for (Map.Entry<Integer, ItemStack> itemEntry : inv.itemStackSlot.entrySet()) {
                int slot = itemEntry.getKey();
                ItemStack item = itemEntry.getValue();
                String serialized = ItemSerializer.serializeToBase64(item);

                boolean isNew = !oldSlotBase64Map.containsKey(slot);
                boolean isChanged = false;
                if (!isNew) {
                    String oldBase64 = oldSlotBase64Map.get(slot);
                    isChanged = !Objects.equals(serialized, oldBase64);
                }

                if (!isNew && !isChanged) continue;

                String displayName = item.hasItemMeta() ? item.getItemMeta().getDisplayName() : "";
                String plainName = ChatColor.stripColor(displayName);

                ps.setString(1, g.groupUUID.toString());
                ps.setString(2, g.ownerPlugin);
                ps.setString(3, pageId);
                ps.setInt(4, slot);
                ps.setString(5, serialized);
                ps.setString(6, displayName);
                ps.setString(7, plainName);
                ps.setString(8, item.getType().name());
                ps.setInt(9, item.getAmount());
                ps.addBatch();

                logInventoryItemChangeAsync(BetterStorage.BSPlugin.getDatabaseManager(),
                        g.groupUUID, g.ownerPlugin, pageId, slot,
                        isNew ? OperationType.ADD : OperationType.UPDATE, item, playerUUID);
            }
            ps.executeBatch();
        }



        // ---------- tag_table ----------
        syncTagsForPage(conn, g, pageId, inv);

        return true;
    }

    // 新しいデータとDB上のデータが同じかチェック
    private static boolean isInventoryDataSame(Connection conn, GroupData g, String pageId, InventoryData inv) throws SQLException {
        // DBから現在のデータを取得
        String sql = "SELECT display_name, row_count, require_permission, version FROM inventory_table WHERE group_uuid = ? AND page_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, g.groupUUID.toString());
            ps.setString(2, pageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String dbDisplayName = rs.getString("display_name");
                    int dbRowCount = rs.getInt("row_count");
                    String dbRequirePermission = rs.getString("require_permission");
                    long dbVersion = rs.getLong("version");

                    // displayNameの比較（nullでも比較できる）
                    if ((inv.displayName == null && dbDisplayName != null) || (inv.displayName != null && !inv.displayName.equals(dbDisplayName))) {
                        return false;
                    }

                    if (dbRowCount != inv.rows) {
                        return false;
                    }

                    // require_permissionの比較（必要に応じてJSON解析）
                    if (!dbRequirePermission.equals(gson.toJson(inv.requirePermission))) {
                        return false;
                    }

                    // バージョンチェック
                    if (dbVersion != inv.version) {
                        return false;
                    }

                    // アイテムスロットの比較（インベントリ内容の差異をチェック）
                    String fetchItemsSql = "SELECT slot, itemstack FROM inventory_item_table WHERE group_uuid = ? AND page_id = ?";
                    Map<Integer, String> dbItemsMap = new HashMap<>();
                    try (PreparedStatement psItems = conn.prepareStatement(fetchItemsSql)) {
                        psItems.setString(1, g.groupUUID.toString());
                        psItems.setString(2, pageId);
                        try (ResultSet rsItems = psItems.executeQuery()) {
                            while (rsItems.next()) {
                                dbItemsMap.put(rsItems.getInt("slot"), rsItems.getString("itemstack"));
                            }
                        }
                    }

                    // 現在のアイテムスロットとDBのアイテムスロットを比較
                    if (!compareInventoryItems(dbItemsMap, inv.itemStackSlot)) {
                        return false;
                    }

                    return true;
                }
            }
        }
        return false;
    }

    // アイテムの比較メソッド
    private static boolean compareInventoryItems(Map<Integer, String> dbItems, Map<Integer, ItemStack> clientItems) {
        // アイテムの数が違う場合は即座に差異あり
        if (dbItems.size() != clientItems.size()) {
            return false;
        }

        // スロットごとにアイテムを比較
        for (Map.Entry<Integer, ItemStack> clientEntry : clientItems.entrySet()) {
            int slot = clientEntry.getKey();
            ItemStack clientItem = clientEntry.getValue();
            String dbItemBase64 = dbItems.get(slot);

            // DBアイテムとクライアントアイテムが異なる場合
            String clientItemBase64 = ItemSerializer.serializeToBase64(clientItem);
            if (!clientItemBase64.equals(dbItemBase64)) {
                return false;
            }
        }

        return true;
    }




    // ---------- SAVE / group ----------
        private static void saveGroupTable(Connection conn, GroupData g) throws SQLException {
            String sql = "REPLACE INTO group_table (group_uuid, group_name, display_name, is_private, owner_plugin) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, g.groupUUID.toString());
                ps.setString(2, g.groupName);
                ps.setString(3, g.displayName);
                ps.setBoolean(4, g.isPrivate);
                ps.setString(5, g.ownerPlugin);
                ps.executeUpdate();
            }
        }

    private static void saveGroupMembers(Connection conn, GroupData g) throws SQLException {
        String sql = "REPLACE INTO group_member_table (group_uuid, member_uuid, role) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (GroupMemberData member : g.playerList) {
                for (String role : member.role) {
                    ps.setString(1, g.groupUUID.toString());
                    ps.setString(2, member.memberUUID.toString());
                    ps.setString(3, role);
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }
    }


    // ---------- SAVE / storage ----------
        private static void saveStorageData(Connection conn, GroupData g) throws SQLException {
            StorageData s = g.storageData;
            String sql = "REPLACE INTO storage_table (group_uuid, plugin_name, bank_money, require_bank_permission) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, g.groupUUID.toString());
                ps.setString(2, g.ownerPlugin);
                ps.setDouble(3, s.bankMoney);
                ps.setString(4, gson.toJson(s.requireBankPermission));
                ps.executeUpdate();
            }
        }

        public static boolean saveInventoryOnly(GroupData g, StorageData storageData, String pageId, UUID playerUUID) {
            try (Connection conn = db.getConnection()) {
                InventoryData inv = storageData.storageInventory.get(pageId);
                storageData.groupUUID = g.groupUUID;
                g.storageData = storageData;
                if (inv == null) {
                    Bukkit.getLogger().warning("インベントリが空のままセーブしようとしたため失敗しました");
                    return false;
                }

                if (!inv.isFullyLoaded()) {
                    Bukkit.getLogger().warning("ロードされていないデータをセーブしようとしました");
                    g.storageData.loadPage(conn, g.ownerPlugin, pageId);
                    inv = g.storageData.storageInventory.get(pageId);
                }

                long dbPageVersion = getInventoryPageVersion(conn, g.groupUUID, g.ownerPlugin, pageId);
                if (dbPageVersion != inv.version) {
                    Bukkit.getLogger().warning("[BetterStorage]Error:InventoryDataVersion不一致:" + g.groupName);
                    Bukkit.getLogger().warning("[BetterStorage]DB上のVer:" + dbPageVersion + "保存しようとしたVer:" + inv.version);
                    return false;
                }

                saveSinglePage(conn, g, pageId, inv, playerUUID, false);
                saveGroupTable(conn, g); // group_tableは必須（versionが無くても更新してOK）

                UnifiedLogManager.saveDiffLogs(BetterStorage.BSPlugin.getDatabaseManager(), g);
                return true;
            } catch (SQLException e) {
                Bukkit.getLogger().warning("Inventory保存失敗: " + e.getMessage());
                return false;
            }
        }

    /** タグの差分同期（tag_tableを現在の集合に合わせてDELETE/INSERT）＋ diff_log_tags 追記 */
    private static void syncTagsForPage(Connection conn, GroupData g, String pageId, InventoryData inv) throws SQLException {
        // 1) 入力クレンジング＆重複排除（順序保持）
        java.util.Set<String> newTags = (inv.userTags == null) ? java.util.Collections.emptySet()
                : inv.userTags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        // 2) 既存タグを取得（現在の実データ）
        java.util.Set<String> existing = new java.util.LinkedHashSet<>();
        final String sel = "SELECT user_tag FROM tag_table WHERE group_uuid = ? AND plugin_name = ? AND page_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sel)) {
            ps.setString(1, g.groupUUID.toString());
            ps.setString(2, g.ownerPlugin);
            ps.setString(3, pageId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) existing.add(rs.getString(1));
            }
        }

        // 3) 差分計算
        java.util.Set<String> toInsert = newTags.stream().filter(t -> !existing.contains(t))
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        java.util.Set<String> toDelete = existing.stream().filter(t -> !newTags.contains(t))
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        // 4) 削除
        if (!toDelete.isEmpty()) {
            String del = "DELETE FROM tag_table WHERE group_uuid = ? AND plugin_name = ? AND page_id = ? AND user_tag = ?";
            try (PreparedStatement ps = conn.prepareStatement(del)) {
                for (String t : toDelete) {
                    ps.setString(1, g.groupUUID.toString());
                    ps.setString(2, g.ownerPlugin);
                    ps.setString(3, pageId);
                    ps.setString(4, t);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }

        // 5) 追加
        if (!toInsert.isEmpty()) {
            String ins = "INSERT INTO tag_table (group_uuid, plugin_name, page_id, user_tag) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(ins)) {
                for (String t : toInsert) {
                    ps.setString(1, g.groupUUID.toString());
                    ps.setString(2, g.ownerPlugin);
                    ps.setString(3, pageId);
                    ps.setString(4, t);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }

        // 6) メモリ上の inv.userTags も正規化済み集合に合わせて整える
        inv.userTags.clear();
        inv.userTags.addAll(newTags);

        // 7) diff_log_tags に ADD/REMOVE を記録
        UnifiedLogManager.logTagDiffsForPage(conn, g, inv, pageId);
    }

    /** タグだけ全ページ分を保存（差分同期＋diff追記） */
    public static boolean saveTagsOnly(GroupData g) {
        if (g == null || g.storageData == null) return true;
        try (Connection conn = db.getConnection()) {
            for (Map.Entry<String, InventoryData> e : g.storageData.storageInventory.entrySet()) {
                String pageId = e.getKey();
                InventoryData inv = e.getValue();
                if (inv == null) continue;
                syncTagsForPage(conn, g, pageId, inv);
            }
            return true;
        } catch (SQLException ex) {
            Bukkit.getLogger().warning("[BetterStorage] タグ保存に失敗: " + ex.getMessage());
            return false;
        }
    }

    /** タグだけ特定ページ分を保存（差分同期＋diff追記） */
    public static boolean saveTagsOnly(GroupData g, String pageId, InventoryData inventoryData) {
        if (g == null || g.storageData == null) return true;
        if (inventoryData == null) return true;
        try (Connection conn = db.getConnection()) {
            syncTagsForPage(conn, g, pageId, inventoryData);
            return true;
        } catch (SQLException ex) {
            Bukkit.getLogger().warning("[BetterStorage] タグ保存に失敗(pageId=" + pageId + "): " + ex.getMessage());
            return false;
        }
    }

    // ===========================================================
    // ===== 2. LOAD ============================================
    // ===========================================================


    /** メインエントリ：グループ名から GroupData を生成 */
    public static GroupData loadGroupData(UUID groupUUID) {
        try (Connection conn = db.getConnection()) {
            String groupName = null;
            String displayName;
            boolean isPrivate;
            String ownerPlugin;

            // --- group_table ------------------------------------
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT group_name, display_name, is_private, owner_plugin FROM group_table WHERE group_uuid = ?")) {
                ps.setString(1, groupUUID.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    groupName = rs.getString("group_name");
                    displayName = rs.getString("display_name");
                    isPrivate = rs.getBoolean("is_private");
                    ownerPlugin = rs.getString("owner_plugin");
                }
            }

            // --- group_member_table -----------------------------
            Map<UUID, List<String>> tempRoles = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT member_uuid, role FROM group_member_table WHERE group_uuid = ?")) {
                ps.setString(1, groupUUID.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("member_uuid"));
                        String role = rs.getString("role");
                        tempRoles.computeIfAbsent(uuid, k -> new ArrayList<>()).add(role);
                    }
                }
            }

            // GroupMemberData に変換
            List<GroupMemberData> memberList = tempRoles.entrySet().stream()
                    .map(e -> new GroupMemberData(e.getKey(), e.getValue().toArray(new String[0])))
                    .collect(Collectors.toList());

            // --- storage + inventory + tags ----------------------
            StorageData storage = loadStorageData(conn, groupUUID);

            // GroupDataを生成（versionは不要）
            return new GroupData(groupName, displayName, memberList, isPrivate, storage, ownerPlugin, groupUUID);

        } catch (SQLException e) {
            Bukkit.getLogger().warning("GroupData の読み込みに失敗: " + e.getMessage());
            return null;
        }
    }

    //グループ名から取得する
    public static GroupData loadGroupData(String groupName) {
        try (Connection conn = db.getConnection()) {
            UUID groupUUID = getGroupUUIDFromName(groupName);
            if (groupUUID == null) {
                Bukkit.getLogger().warning("group_name '" + groupName + "' に対応する group_uuid が見つかりませんでした");
                return null;
            }
            return loadGroupData(groupUUID); // 既存のUUID版を再利用
        } catch (SQLException e) {
            Bukkit.getLogger().warning("GroupUUIDの取得に失敗: " + e.getMessage());
            return null;
        }
    }

    //GroupNameからGroupUUID生成
    public static @Nullable UUID getGroupUUIDFromName(String input) {
        try (Connection conn = BetterStorage.BSPlugin.getDatabaseManager().getConnection()) {
            String sql = "SELECT group_uuid FROM group_table WHERE group_name = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, input);
                Bukkit.getLogger().info("🔍 group_name lookup: '" + input + "'");
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return UUID.fromString(rs.getString("group_uuid"));
                    }
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().warning("グループUUIDの取得中にエラー: " + e.getMessage());
        }
        return null;
    }

    /** storage_table を読み込み、InventoryData 群を組み立てる */
    private static StorageData loadStorageData(Connection conn, UUID groupUUID) throws SQLException {
        String pluginName;
        double bankMoney = 0;
        Set<String> requireBankPerm = new HashSet<>();

        try (PreparedStatement ps = conn.prepareStatement("SELECT plugin_name, bank_money, require_bank_permission FROM storage_table WHERE group_uuid = ?")) {
            ps.setString(1, groupUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                pluginName = rs.getString("plugin_name");
                bankMoney = rs.getDouble("bank_money");
                requireBankPerm = gson.fromJson(rs.getString("require_bank_permission"), new TypeToken<Set<String>>(){}.getType());
            }
        }

        Map<String, InventoryData> invMap = loadInventoryData(conn, groupUUID, pluginName);
        return new StorageData(requireBankPerm, invMap, bankMoney);
    }

    /** inventory_table + items + tags を読み込み */
    private static Map<String, InventoryData> loadInventoryData(Connection conn, UUID groupUUID, String pluginName) throws SQLException {
        Map<String, InventoryData> map = new HashMap<>();
        String sql = "SELECT page_id, display_name, row_count, require_permission, version FROM inventory_table WHERE group_uuid = ? AND plugin_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupUUID.toString());
            ps.setString(2, pluginName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String pageId = rs.getString("page_id");
                    String display = rs.getString("display_name");
                    int rows = rs.getInt("row_count");
                    Set<String> reqPerm = gson.fromJson(rs.getString("require_permission"), new TypeToken<Set<String>>(){}.getType());
                    long version = rs.getLong("version"); // ← 追加！

                    Map<Integer, ItemStack> slotMap = loadInventoryItems(conn, groupUUID, pluginName, pageId);

                    InventoryData inv = new InventoryData(display, rows, reqPerm, slotMap);
                    inv.version = version; // ← version設定！
                    map.put(pageId, inv);
                }
            }
        }
        loadTags(conn, groupUUID, pluginName, map);
        return map;
    }


    /**
     *
     * @param conn
     * @param groupUUID
     * @return
     * @throws SQLException
     * GUI表示用にInventoryDataのメタデータのみ取得する 全取得より軽量!
     */
    private static StorageData loadStorageMetaOnly(Connection conn, UUID groupUUID) throws SQLException {
        String pluginName = null;
        double bankMoney = 0;
        Set<String> requireBankPerm = new HashSet<>();

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT plugin_name, bank_money, require_bank_permission FROM storage_table WHERE group_uuid = ?")) {
            ps.setString(1, groupUUID.toString());

            //Bukkit.getLogger().info("[Debug] Executing query for group_uuid = " + groupUUID);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()){
                    Bukkit.getLogger().warning("[BetterStorage] No result found for group_uuid = " + groupUUID);
                    return null;
                }
                pluginName = rs.getString("plugin_name");
                bankMoney = rs.getDouble("bank_money");
                String requireBankPermRaw = rs.getString("require_bank_permission");
                requireBankPerm = gson.fromJson(rs.getString("require_bank_permission"), new TypeToken<Set<String>>() {}.getType());

                //Bukkit.getLogger().info("[Debug] Loaded: plugin=" + pluginName + ", bank=" + bankMoney + ", perms=" + requireBankPermRaw);
            }
        }

        Map<String, InventoryData> invMap = loadInventoryMetaOnly(conn, groupUUID, pluginName);
        StorageData storageData = new StorageData(requireBankPerm, invMap, bankMoney);
        storageData.groupData = loadGroupData(groupUUID);
        storageData.groupUUID = groupUUID;
        if(storageData.groupData == null){
            Bukkit.getLogger().warning("StorageMetaのGroupDataNull");
        }else {
            Bukkit.getLogger().warning("StorageMetaのGroupData有効!");
        }


        storageData.setFullyLoaded(false);
        return storageData;
    }
    //外部呼出し用
    public static StorageData loadStorageMetaOnly(UUID groupUUID) {
        try (Connection conn = db.getConnection()) {
            //Bukkit.getLogger().info("[Debug] loadStorageMetaOnly: groupUUID = " + groupUUID);
            return loadStorageMetaOnly(conn, groupUUID);
        } catch (SQLException e) {
            Bukkit.getLogger().warning("StorageMetaの読み込みに失敗: " + e.getMessage());
            return null;
        }
    }

    //指定したgroupのdb状にあるインベントリデータを持ってくるメソッド
    public static InventoryData getLatestInventoryData(UUID groupUUID, String pageId, String pluginName) {
        try (Connection conn = db.getConnection()) {
            // InventoryDataをDBから取得
            Map<String, InventoryData> inventoryDataMap = loadInventoryData(conn, groupUUID, pluginName);

            // 指定されたpageIdに対応するInventoryDataを取得
            InventoryData invData = inventoryDataMap.get(pageId);

            if (invData != null) {
                // データが見つかればそのまま返す
                //Bukkit.getLogger().info("Loaded latest inventory data for pageId: " + pageId);
                return invData;
            } else {
                // pageIdが見つからない場合
                Bukkit.getLogger().warning("No inventory data found for pageId: " + pageId);
                return null;
            }
        } catch (SQLException e) {
            Bukkit.getLogger().warning("Failed to load latest inventory data: " + e.getMessage());
            return null;
        }
    }


    /**
     *
     * @param conn
     * @param groupUUID
     * @param pluginName
     * @return
     * @throws SQLException
     * 軽さだけが取り柄の中身がスカスカなInventoryDataたちを生成する
     */
    private static Map<String, InventoryData> loadInventoryMetaOnly(Connection conn, UUID groupUUID, String pluginName) throws SQLException {
        Map<String, InventoryData> map = new HashMap<>();
        String sql = "SELECT page_id, display_name, row_count, require_permission, version FROM inventory_table WHERE group_uuid = ? AND plugin_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupUUID.toString());
            ps.setString(2, pluginName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String pageId = rs.getString("page_id");
                    String display = rs.getString("display_name");
                    int rows = rs.getInt("row_count");
                    Set<String> reqPerm = gson.fromJson(rs.getString("require_permission"), new TypeToken<Set<String>>() {}.getType());
                    long version = rs.getLong("version"); // ← ここ追加！

                    InventoryData inv = new InventoryData(display, rows, reqPerm, new HashMap<>());
                    inv.version = version; // ← versionをセット！
                    inv.setFullyLoaded(false);
                    map.put(pageId, inv);
                }
            }
        }

        loadTags(conn, groupUUID, pluginName, map);
        return map;
    }
    //外部呼出し用
    public static Map<String, InventoryData> loadInventoryMetaOnly(UUID groupUUID, String pluginName) {
        try (Connection conn = db.getConnection()) {
            Map<String, InventoryData> result = loadInventoryMetaOnly(conn, groupUUID, pluginName);
            return result.isEmpty() ? null : result;
        } catch (SQLException e) {
            Bukkit.getLogger().warning("InventoryMetaの読み込みに失敗: " + e.getMessage());
            return null;
        }
    }

    /**
     *
     * @param conn
     * @param groupUUID
     * @param pluginName
     * @param inv
     * @param pageId
     * @throws SQLException
     * スカスカちゃん達に中身を吹き込む(InventoryData)
     */
    public static void loadPageItems(Connection conn, UUID groupUUID, String pluginName, InventoryData inv, String pageId) throws SQLException {
        String sql = "SELECT slot, itemstack FROM inventory_item_table WHERE group_uuid = ? AND plugin_name = ? AND page_id = ?";
        Map<Integer, ItemStack> slotMap = new HashMap<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupUUID.toString());
            ps.setString(2, pluginName);
            ps.setString(3, pageId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int slot = rs.getInt("slot");
                    ItemStack item = ItemSerializer.deserializeFromBase64(rs.getString("itemstack"));
                    if (item != null) {
                        slotMap.put(slot, item);
                    }
                }
            }
        }

        inv.itemStackSlot = slotMap;
        inv.setFullyLoaded(true);
    }

    /** inventory_item_table からアイテムを読み込み */
    private static Map<Integer, ItemStack> loadInventoryItems(Connection conn, UUID groupUUID, String pluginName, String pageId) throws SQLException {
        Map<Integer, ItemStack> map = new HashMap<>();
        String sql = "SELECT slot, itemstack FROM inventory_item_table WHERE group_uuid = ? AND plugin_name = ? AND page_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupUUID.toString());
            ps.setString(2, pluginName);
            ps.setString(3, pageId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int slot = rs.getInt("slot");
                    ItemStack item = ItemSerializer.deserializeFromBase64(rs.getString("itemstack"));
                    if (item != null) {
                        map.put(slot, item);
                    }
                }
            }
        }
        return map;
    }

    /** tag_table → InventoryData.userTag へ反映 */
    private static void loadTags(Connection conn, UUID groupUUID, String pluginName, Map<String, InventoryData> map) throws SQLException {
        String sql = "SELECT page_id, user_tag FROM tag_table WHERE group_uuid = ? AND plugin_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupUUID.toString());
            ps.setString(2, pluginName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String pageId = rs.getString("page_id");
                    String userTag = rs.getString("user_tag");
                    InventoryData inv = map.get(pageId);
                    if (inv != null && userTag != null && !userTag.isEmpty()) {
                        inv.userTags.add(userTag);
                    }
                }
            }
        }
    }

    //そのデータがどのプラグインで使われているのかの確認用
    private static String loadOwnerPlugin(Connection conn, UUID groupUUID) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT plugin_name FROM storage_table WHERE group_uuid = ?")) {
            ps.setString(1, groupUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("plugin_name");
            }
        }
        return null;
    }

    /**
     * 各ページのバージョンを取得するメソッド
     * @param conn
     * @param groupUUID
     * @param pageId
     * @return
     * @throws SQLException
     */
    public static long getInventoryPageVersion(Connection conn, UUID groupUUID, String pluginName, String pageId) throws SQLException {
        String sql = "SELECT version FROM inventory_table WHERE group_uuid = ? AND plugin_name = ? AND page_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupUUID.toString());
            ps.setString(2, pluginName);
            ps.setString(3, pageId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("version") : 0;
            }
        }
    }

    /** プレイヤーが属しているすべてのGroupDataを取得（group_member_tableベース） */
    public static @NotNull List<GroupData> loadGroupsByPlayer(OfflinePlayer player) {
        List<GroupData> result = new ArrayList<>();
        UUID playerUUID = player.getUniqueId();

        try (Connection conn = db.getConnection()) {
            String sql = "SELECT DISTINCT group_uuid FROM group_member_table WHERE member_uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUUID.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID groupUUID = UUID.fromString(rs.getString("group_uuid"));
                        GroupData gd = loadGroupData(groupUUID);
                        if (gd != null) {
                            result.add(gd);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().warning("プレイヤー所属グループ取得に失敗: " + e.getMessage());
        }

        return result;
    }

    // ===========================================================
    // ===== 3. DELETE ==========================================
    // ===========================================================
    /** ページ単位削除 */
    private static void deletePageData(Connection conn, UUID groupUUID, String pluginName, String pageId, String executedBy) throws SQLException {

        // 差分ログを削除前に保存
        GroupData group = loadGroupData(groupUUID);
        if (group != null) {
            UnifiedLogManager.saveBackupSnapshot(group);
        }

        String[] sqls = {
                "DELETE FROM inventory_item_table WHERE group_uuid=? AND plugin_name=? AND page_id=?",
                "DELETE FROM inventory_table WHERE group_uuid=? AND plugin_name=? AND page_id=?",
                "DELETE FROM tag_table WHERE group_uuid=? AND plugin_name=? AND page_id=?"
        };

        for (String sql : sqls) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, groupUUID.toString());
                ps.setString(2, pluginName);
                ps.setString(3, pageId);
                ps.executeUpdate();
            }
        }

        logDeletionInfo("Page \"" + pageId + "\" in GroupUUID \"" + groupUUID + "\" was deleted by " + executedBy);
    }

    private static void deleteGroupData(Connection conn, UUID groupUUID, String pluginName, String groupName, String executedBy) throws SQLException {
        // 差分ログ保存＆削除履歴＋バックアップも保存
        GroupData group = loadGroupData(groupUUID);
        if (group != null) {
            UnifiedLogManager.saveDiffLogs(BetterStorage.BSPlugin.getDatabaseManager(), group);
            UnifiedLogManager.saveDeleteHistory(group, executedBy); // ✅ ここで履歴＋バックアップ保存
        }

        // 各テーブルから削除
        String[] sqls = {
                "DELETE FROM inventory_item_table WHERE group_uuid=? AND plugin_name=?",
                "DELETE FROM inventory_table WHERE group_uuid=? AND plugin_name=?",
                "DELETE FROM tag_table WHERE group_uuid=? AND plugin_name=?",
                "DELETE FROM storage_table WHERE group_uuid=? AND plugin_name=?",
                "DELETE FROM group_member_table WHERE group_uuid=?",
                "DELETE FROM group_table WHERE group_uuid=?"
        };

        for (String sql : sqls) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, groupUUID.toString());
                if (sql.contains("plugin_name")) {
                    ps.setString(2, pluginName);
                }
                ps.executeUpdate();
            }
        }

        logDeletionInfo("Group \"" + groupName + "\" (UUID=" + groupUUID + ") was deleted by " + executedBy);
    }

    public static boolean deletePageData(UUID groupUUID, String pluginName, String pageId, String executedBy) {
        try (Connection conn = BetterStorage.BSPlugin.getDatabaseManager().getConnection()) {
            deletePageData(conn, groupUUID, pluginName, pageId, executedBy);
        } catch (SQLException e) {
            Bukkit.getLogger().warning("[BetterStorage] ページ削除中にエラー: " + e.getMessage());
            return false;
        }
        return true;
    }

    public static boolean deleteGroupData(UUID groupUUID, String pluginName, String groupName, String executedBy) {
        try (Connection conn = BetterStorage.BSPlugin.getDatabaseManager().getConnection()) {
            deleteGroupData(conn, groupUUID, pluginName, groupName, executedBy);
        } catch (SQLException e) {
            Bukkit.getLogger().warning("[BetterStorage] グループ削除中にエラー: " + e.getMessage());
            return false;
        }
        return true;
    }





    // ===========================================================
    // ===== 4. LOG =============================================
    // ===========================================================

    public static void logInventoryItemChangeAsync(DatabaseManager db, UUID groupUUID, String pluginName, String pageId, int slot, OperationType op, ItemStack item, UUID playerUUID) {
        boolean isRemove = (op == OperationType.REMOVE);
        if (item == null || (!isRemove && item.getType() == Material.AIR)) return;

        String serializedItem = ItemSerializer.serializeToBase64(item);
        String displayName = item.hasItemMeta() && item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : "";
        String material = item.getType().name();
        int amount = item.getAmount();
        String plainName = ChatColor.stripColor(displayName);

        Bukkit.getScheduler().runTaskAsynchronously(BetterStorage.BSPlugin, () -> {
            try (Connection conn = db.getConnection()) {
                String sql = "INSERT INTO inventory_item_log " +
                        "(group_uuid, plugin_name, page_id, slot, operation_type, itemstack, display_name, display_name_plain, material, amount, player_uuid, timestamp) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?,NOW())";

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, groupUUID.toString());
                    ps.setString(2, pluginName);
                    ps.setString(3, pageId);
                    ps.setInt(4, slot);
                    ps.setString(5, op.toDbString());
                    ps.setString(6, serializedItem);
                    ps.setString(7, displayName);
                    ps.setString(8, plainName);
                    ps.setString(9, material);
                    ps.setInt(10, amount);
                    ps.setString(11, playerUUID != null ? playerUUID.toString() : null); // null安全
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                Bukkit.getLogger().warning("[BetterStorage] 非同期ログ保存失敗: " + e.getMessage());
            }
        });
    }


    private static void logInventoryItemChange(Connection conn, UUID groupUUID, String pluginName, String pageId, int slot, String op, ItemStack item) throws SQLException {
        String displayName = item.hasItemMeta() && item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : "";
        String plainName = ChatColor.stripColor(displayName);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO inventory_item_log (group_uuid, plugin_name, page_id, slot, operation_type, itemstack, display_name, display_name_plain, material, amount, timestamp) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,NOW())")) {
            ps.setString(1, groupUUID.toString());
            ps.setString(2, pluginName);
            ps.setString(3, pageId);
            ps.setInt(4, slot);
            ps.setString(5, op);
            ps.setString(6, ItemSerializer.serializeToBase64(item));
            ps.setString(7, displayName);
            ps.setString(8, plainName);
            ps.setString(9, item.getType().name());
            ps.setInt(10, item.getAmount());
            ps.executeUpdate();
        }

    }

    public static void restoreInventoryState(Connection conn, UUID groupUUID, String pluginName, String pageId, LocalDateTime before) throws SQLException {
        String sql = "SELECT slot, itemstack FROM inventory_item_log WHERE group_uuid=? AND plugin_name=? AND page_id=? AND timestamp<=? ORDER BY timestamp DESC";
        Map<Integer, ItemStack> latest = new HashMap<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupUUID.toString());
            ps.setString(2, pluginName);
            ps.setString(3, pageId);
            ps.setTimestamp(4, Timestamp.valueOf(before));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int slot = rs.getInt("slot");
                    if (latest.containsKey(slot)) continue;
                    latest.put(slot, ItemSerializer.deserializeFromBase64(rs.getString("itemstack")));
                }
            }
        }

        // GroupManager → resolveByUUID に置き換え
        GroupData group = GroupData.resolveByUUID(groupUUID);
        if (group != null && group.storageData != null) {
            InventoryData inv = group.storageData.storageInventory.get(pageId);
            if (inv != null) {
                inv.itemStackSlot.clear();
                inv.itemStackSlot.putAll(latest);
                inv.setFullyLoaded(true); // 忘れずに

                // ✅ ログ追加（ファイル + コンソール）
                String msg = String.format("Restored inventory: groupUUID=%s, plugin=%s, pageId=%s, before=%s, restoredSlotCount=%d",
                        groupUUID, pluginName, pageId, before.toString(), latest.size());
                logDeletionInfo(msg);
            }
        }
    }


    private static void writeLogFile(String msg) {
        try (FileWriter w = new FileWriter(new File(BetterStorage.BSPlugin.getDataFolder(), "BetterStorage.log"), true)) {
            w.write("[" + LocalDateTime.now() + "] " + msg + "\n");
        } catch (IOException e) {
            Bukkit.getLogger().warning("log write fail: " + e.getMessage());
        }
    }

    private static void logDeletionInfo(String msg) {
        Bukkit.getLogger().info("[BetterStorage] " + msg);
        writeLogFile(msg);
    }

    // ===========================================================
    // ===== 5. SEARCH ==========================================
    // ===========================================================

    // タグによるページ検索
    public static List<InventoryData> searchPagesByTagWithDisplay(Connection conn, UUID groupUUID, String plugin, String keyword) throws SQLException {
        List<InventoryData> result = new ArrayList<>();
        String sql = "SELECT DISTINCT page_id FROM tag_table WHERE group_uuid=? AND plugin_name=? AND user_tag LIKE ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupUUID.toString());
            ps.setString(2, plugin);
            ps.setString(3, "%" + keyword + "%");
            try (ResultSet rs = ps.executeQuery()) {
                GroupData group = GroupData.resolveByUUID(groupUUID); // ← 修正点
                if (group != null && group.storageData != null) {
                    while (rs.next()) {
                        String pageId = rs.getString("page_id");
                        InventoryData inv = group.storageData.storageInventory.get(pageId);
                        if (inv != null) {
                            result.add(inv);
                        }
                    }
                }
            }
        }
        return result;
    }


    // アイテムのDisplayNameによるページ検索
    public static List<InventoryData> getPagesContainingDisplayNameWithDisplay(Connection conn, UUID groupUUID, String plugin, String keyword) throws SQLException {
        List<InventoryData> result = new ArrayList<>();
        String sql = "SELECT DISTINCT page_id FROM inventory_item_table WHERE group_uuid=? AND plugin_name=? AND display_name LIKE ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupUUID.toString());
            ps.setString(2, plugin);
            ps.setString(3, "%" + keyword + "%");
            try (ResultSet rs = ps.executeQuery()) {
                GroupData group = GroupData.resolveByUUID(groupUUID);
                if (group != null && group.storageData != null) {
                    while (rs.next()) {
                        String pageId = rs.getString("page_id");
                        InventoryData inv = group.storageData.storageInventory.get(pageId);
                        if (inv != null) {
                            result.add(inv);
                        }
                    }
                }
            }
        }
        return result;
    }


    // ===========================================================
    // ===== 6. UTIL ============================================
    // ===========================================================

    /** group_table から全グループを取得 */
    public static List<GroupData> loadAllGroups() {
        List<GroupData> list = new ArrayList<>();
        String sql = "SELECT group_uuid FROM group_table";
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                UUID groupUUID = UUID.fromString(rs.getString("group_uuid"));
                GroupData data = loadGroupData(groupUUID);
                if (data != null) list.add(data);
            }
        } catch (SQLException e) {
            Bukkit.getLogger().warning("allGroups load fail: " + e.getMessage());
        }
        return list;
    }
}
