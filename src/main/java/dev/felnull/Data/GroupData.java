package dev.felnull.Data;

import dev.felnull.DataIO.DataIO;
import dev.felnull.DataIO.GroupManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class GroupData {
    public final String groupName; // グループ名（表示用・識別用）
    public final UUID groupUUID;   // グループ固有のUUID（内部識別子）
    public String displayName;
    public List<GroupMemberData> playerList; // 所属プレイヤーと役職
    public boolean isPrivate; // 個人グループか
    public StorageData storageData; // ストレージ情報
    public String ownerPlugin;

    // コンストラクタ（新規作成）
    public GroupData(@NotNull String groupName, @NotNull String displayName, @NotNull List<GroupMemberData> playerList,
                     boolean isPrivate, StorageData storageData, String ownerPlugin, @Nullable UUID groupUUID) {
        this.groupName = groupName;
        this.displayName = displayName;
        this.playerList = playerList;
        this.isPrivate = isPrivate;
        this.ownerPlugin = ownerPlugin;
        this.groupUUID = groupUUID != null ? groupUUID : UUID.randomUUID();

        this.storageData = storageData;
        if (this.storageData != null) {
            this.storageData.groupUUID = this.groupUUID;
            this.storageData.groupData = this;
        }
    }

    // 個人用グループ作成
    public GroupData(@NotNull OfflinePlayer player, StorageData storageData, String ownerPlugin) {
        this.groupName = player.getUniqueId().toString();
        this.groupUUID = UUID.randomUUID();
        this.displayName = player.getName();
        this.isPrivate = true;
        this.ownerPlugin = ownerPlugin;
        this.storageData = storageData;

        this.playerList = new ArrayList<>();
        this.playerList.add(new GroupMemberData(player.getUniqueId(), new String[]{ GroupPermENUM.OWNER.getPermName() }));

        if (this.storageData != null) {
            this.storageData.groupUUID = this.groupUUID;
            this.storageData.groupData = this;
        }
    }

    // ===============================
    // === GroupManagerラッパー ===
    // ===============================

    public static @Nullable UUID resolveUUID(String groupName) {
        GroupData data = DataIO.loadGroupData(groupName);
        return data != null ? data.groupUUID : null;
    }

    public static @Nullable String resolveName(UUID groupUUID) {
        GroupData data = DataIO.loadGroupData(groupUUID);
        return data != null ? data.groupName : null;
    }

    public static @Nullable GroupData resolveByUUID(UUID groupUUID) {
        return DataIO.loadGroupData(groupUUID);
    }

    public static @Nullable GroupData resolveByName(String groupName) {
        return DataIO.loadGroupData(groupName);
    }

    public static List<GroupData> getGroupsOfPlayer(OfflinePlayer player) {
        return DataIO.loadGroupsByPlayer(player);
    }

    public GroupData deepClone(StorageData storageData) {
        List<GroupMemberData> clonedList = this.playerList.stream()
                .map(gm -> new GroupMemberData(gm.memberUUID, gm.role.clone()))
                .collect(Collectors.toList());

        return new GroupData(this.groupName, this.displayName, clonedList, this.isPrivate, storageData, this.ownerPlugin, this.groupUUID);
    }

    /**
     * 指定UUIDのプレイヤーの役職を取得（存在しない場合 null）
     */
    public @Nullable String[] getRoles(UUID playerUUID) {
        for (GroupMemberData member : playerList) {
            if (member.memberUUID.equals(playerUUID)) {
                return member.role;
            }
        }
        return null;
    }

    /**
     * 指定UUIDのプレイヤーがメンバーに含まれているか
     */
    public boolean contains(UUID playerUUID) {
        return playerList.stream().anyMatch(m -> m.memberUUID.equals(playerUUID));
    }

    /**
     * OfflinePlayerのリストを取得（必要な場合のみ）
     */
    public Set<OfflinePlayer> getOfflinePlayerSet() {
        return playerList.stream()
                .map(m -> Bukkit.getOfflinePlayer(m.memberUUID))
                .collect(Collectors.toSet());
    }
}
