package dev.felnull.Data;

import dev.felnull.DataIO.GroupManager;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class GroupData {
    public final String groupName; // グループ名（表示用・識別用）
    public final UUID groupUUID;   // グループ固有のUUID（内部識別子）
    public String displayName;
    public Set<OfflinePlayer> playerList; // 所属プレイヤー
    public Map<OfflinePlayer, String[]> playerPermission; // 役職
    public boolean isPrivate; // 個人グループか
    public StorageData storageData; // ストレージ情報
    public String ownerPlugin;
    public long version = 0;

    // コンストラクタ（グループ新規作成用）
    public GroupData(@NotNull String groupName, @NotNull String displayName, @NotNull Set<OfflinePlayer> playerList,
                     @NotNull Map<OfflinePlayer, String[]> playerPermission, boolean isPrivate,
                     StorageData storageData, String ownerPlugin, @Nullable UUID groupUUID) {
        this.groupName = groupName;
        this.displayName = displayName;
        this.playerList = playerList;
        this.playerPermission = playerPermission;
        this.isPrivate = isPrivate;
        this.ownerPlugin = ownerPlugin;

        this.groupUUID = groupUUID != null ? groupUUID : UUID.randomUUID();

        this.storageData = storageData;
        if (this.storageData != null) {
            this.storageData.groupUUID = this.groupUUID;
            this.storageData.groupData = this;
        }
    }

    // 個人用グループ生成用（UUID自動生成）
    public GroupData(@NotNull OfflinePlayer player, StorageData storageData, String ownerPlugin) {
        this.groupName = player.getUniqueId().toString();
        this.groupUUID = UUID.randomUUID();
        this.displayName = player.getName(); // 任意で変更可能

        this.playerList = new HashSet<>();
        this.playerPermission = new HashMap<>();
        this.playerList.add(player);
        this.playerPermission.put(player, new String[]{GroupPermENUM.OWNER.getPermName()});
        this.isPrivate = true;
        this.ownerPlugin = ownerPlugin;

        this.storageData = storageData;
        if (this.storageData != null) {
            this.storageData.groupUUID = this.groupUUID;
            this.storageData.groupData = this;
        }
    }

    // ===============================
    // === GroupManagerラッパー ===
    // ===============================

    /** groupName → UUID（キャッシュから） */
    public static @Nullable UUID resolveUUID(String groupName) {
        return GroupManager.resolveUUID(groupName);
    }

    /** groupUUID → groupName（キャッシュから） */
    public static @Nullable String resolveName(UUID groupUUID) {
        return GroupManager.resolveName(groupUUID);
    }

    /** groupUUID → GroupData（キャッシュから） */
    public static @Nullable GroupData resolveByUUID(UUID groupUUID) {
        return GroupManager.getGroupByUUID(groupUUID);
    }

    /** groupName → GroupData（キャッシュから） */
    public static @Nullable GroupData resolveByName(String groupName) {
        return GroupManager.getGroupByName(groupName);
    }

    /** プレイヤーが属している全GroupDataを取得（キャッシュから） */
    public static @NotNull List<GroupData> resolveByPlayer(OfflinePlayer player) {
        List<String> groupNames = GroupManager.getGroupsForPlayer(player);
        List<GroupData> result = new ArrayList<>();
        for (String name : groupNames) {
            GroupData gd = resolveByName(name);
            if (gd != null) {
                result.add(gd);
            }
        }
        return result;
    }
}