package dev.felnull.DataIO;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.felnull.BetterStorage;
import dev.felnull.Data.GroupData;
import dev.felnull.Data.InventoryData;
import dev.felnull.Data.StorageData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class DataIO {
    private static final Gson gson = new Gson();

    // ===========================================================
    // ===== 1. SAVE ============================================
    // ===========================================================

    /** グループ全体を保存（各テーブルへの分割保存） */
    public static boolean saveGroupData(DatabaseManager db, GroupData g, long clientVersion) {
        try (Connection conn = db.getConnection()) {
            // 最新version取得
            String selectSql = "SELECT version FROM group_table WHERE group_name = ?";
            long dbVersion = 0;
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setString(1, g.groupName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) dbVersion = rs.getLong("version");
                }
            }
            // バージョン不一致なら競合
            if (dbVersion != clientVersion) {
                return false;
            }

            // versionを+1
            g.version = dbVersion + 1;

            // ---- ここから従来の保存処理 ----
            saveGroupTable(conn, g);          // versionも更新すること！
            saveGroupMembers(conn, g);
            if (g.storageData != null) {
                saveStorageData(conn, g);
                saveInventoryData(conn, g);
                saveInventoryItems(conn, g);
                saveTags(conn, g);
            }
            // ---- ここまで ----

            return true;
        } catch (SQLException e) {
            Bukkit.getLogger().warning("GroupDataの保存に失敗: " + e.getMessage());
            return false;
        }
    }

    // ---------- SAVE / group ----------
    private static void saveGroupTable(Connection conn, GroupData g) throws SQLException {
        String sql = "REPLACE INTO group_table (group_name, display_name, is_private, owner_plugin, version) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, g.groupName);
            ps.setString(2, g.displayName); // 新規
            ps.setBoolean(3, g.isPrivate);
            ps.setString(4, g.ownerPlugin);
            ps.setLong(5, g.version);
            ps.executeUpdate();
        }
    }

    private static void saveGroupMembers(Connection conn, GroupData g) throws SQLException {
        String sql = "REPLACE INTO group_member_table (group_uuid, member_uuid, role) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (OfflinePlayer member : g.playerList) {
                String[] roles = g.playerPermission.get(member);
                if (roles != null) {
                    for (String role : roles) {
                        ps.setString(1, g.groupName);
                        ps.setString(2, member.getUniqueId().toString());
                        ps.setString(3, role);
                        ps.addBatch();
                    }
                }
            }
            ps.executeBatch();
        }
    }

    // ---------- SAVE / storage ----------
    private static void saveStorageData(Connection conn, GroupData g) throws SQLException {
        StorageData s = g.storageData;
        String sql = "REPLACE INTO storage_table (group_name, plugin_name, bank_money, require_bank_permission) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, g.groupName);
            ps.setString(2, g.ownerPlugin);
            ps.setDouble(3, s.bankMoney);
            ps.setString(4, gson.toJson(s.requireBankPermission));
            ps.executeUpdate();
        }
    }

    private static void saveInventoryData(Connection conn, GroupData g) throws SQLException {
        String sql = "REPLACE INTO inventory_table (group_name, plugin_name, page_id, display_name, row_count, require_permission) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map.Entry<String, InventoryData> entry : g.storageData.storageInventory.entrySet()) {
                InventoryData inv = entry.getValue();
                ps.setString(1, g.groupName);
                ps.setString(2, g.ownerPlugin);
                ps.setString(3, entry.getKey());
                ps.setString(4, inv.displayName);
                ps.setInt(5, inv.rows);
                ps.setString(6, gson.toJson(inv.requirePermission));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void saveInventoryItems(Connection conn, GroupData g) throws SQLException {
        String sql = "REPLACE INTO inventory_item_table (group_name, plugin_name, page_id, slot, itemstack, display_name, material, amount) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map.Entry<String, InventoryData> entry : g.storageData.storageInventory.entrySet()) {
                String pageId = entry.getKey();
                InventoryData inv = entry.getValue();
                for (Map.Entry<Integer, ItemStack> itemEntry : inv.itemStackSlot.entrySet()) {
                    ItemStack item = itemEntry.getValue();
                    ps.setString(1, g.groupName);
                    ps.setString(2, g.ownerPlugin);
                    ps.setString(3, pageId);
                    ps.setInt(4, itemEntry.getKey());
                    ps.setString(5, ItemSerializer.serializeToBase64(item));
                    ps.setString(6, item.hasItemMeta() ? item.getItemMeta().getDisplayName() : "");
                    ps.setString(7, item.getType().name());
                    ps.setInt(8, item.getAmount());
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }
    }

    public static boolean saveInventoryOnly(DatabaseManager db, GroupData g, String pageId) {
        try (Connection conn = db.getConnection()) {
            InventoryData inv = g.storageData.storageInventory.get(pageId);
            if (inv == null) return false;

            // version管理（オプション）
            long dbVersion = getGroupVersion(conn, g.groupName);
            if (dbVersion != g.version) return false;
            g.version = dbVersion + 1;

            // inventory_table
            String invSql = "REPLACE INTO inventory_table (group_name, plugin_name, page_id, display_name, row_count, require_permission) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(invSql)) {
                ps.setString(1, g.groupName);
                ps.setString(2, g.ownerPlugin);
                ps.setString(3, pageId);
                ps.setString(4, inv.displayName);
                ps.setInt(5, inv.rows);
                ps.setString(6, gson.toJson(inv.requirePermission));
                ps.executeUpdate();
            }

            // inventory_item_table
            String itemSql = "REPLACE INTO inventory_item_table (group_name, plugin_name, page_id, slot, itemstack, display_name, material, amount) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(itemSql)) {
                for (Map.Entry<Integer, ItemStack> itemEntry : inv.itemStackSlot.entrySet()) {
                    ItemStack item = itemEntry.getValue();
                    ps.setString(1, g.groupName);
                    ps.setString(2, g.ownerPlugin);
                    ps.setString(3, pageId);
                    ps.setInt(4, itemEntry.getKey());
                    ps.setString(5, ItemSerializer.serializeToBase64(item));
                    ps.setString(6, item.hasItemMeta() ? item.getItemMeta().getDisplayName() : "");
                    ps.setString(7, item.getType().name());
                    ps.setInt(8, item.getAmount());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // tag_table
            String tagSql = "REPLACE INTO tag_table (group_name, plugin_name, page_id, user_tag) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(tagSql)) {
                if (inv.userTag != null) {
                    for (String tag : inv.userTag) {
                        ps.setString(1, g.groupName);
                        ps.setString(2, g.ownerPlugin);
                        ps.setString(3, pageId);
                        ps.setString(4, tag);
                        ps.addBatch();
                    }
                }
                ps.executeBatch();
            }

            // group_table の version 更新（省略しても動くけど整合性のためにやるならやる）
            saveGroupTable(conn, g);

            return true;
        } catch (SQLException e) {
            Bukkit.getLogger().warning("Inventory保存失敗: " + e.getMessage());
            return false;
        }
    }

    public static long getGroupVersion(Connection conn, String groupName) throws SQLException {
        String sql = "SELECT version FROM group_table WHERE group_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("version");
                } else {
                    return 0; // 存在しない場合は初期値として 0 を返す
                }
            }
        }
    }

    private static void saveTags(Connection conn, GroupData g) throws SQLException {
        String sql = "REPLACE INTO tag_table (group_name, plugin_name, page_id, user_tag) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map.Entry<String, InventoryData> entry : g.storageData.storageInventory.entrySet()) {
                String pageId = entry.getKey();
                String[] tags = entry.getValue().userTag;
                if (tags != null) {
                    for (String tag : tags) {
                        ps.setString(1, g.groupName);
                        ps.setString(2, g.ownerPlugin);
                        ps.setString(3, pageId);
                        ps.setString(4, tag);
                        ps.addBatch();
                    }
                }
            }
            ps.executeBatch();
        }
    }
    // ===========================================================
    // ===== 2. LOAD ============================================
    // ===========================================================

    /** メインエントリ：グループ名から GroupData を生成 */
    public static GroupData loadGroupData(DatabaseManager db, String groupName) {
        try (Connection conn = db.getConnection()) {
            // --- group_table ------------------------------
            String displayName = null;
            boolean isPrivate;
            long version = 0;
            String ownerPlugin = null;

            // display_name, is_private, version, owner_plugin も一度に取得
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT display_name, is_private, version, owner_plugin FROM group_table WHERE group_name = ?")) {
                ps.setString(1, groupName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;  // 無ければ null
                    displayName = rs.getString("display_name");
                    isPrivate = rs.getBoolean("is_private");
                    version = rs.getLong("version");
                    ownerPlugin = rs.getString("owner_plugin");
                }
            }

            // --- group_member_table -----------------------
            Set<OfflinePlayer> playerList = new HashSet<>();
            Map<OfflinePlayer, String[]> playerPerm = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT member_uuid, role FROM group_member_table WHERE group_uuid = ?")) {
                ps.setString(1, groupName);
                try (ResultSet rs = ps.executeQuery()) {
                    Map<String, List<String>> temp = new HashMap<>();
                    while (rs.next()) {
                        temp.computeIfAbsent(rs.getString("member_uuid"), k -> new ArrayList<>()).add(rs.getString("role"));
                    }
                    for (Map.Entry<String, List<String>> e : temp.entrySet()) {
                        OfflinePlayer pl = Bukkit.getOfflinePlayer(UUID.fromString(e.getKey()));
                        playerList.add(pl);
                        playerPerm.put(pl, e.getValue().toArray(new String[0]));
                    }
                }
            }

            // --- storage & inventories --------------------
            StorageData storage = loadStorageData(conn, groupName);

            GroupData groupData = new GroupData(
                    groupName,
                    displayName,
                    playerList,
                    playerPerm,
                    isPrivate,
                    storage,
                    ownerPlugin
            );
            groupData.version = version; // versionもセット

            return groupData;
        } catch (SQLException e) {
            Bukkit.getLogger().warning("GroupData の読み込みに失敗: " + e.getMessage());
            return null;
        }
    }

    /** storage_table を読み込み、InventoryData 群を組み立てる */
    private static StorageData loadStorageData(Connection conn, String groupName) throws SQLException {
        String pluginName = null;
        double bankMoney = 0;
        Set<String> requireBankPerm = new HashSet<>();

        try (PreparedStatement ps = conn.prepareStatement("SELECT plugin_name, bank_money, require_bank_permission FROM storage_table WHERE group_name = ?")) {
            ps.setString(1, groupName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null; // storage が無い
                pluginName = rs.getString("plugin_name");
                bankMoney = rs.getDouble("bank_money");
                requireBankPerm = gson.fromJson(rs.getString("require_bank_permission"), new TypeToken<Set<String>>(){}.getType());
            }
        }
        Map<String, InventoryData> invMap = loadInventoryData(conn, groupName, pluginName);
        return new StorageData(requireBankPerm, invMap, bankMoney);
    }

    /** inventory_table + items + tags を読み込み */
    private static Map<String, InventoryData> loadInventoryData(Connection conn, String groupName, String pluginName) throws SQLException {
        Map<String, InventoryData> map = new HashMap<>();
        String sql = "SELECT page_id, display_name, row_count, require_permission FROM inventory_table WHERE group_name = ? AND plugin_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupName);
            ps.setString(2, pluginName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String pageId = rs.getString("page_id");
                    String display = rs.getString("display_name");
                    int rows = rs.getInt("row_count");
                    Set<String> reqPerm = gson.fromJson(rs.getString("require_permission"), new TypeToken<Set<String>>(){}.getType());
                    Map<Integer, ItemStack> slotMap = loadInventoryItems(conn, groupName, pluginName, pageId);
                    map.put(pageId, new InventoryData(null, display, rows, reqPerm, slotMap));
                }
            }
        }
        loadTags(conn, groupName, pluginName, map);
        return map;
    }

    /** inventory_item_table からアイテムを読み込み */
    private static Map<Integer, ItemStack> loadInventoryItems(Connection conn, String groupName, String pluginName, String pageId) throws SQLException {
        Map<Integer, ItemStack> res = new HashMap<>();
        String sql = "SELECT slot, itemstack FROM inventory_item_table WHERE group_name = ? AND plugin_name = ? AND page_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupName);
            ps.setString(2, pluginName);
            ps.setString(3, pageId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    res.put(rs.getInt("slot"), ItemSerializer.deserializeFromBase64(rs.getString("itemstack")));
                }
            }
        }
        return res;
    }

    /** tag_table → InventoryData.userTag へ反映 */
    private static void loadTags(Connection conn, String groupName, String pluginName, Map<String, InventoryData> map) throws SQLException {
        String sql = "SELECT page_id, user_tag FROM tag_table WHERE group_name = ? AND plugin_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupName);
            ps.setString(2, pluginName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    InventoryData inv = map.get(rs.getString("page_id"));
                    if (inv != null) inv.userTag = new String[]{rs.getString("user_tag")};
                }
            }
        }
    }

    private static String loadOwnerPlugin(Connection conn, String groupName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT plugin_name FROM storage_table WHERE group_name = ?")) {
            ps.setString(1, groupName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("plugin_name");
            }
        }
        return null;
    }

    // ===========================================================
    // ===== 3. DELETE ==========================================
    // ===========================================================
    /** ページ単位削除 */
    public static void deletePageData(Connection conn,String g,String p,String page,String exec) throws SQLException{
        String[] sqls={
                "DELETE FROM inventory_item_table WHERE group_name=? AND plugin_name=? AND page_id=?",
                "DELETE FROM inventory_table WHERE group_name=? AND plugin_name=? AND page_id=?",
                "DELETE FROM tag_table WHERE group_name=? AND plugin_name=? AND page_id=?"};
        for(String s:sqls){
            try(PreparedStatement ps=conn.prepareStatement(s)){
                ps.setString(1,g); ps.setString(2,p); ps.setString(3,page); ps.executeUpdate();
            }
        }
        logDelete("Page \""+page+"\" in Group \""+g+"\" was deleted by "+exec);
    }

    /** グループ単位削除 */
    public static void deleteGroupData(Connection conn,String g,String p,String exec) throws SQLException{
        String[] sqls={
                "DELETE FROM inventory_item_table WHERE group_name=? AND plugin_name=?",
                "DELETE FROM inventory_table WHERE group_name=? AND plugin_name=?",
                "DELETE FROM tag_table WHERE group_name=? AND plugin_name=?",
                "DELETE FROM storage_table WHERE group_name=? AND plugin_name=?",
                "DELETE FROM group_member_table WHERE group_uuid=?",
                "DELETE FROM group_table WHERE group_name=?"};
        for(String s:sqls){
            try(PreparedStatement ps=conn.prepareStatement(s)){
                ps.setString(1,g);
                if(s.contains("plugin_name")) ps.setString(2,p);
                ps.executeUpdate();
            }
        }
        logDelete("Group \""+g+"\" was deleted by "+exec);
    }

    // ===========================================================
    // ===== 4. LOG =============================================
    // ===========================================================

    public static void logInventoryItemChange(Connection conn,String g,String p,String page,int slot,String op,ItemStack item) throws SQLException{
        try(PreparedStatement ps=conn.prepareStatement(
                "INSERT INTO inventory_item_log (group_name,plugin_name,page_id,slot,operation_type,itemstack,display_name,material,amount,timestamp) VALUES (?,?,?,?,?,?,?,?,?,NOW())")){
            ps.setString(1,g); ps.setString(2,p); ps.setString(3,page); ps.setInt(4,slot); ps.setString(5,op);
            ps.setString(6,ItemSerializer.serializeToBase64(item));
            ps.setString(7,item.hasItemMeta()?item.getItemMeta().getDisplayName():"");
            ps.setString(8,item.getType().name()); ps.setInt(9,item.getAmount());
            ps.executeUpdate();
        }
    }

    public static void restoreInventoryState(Connection conn,String g,String p,String page,LocalDateTime before) throws SQLException{
        String sql="SELECT slot,itemstack FROM inventory_item_log WHERE group_name=? AND plugin_name=? AND page_id=? AND timestamp<=? ORDER BY timestamp DESC";
        Map<Integer,ItemStack> latest=new HashMap<>();
        try(PreparedStatement ps=conn.prepareStatement(sql)){
            ps.setString(1,g); ps.setString(2,p); ps.setString(3,page); ps.setTimestamp(4,Timestamp.valueOf(before));
            try(ResultSet rs=ps.executeQuery()){
                while(rs.next()){
                    int slot=rs.getInt("slot"); if(latest.containsKey(slot)) continue; // 最初に出たものが最新
                    latest.put(slot,ItemSerializer.deserializeFromBase64(rs.getString("itemstack")));
                }
            }
        }
        GroupData data= GroupManager.getGroup(g);
        if(data!=null){
            InventoryData inv=data.storageData.storageInventory.get(page);
            inv.itemStackSlot.clear();
            inv.itemStackSlot.putAll(latest);
        }
    }

    private static void writeLogFile(String msg){
        try(FileWriter w=new FileWriter(new File(BetterStorage.BSPlugin.getDataFolder(),"BetterStorage.log"),true)){
            w.write("["+LocalDateTime.now()+"] "+msg+"\n");
        }catch(IOException e){ Bukkit.getLogger().warning("log write fail: "+e.getMessage()); }
    }

    private static void logDelete(String msg){
        Bukkit.getLogger().info("[BetterStorage] "+msg);
        writeLogFile(msg);
    }

    // ===========================================================
    // ===== 5. SEARCH ==========================================
    // ===========================================================

    public static List<String> searchPagesByTag(Connection conn, String g, String p, String kw) throws SQLException {
        List<String> res = new ArrayList<>();
        String sql = "SELECT DISTINCT page_id FROM tag_table WHERE group_name=? AND plugin_name=? AND user_tag LIKE ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, g);
            ps.setString(2, p);
            ps.setString(3, "%" + kw + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    res.add(rs.getString("page_id"));
                }
            }
        }
        return res;
    }

    public static List<String> getPagesContainingDisplayName(Connection conn, String g, String p, String kw) throws SQLException {
        List<String> res = new ArrayList<>();
        String sql = "SELECT DISTINCT page_id FROM inventory_item_table WHERE group_name=? AND plugin_name=? AND display_name LIKE ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, g);
            ps.setString(2, p);
            ps.setString(3, "%" + kw + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    res.add(rs.getString("page_id"));
                }
            }
        }
        return res;
    }

    // ===========================================================
    // ===== 6. UTIL ============================================
    // ===========================================================

    /** group_table から全グループを取得 */
    public static List<GroupData> loadAllGroups(DatabaseManager db){
        List<GroupData> list=new ArrayList<>();
        try(Connection conn=db.getConnection(); Statement st=conn.createStatement(); ResultSet rs=st.executeQuery("SELECT group_name FROM group_table")){
            while(rs.next()){
                GroupData data=loadGroupData(db,rs.getString(1));
                if(data!=null) list.add(data);
            }
        }catch(SQLException e){ Bukkit.getLogger().warning("allGroups load fail: "+e.getMessage()); }
        return list;
    }
}
