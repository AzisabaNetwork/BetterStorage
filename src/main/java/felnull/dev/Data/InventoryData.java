package felnull.dev.Data;

import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.Set;

public class InventoryData {
    public final String[] userTag;
    public final String displayName;
    public final int rows;
    public final Set<String> requirePermission;
    public Map<Integer, Inventory> inventorySlot;
    public InventoryData(String[] userTag, String displayName, int rows, Set<String> requirePermission, Map<Integer, Inventory> inventorySlot) {
        this.userTag = userTag;
        this.displayName = displayName;
        this.rows = rows;
        this.requirePermission = requirePermission;
        this.inventorySlot = inventorySlot;
    }
}
