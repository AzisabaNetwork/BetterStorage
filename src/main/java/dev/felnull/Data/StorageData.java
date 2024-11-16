package dev.felnull.Data;

import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.Set;

public class StorageData {
    public final int groupName;//グループ名(個人プレイヤーもグループ扱い)
    public final boolean personal;//個人用かグループ用かのフラグ
    public final Set<String> requireBankPermission;//ストレージ直下の金庫の要求パーミッション(BukkitPermではない)
    public int bankMoney;//ストレージ付属金庫の値
    public Map<String,InventoryData> storageInventry;//ストレージに含まれているインベントリデータ

    public StorageData(int groupName, boolean personal, Set<String> requireBankPermission, Map<String,InventoryData> storageInventry, int bankMoney) {
        this.groupName = groupName;
        this.personal = personal;
        this.requireBankPermission = requireBankPermission;
        this.storageInventry = storageInventry;
        this.bankMoney = bankMoney;
    }
}
