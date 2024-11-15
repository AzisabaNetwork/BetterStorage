package felnull.dev.Data;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;

public class GroupData {
    public final String groupName;
    public Set<Player> playerList;
    public Map<Player,String[]> playerPermission;
    public GroupData (String groupName, Set<Player> playerList, Map<Player,String[]> playerPermission) {
        this.groupName = groupName;
        this.playerList = playerList;
        this.playerPermission = playerPermission;
    }

}
