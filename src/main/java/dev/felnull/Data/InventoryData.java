package dev.felnull.Data;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class InventoryData {
    public String[] userTag; //ユーザーがインベントリに対してつけるタグ
    public String displayName; //表示名
    public final int rows; //行数
    public Set<String> requirePermission; //インベントリを開くのに必要な最低限の権限
    public Map<Integer, ItemStack> itemStackSlot; //スロットに対応するアイテム
    public InventoryData(String[] userTag, String displayName, int rows, Set<String> requirePermission, Map<Integer, ItemStack> itemStackSlot) {
        this.userTag = userTag;
        this.displayName = displayName;
        this.rows = rows;
        this.requirePermission = requirePermission;
        this.itemStackSlot = itemStackSlot;
    }

    public boolean saveInventory(Inventory inventory) {
        Map<Integer, ItemStack> newItemStackSlot = new HashMap<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);

            // 空でないスロットのみをマップに追加
            if (item != null) {
                newItemStackSlot.put(i, item);
            }
        }
        this.itemStackSlot = newItemStackSlot;
        return false;
    }
}
