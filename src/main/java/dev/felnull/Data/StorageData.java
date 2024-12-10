package dev.felnull.Data;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class StorageData {
    public String groupName;//グループ名(個人プレイヤーもグループ扱い)
    public GroupData groupData;//ストレージの所属グループ
    public final Set<String> requireBankPermission;//ストレージ直下の金庫の要求パーミッション(BukkitPermではない)
    public double bankMoney;//ストレージ付属金庫の値
    @NotNull
    public Map<String,InventoryData> storageInventory;//ストレージに含まれているインベントリデータ　キーのStringはページ名

    public StorageData(@NotNull Set<String> requireBankPermission, Map<String,InventoryData> storageInventory, double bankMoney) {
        this.requireBankPermission = requireBankPermission;
        if(storageInventory == null){
            storageInventory = new HashMap<>();
        }
        this.storageInventory = storageInventory;
        this.bankMoney = bankMoney;
    }
}
