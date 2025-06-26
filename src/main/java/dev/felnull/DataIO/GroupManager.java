package dev.felnull.DataIO;

import dev.felnull.BetterStorage;
import dev.felnull.Data.GroupData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GroupManager {
    /** groupUUID から GroupData を参照するマップ */
    private static final Map<UUID, GroupData> uuidMap = new ConcurrentHashMap<>();
    /** groupName（表示用／識別用）から GroupData を参照するマップ */
    private static final Map<String, GroupData> nameMap = new ConcurrentHashMap<>();

    // グループを登録（UUID と groupName、両方でアクセス可能にする）
    public static void registerGroup(GroupData data) {
        uuidMap.put(data.groupUUID, data);
        nameMap.put(data.groupName, data);
    }

    // UUID でグループを取得
    public static GroupData getGroupByUUID(UUID groupUUID) {
        return uuidMap.get(groupUUID);
    }

    // groupName でグループを取得
    public static GroupData getGroupByName(String groupName) {
        return nameMap.get(groupName);
    }

    // UUID でグループを削除
    public static void unregisterGroupByUUID(UUID groupUUID) {
        GroupData removed = uuidMap.remove(groupUUID);
        if (removed != null) {
            nameMap.remove(removed.groupName);
        }
    }

    // groupName でグループを削除
    public static void unregisterGroupByName(String groupName) {
        GroupData removed = nameMap.remove(groupName);
        if (removed != null) {
            uuidMap.remove(removed.groupUUID);
        }
    }

    // 全グループ取得（UUID マップの値を返す）
    public static Collection<GroupData> getAllGroups() {
        return Collections.unmodifiableCollection(uuidMap.values());
    }

    // UUID ベースで登録済みか
    public static boolean isRegisteredByUUID(UUID groupUUID) {
        return uuidMap.containsKey(groupUUID);
    }

    // groupName ベースで登録済みか
    public static boolean isRegisteredByName(String groupName) {
        return nameMap.containsKey(groupName);
    }

    // 全削除（再読み込み用など）
    public static void clear() {
        uuidMap.clear();
        nameMap.clear();
    }

    // — 便利ユーティリティ —

    /** groupName から UUID を返す（キャッシュがなければ 取得） */
    public static UUID resolveUUID(String groupName) {
        // 1. キャッシュ確認
        GroupData gd = nameMap.get(groupName);
        if (gd != null) return gd.groupUUID;

        // 2. DBフォールバック（キャッシュにない場合はDBから取得）
        try (Connection conn = BetterStorage.BSPlugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT group_uuid FROM group_table WHERE group_name = ?")) {
            ps.setString(1, groupName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String uuidStr = rs.getString("group_uuid");
                    UUID uuid = UUID.fromString(uuidStr);

                    // （オプション）GroupData自体の再構築・登録はここでは行わない
                    return uuid;
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().warning("[BetterStorage] resolveUUIDのDBフォールバックに失敗: " + e.getMessage());
        }

        return null;
    }

    /** UUID から groupName を返す（キャッシュがなければ null） */
    public static String resolveName(UUID groupUUID) {
        GroupData gd = uuidMap.get(groupUUID);
        return (gd != null ? gd.groupName : null);
    }

    /** プレイヤー名から所属しているグループ一覧（groupName）を取得 */
    public static List<String> getGroupsForPlayer(OfflinePlayer player) {
        return uuidMap.values().stream()
                .filter(gd -> gd.playerList.contains(player))
                .map(gd -> gd.groupName)
                .collect(Collectors.toList());
    }
    /** 全グループ名を取得（表示や補完用） */
    public static List<String> getAllGroupNames() {
        return new ArrayList<>(nameMap.keySet());
    }

    /**個人向けグループデータを取得**/
    public static @Nullable GroupData getPersonalGroup(UUID playerUUID) {
        return getAllGroups().stream()
                .filter(g -> g.isPrivate && g.groupName.equals(playerUUID.toString()))
                .findFirst()
                .orElse(null);
    }

}