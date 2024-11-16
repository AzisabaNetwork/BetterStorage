package dev.felnull.Data;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Set;

public class InventoryData {
    public final String[] userTag; //ユーザーがインベントリに対してつけるタグ
    public final String displayName; //表示名
    public final int rows; //行数
    public final Set<String> requirePermission; //インベントリを開くのに必要な最低限の権限
    public Map<Integer, ItemStack> itemStackSlot; //スロットに対応するアイテム
    public InventoryData(String[] userTag, String displayName, int rows, Set<String> requirePermission, Map<Integer, ItemStack> itemStackSlot) {
        this.userTag = userTag;
        this.displayName = displayName;
        this.rows = rows;
        this.requirePermission = requirePermission;
        this.itemStackSlot = itemStackSlot;
    }
}
