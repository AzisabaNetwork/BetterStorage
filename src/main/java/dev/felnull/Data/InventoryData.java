package dev.felnull.Data;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class InventoryData {
    public List<String> userTags = new ArrayList<>(); // ← 修正：タグを複数保持するListに
    public String displayName; // 表示名
    public final int rows; // 行数
    public Set<String> requirePermission; // インベントリを開くのに必要な最低限の権限
    public Map<Integer, ItemStack> itemStackSlot; // スロットに対応するアイテム
    public boolean fullyLoaded = true;
    public long version = 0L;

    public InventoryData(String displayName, int rows, Set<String> requirePermission, Map<Integer, ItemStack> itemStackSlot) {
        this.displayName = displayName;
        this.rows = rows;
        this.requirePermission = requirePermission;
        this.itemStackSlot = itemStackSlot;
    }

    // タグ用の補助メソッド
    public void addUserTag(String tag) {
        if (tag == null) return;
        userTags.add(tag);
    }
    public boolean removeUserTag(String tag){
        if (tag == null) return false;
        return userTags.removeIf(s -> Objects.equals(s, tag));
    }

    // タグ全取得
    public List<String> getUserTags() {
        return userTags;
    }

    // Inventoryを保存して itemStackSlot に更新
    public boolean saveInventory(Inventory inventory) {
        Map<Integer, ItemStack> newItemStackSlot = new HashMap<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null) {
                newItemStackSlot.put(i, item);
            }
        }
        this.itemStackSlot = newItemStackSlot;
        return false;
    }

    public void setFullyLoaded(boolean fullyLoaded) {
        this.fullyLoaded = fullyLoaded;
    }

    public boolean isFullyLoaded() {
        return fullyLoaded;
    }

    public InventoryData deepClone() {
        InventoryData clone = new InventoryData(this.displayName, this.rows,
                new HashSet<>(this.requirePermission),
                new HashMap<>()); // itemStackSlotは後でコピー

        // ItemStackを安全にコピー（浅いコピーで十分ならそのまま）
        for (Map.Entry<Integer, ItemStack> entry : this.itemStackSlot.entrySet()) {
            clone.itemStackSlot.put(entry.getKey(), entry.getValue().clone()); // deep copy推奨
        }

        clone.userTags = new ArrayList<>(this.userTags);
        clone.version = this.version;
        clone.fullyLoaded = this.fullyLoaded;
        return clone;
    }
}
