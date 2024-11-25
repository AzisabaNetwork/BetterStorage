package dev.felnull.Data;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;

public class GroupData {
    public final String groupName; //グループ名　プレイヤー個人の場合はプレイヤーUUID
    public Set<Player> playerList; //グループ所属のプレイヤーリスト 最低１つは格納されるはず
    public Map<Player,String[]> playerPermission; //プレイヤーが保持している役職
    public boolean isPrivate; //個人用グループか否か
    public StorageData storageData; //グループ保有のストレージデータ null許容

    public GroupData (String groupName, Set<Player> playerList, Map<Player,String[]> playerPermission, boolean isPrivate, StorageData storageData) {
        this.groupName = groupName;
        this.playerList = playerList;
        this.playerPermission = playerPermission;
        this.isPrivate = isPrivate;
        storageData.groupName = groupName;
        storageData.groupData = this;
        this.storageData = storageData;
    }

}
