package dev.felnull.api;

import dev.felnull.Data.GroupPermENUM;
import dev.felnull.Data.InventoryData;
import dev.felnull.Data.StorageData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;


public class EcpImporter {

    public static StorageData importFromUUID(UUID playerUUID, UUID groupUUID) {
        File file = new File(Bukkit.getPluginManager().getPlugin("BetterStorage")
                .getDataFolder().getParent(), "EnderChestPlus/Inventories/" + playerUUID + ".yml");

        if (!file.exists()) return null;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        Set<String> bankPerms = new HashSet<>();
        bankPerms.add(GroupPermENUM.MEMBER.getPermName());
        Map<String, InventoryData> inventoryMap = new HashMap<>();

        for (String pageId : config.getKeys(false)) {
            ConfigurationSection pageSection = config.getConfigurationSection(pageId);

            Map<Integer, ItemStack> itemMap = new HashMap<>();
            for (String slotKey : pageSection.getKeys(false)) {
                int slot = Integer.parseInt(slotKey);
                ItemStack item = pageSection.getItemStack(slotKey);
                if (item != null && item.getType() != Material.AIR) {
                    itemMap.put(slot, item);
                }
            }

            Set<String> pagePerms = new HashSet<>();
            pagePerms.add(GroupPermENUM.MEMBER.getPermName());

            InventoryData inv = new InventoryData("ECPページ #" + pageId, 57, pagePerms, itemMap);
            inv.addUserTag("ecp-imported");

            inventoryMap.put(pageId, inv);
        }

        StorageData storageData = new StorageData(bankPerms, inventoryMap, 0.0);
        storageData.groupUUID = groupUUID;

        return storageData;
    }
}