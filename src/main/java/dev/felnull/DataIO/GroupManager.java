package dev.felnull.DataIO;

import dev.felnull.Data.GroupData;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GroupManager {
    private static final Map<String, GroupData> groupDataMap = new ConcurrentHashMap<>();

    // グループを登録
    public static void registerGroup(GroupData data) {
        groupDataMap.put(data.groupName, data);
    }

    // グループを取得
    public static GroupData getGroup(String groupName) {
        return groupDataMap.get(groupName);
    }

    // グループを削除
    public static void unregisterGroup(String groupName) {
        groupDataMap.remove(groupName);
    }

    // 全グループ取得
    public static Collection<GroupData> getAllGroups() {
        return groupDataMap.values();
    }

    // 登録されているか
    public static boolean isRegistered(String groupName) {
        return groupDataMap.containsKey(groupName);
    }

    // 全削除（再読み込み用など）
    public static void clear() {
        groupDataMap.clear();
    }
}