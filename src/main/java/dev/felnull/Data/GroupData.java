package dev.felnull.Data;

import dev.felnull.DataIO.DataIO;
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

    /** groupName → UUID */
    public static @Nullable UUID resolveUUID(String groupName) {
        GroupData data = DataIO.loadGroupData(groupName);
        return data != null ? data.groupUUID : null;
    }

    /** groupUUID → groupName */
    public static @Nullable String resolveName(UUID groupUUID) {
        GroupData data = DataIO.loadGroupData(groupUUID);
        return data != null ? data.groupName : null;
    }

    /** groupUUID → GroupData */
    public static @Nullable GroupData resolveByUUID(UUID groupUUID) {
        return DataIO.loadGroupData(groupUUID);
    }

    /** groupName → GroupData */
    public static @Nullable GroupData resolveByName(String groupName) {
        return DataIO.loadGroupData(groupName);
    }

    /**
     * 指定されたプレイヤーが所属している全 GroupData を返すラッパー（DataIOベース）。
     */
    public static List<GroupData> getGroupsOfPlayer(OfflinePlayer player) {
        return DataIO.loadGroupsByPlayer(player);
    }

    public GroupData deepClone(StorageData storageData) {
        // ここではdeepClone時に groupData は引数で渡される
        Set<OfflinePlayer> clonedPlayerList = new HashSet<>(this.playerList);
        Map<OfflinePlayer, String[]> clonedPlayerPermission = new HashMap<>(this.playerPermission);

        return new GroupData(this.groupName, this.displayName, clonedPlayerList, clonedPlayerPermission, this.isPrivate, storageData, this.ownerPlugin, this.groupUUID);
    }


}