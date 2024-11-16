package dev.felnull.Data;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;

public class GroupData {
    public final String groupName; //グループ名　プレイヤー個人の場合はプレイヤーUUID
    public Set<Player> playerList; //グループ所属のプレイヤーリスト 最低１つは格納されるはず
    public Map<Player,String[]> playerPermission; //プレイヤーが保持している役職 //StringじゃなくてENUMでやるべきかもです
    public GroupData (String groupName, Set<Player> playerList, Map<Player,String[]> playerPermission) {
        this.groupName = groupName;
        this.playerList = playerList;
        this.playerPermission = playerPermission;
    }

}
