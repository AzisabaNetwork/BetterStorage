package dev.felnull.Data;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.*;
import java.util.stream.Collectors;

public class DeletedGroupBackup {
    public String groupName;
    public UUID groupUUID;
    public String displayName;
    public boolean isPrivate;
    public String ownerPlugin;
    public List<GroupMemberData> memberList; // GroupMemberData で役職を含めて管理
    public StorageDataBackup storageData;    // GZIP+JSONで保存

    public DeletedGroupBackup(GroupData groupData, StorageDataBackup storageData) {
        this.groupName = groupData.groupName;
        this.groupUUID = groupData.groupUUID;
        this.displayName = groupData.displayName;
        this.isPrivate = groupData.isPrivate;
        this.ownerPlugin = groupData.ownerPlugin;
        this.storageData = storageData;

        // そのままコピー（deep copy したいなら new GroupMemberData(...) に変換してもいい）
        this.memberList = new ArrayList<>(groupData.playerList);
    }


    /** GroupData に復元する */
    public GroupData toGroupData() {
        List<GroupMemberData> members = new ArrayList<>();
        for (GroupMemberData m : memberList) {
            members.add(new GroupMemberData(m.memberUUID, m.role.clone()));
        }

        StorageData sd = storageData.toStorageData(); // 復元用メソッド
        return new GroupData(groupName, displayName, members, isPrivate, sd, ownerPlugin, groupUUID);
    }
}
