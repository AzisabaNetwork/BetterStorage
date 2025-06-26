package dev.felnull.Data;

import dev.felnull.DataIO.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class StorageDataBackup {
    public Set<String> requireBankPermission;
    public double bankMoney;
    public Map<String, InventoryDataBackup> storageInventory = new HashMap<>();

    public StorageDataBackup(StorageData original) {
        this.requireBankPermission = original.requireBankPermission;
        this.bankMoney = original.bankMoney;

        for (Map.Entry<String, InventoryData> entry : original.storageInventory.entrySet()) {
            String pageId = entry.getKey();
            InventoryData originalInv = entry.getValue();
            storageInventory.put(pageId, new InventoryDataBackup(originalInv));
        }
    }

    public StorageData toStorageData() {
        StorageData data = new StorageData(requireBankPermission, new HashMap<>(), bankMoney);

        for (Map.Entry<String, InventoryDataBackup> entry : storageInventory.entrySet()) {
            String pageId = entry.getKey();
            InventoryDataBackup backup = entry.getValue();

            Map<Integer, ItemStack> slotMap = new HashMap<>();
            for (Map.Entry<Integer, String> itemEntry : backup.itemStackBase64.entrySet()) {
                ItemStack item = ItemSerializer.deserializeFromBase64(itemEntry.getValue());
                slotMap.put(itemEntry.getKey(), item);
            }

            InventoryData inv = new InventoryData(
                    backup.displayName,
                    backup.rows,
                    backup.requirePermission,
                    slotMap
            );
            inv.userTags = backup.userTags;
            data.storageInventory.put(pageId, inv);
        }

        return data;
    }
}
