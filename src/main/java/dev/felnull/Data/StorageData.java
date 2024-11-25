package dev.felnull.Data;

import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.Set;

public class StorageData {
    public String groupName;//グループ名(個人プレイヤーもグループ扱い)
    public GroupData groupData;//ストレージの所属グループ
    public final Set<String> requireBankPermission;//ストレージ直下の金庫の要求パーミッション(BukkitPermではない)
    public int bankMoney;//ストレージ付属金庫の値
    public Map<String,InventoryData> storageInventry;//ストレージに含まれているインベントリデータ　キーのStringはページ名

    public StorageData(Set<String> requireBankPermission, Map<String,InventoryData> storageInventry, int bankMoney) {
        this.requireBankPermission = requireBankPermission;
        this.storageInventry = storageInventry;
        this.bankMoney = bankMoney;
    }
}
