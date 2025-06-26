package dev.felnull.Data;

import dev.felnull.DataIO.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class InventoryDataBackup {
    public String displayName;
    public int rows;
    public Set<String> requirePermission;
    public Map<Integer, String> itemStackBase64 = new HashMap<>(); // ← Base64形式に変える
    public List<String> userTags;
    public long version; // ←追加！

    public InventoryDataBackup(InventoryData original) {
        this.displayName = original.displayName;
        this.rows = original.rows;
        this.requirePermission = new HashSet<>(original.requirePermission);
        this.userTags = new ArrayList<>(original.userTags);

        for (Map.Entry<Integer, ItemStack> entry : original.itemStackSlot.entrySet()) {
            String base64 = ItemSerializer.serializeToBase64(entry.getValue());
            itemStackBase64.put(entry.getKey(), base64);
        }
    }

    public InventoryData toInventoryData() {
        Map<Integer, ItemStack> itemStackSlot = new HashMap<>();
        for (Map.Entry<Integer, String> entry : itemStackBase64.entrySet()) {
            ItemStack item = ItemSerializer.deserializeFromBase64(entry.getValue());
            itemStackSlot.put(entry.getKey(), item);
        }
        InventoryData inv = new InventoryData(this.displayName, this.rows, this.requirePermission, itemStackSlot);
        inv.userTags.clear();
        inv.userTags.addAll(userTags != null ? userTags : Collections.emptyList());
        inv.version = this.version;
        return inv;
    }
}
