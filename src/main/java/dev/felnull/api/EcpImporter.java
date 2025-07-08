package dev.felnull.api;

import dev.felnull.BetterStorage;
import dev.felnull.Data.GroupPermENUM;
import dev.felnull.Data.InventoryData;
import dev.felnull.Data.StorageData;
import jp.azisaba.lgw.ecplus.EnderChestPlus;
import jp.azisaba.lgw.ecplus.InventoryLoader;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class EcpImporter {

    public static StorageData importFromUUID(UUID playerUUID, UUID groupUUID) {
        try (Connection conn = BetterStorage.BSPlugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT ecp_imported FROM group_table WHERE group_uuid = ?")) {
            ps.setString(1, groupUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getBoolean("ecp_imported")) {
                    Bukkit.getLogger().warning("[BetterStorage] このグループはすでにECPからインポートされていますにゃ！");
                    return null;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        // EnderChestPlusのファイルパス
        File file = new File(Bukkit.getPluginManager().getPlugin("BetterStorage")
                .getDataFolder().getParent(), "EnderChestPlus/Inventories/" + playerUUID + ".yml");

        if (!file.exists()) return null;

        YamlConfiguration config;
        try {
            config = YamlConfiguration.loadConfiguration(file);
        } catch (Exception ex) {
            Bukkit.getLogger().warning("[BetterStorage] ECPインポート時にファイルの読み込みに失敗したにゃ: " + ex.getMessage());
            return null;
        }

        Plugin plugin = Bukkit.getPluginManager().getPlugin("EnderChestPlus");

        if (plugin != null && plugin.isEnabled() && plugin instanceof EnderChestPlus) {
            EnderChestPlus ecp = (EnderChestPlus) plugin;
            InventoryLoader loader = ecp.getLoader();

            if (loader != null) {
                loader.saveAllInventoryData(false); // false = 非同期ではない
                Bukkit.getLogger().info("[BetterStorage] EnderChestPlus のセーブを実行したにゃ");
            } else {
                Bukkit.getLogger().warning("[BetterStorage] EnderChestPlus の loader が null だったにゃ");
            }
        }

        // バンク権限（ストレージ全体にアクセスするための最低限の権限）
        Set<String> bankPerms = new HashSet<>();
        bankPerms.add(GroupPermENUM.MEMBER.getPermName());

        // ページID → InventoryData
        Map<String, InventoryData> inventoryMap = new HashMap<>();

        for (String pageId : config.getKeys(false)) {
            ConfigurationSection pageSection = config.getConfigurationSection(pageId);
            if (pageSection == null) continue;

            Map<Integer, ItemStack> itemMap = new HashMap<>();
            for (String slotKey : pageSection.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(slotKey);
                    if (slot < 0 || slot >= 57) continue; // スロット番号チェック追加
                    ItemStack item = pageSection.getItemStack(slotKey);
                    if (item != null && item.getType() != Material.AIR) {
                        itemMap.put(slot, item);
                    }
                } catch (NumberFormatException ex) {
                    continue; // 無効なスロットキーはスキップ
                }
            }

            // ページ権限
            Set<String> pagePerms = new HashSet<>();
            pagePerms.add(GroupPermENUM.MEMBER.getPermName());

            int displayPageId;
            try {
                displayPageId = Integer.parseInt(pageId);
            } catch (NumberFormatException ex) {
                displayPageId = 0; // fallback
            }

            // InventoryDataを作成
            InventoryData inv = new InventoryData("ECPページ #" + (displayPageId + 1), 57, pagePerms, itemMap);

            inventoryMap.put(pageId, inv); // 内部IDはそのまま（ズレ防止）
        }

        // ストレージデータ構築
        StorageData storageData = new StorageData(bankPerms, inventoryMap, 0.0);
        storageData.groupUUID = groupUUID;

        try (Connection conn = BetterStorage.BSPlugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE group_table SET ecp_imported = TRUE WHERE group_uuid = ?")) {
            ps.setString(1, groupUUID.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return storageData;
    }

}
