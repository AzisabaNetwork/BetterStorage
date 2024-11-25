package dev.felnull.Data;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GroupData {
    public final String groupName; //グループ名　プレイヤー個人の場合はプレイヤーUUID
    public Set<Player> playerList; //グループ所属のプレイヤーリスト 最低１つは格納されるはず
    public Map<Player,String[]> playerPermission; //プレイヤーが保持している役職
    public boolean isPrivate; //個人用グループか否か
    public StorageData storageData; //グループ保有のストレージデータ null許容

    public GroupData (@NotNull String groupName,@NotNull Set<Player> playerList,@NotNull Map<Player,String[]> playerPermission, boolean isPrivate, StorageData storageData) {
        this.groupName = groupName;
        this.playerList = playerList;
        this.playerPermission = playerPermission;
        this.isPrivate = isPrivate;
        storageData.groupName = groupName;
        storageData.groupData = this;
        this.storageData = storageData;
    }

    public GroupData (@NotNull String groupName,@NotNull Player player, StorageData storageData) {
        this.groupName = groupName;

        //引数で得たプレイヤーをメンバに追加してowner権限を付与する
        playerList = new HashSet<>();
        playerPermission = new HashMap<>();
        playerList.add(player);
        String[] permission = {GroupPermENUM.OWNER.getPermName()};
        playerPermission.put(player, permission );
        //個人用を想定したコンストラクタなのでtrue
        this.isPrivate = true;

        storageData.groupName = groupName;
        storageData.groupData = this;
        this.storageData = storageData;
    }

}
