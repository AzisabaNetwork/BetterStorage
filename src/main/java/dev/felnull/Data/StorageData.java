package dev.felnull.Data;

import dev.felnull.DataIO.DataIO;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class StorageData {
    public UUID groupUUID; // ← UUIDベースに変更
    public GroupData groupData;
    public final Set<String> requireBankPermission;
    public double bankMoney;
    @NotNull
    public Map<String, InventoryData> storageInventory;
    public boolean fullyLoaded = true;

    public StorageData(@NotNull Set<String> requireBankPermission, Map<String, InventoryData> storageInventory, double bankMoney) {
        this.requireBankPermission = requireBankPermission;
        this.storageInventory = (storageInventory != null ? storageInventory : new HashMap<>());
        this.bankMoney = bankMoney;
    }

    public void setFullyLoaded(boolean fullyLoaded) {
        this.fullyLoaded = fullyLoaded;
    }

    public boolean isFullyLoaded() {
        return fullyLoaded;
    }

    /**
     * 指定したページの中身を読み込む。
     * isFully = false の場合、InventoryDataはメタデータのみ。
     */
    public void loadPage(Connection conn, String pluginName, String pageId) throws SQLException {
        InventoryData inv = storageInventory.get(pageId);
        if (inv != null && !inv.isFullyLoaded()) {
            // ページ内のアイテムを読み込み
            DataIO.loadPageItems(conn, groupUUID, pluginName, inv, pageId);

            // ← versionが未設定だったらここで取得しておく（重要！）
            if (inv.version == 0L) {
                inv.version = DataIO.getInventoryPageVersion(conn, groupUUID, pluginName, pageId);
            }

            inv.setFullyLoaded(true);
        }
    }

    public void detach() {
        this.groupData = null;
    }

    public void attach(GroupData group) {
        this.groupData = group;
        this.groupUUID = group.groupUUID;
        this.groupData.storageData = this;
    }

    /**
     * 指定されたページIDに対応するInventoryDataを更新する。
     * nullチェック付きで安全に置き換える。
     */
    public void updateInventoryData(String pageId) {
        InventoryData newData = storageInventory.get(pageId);
        if (pageId == null || newData == null) {
            throw new IllegalArgumentException("pageId または newData が null です");
        }

        storageInventory.put(pageId, newData);
    }

    /**
     * 指定されたページIDのInventoryDataを取得する（存在しない場合はnull）。
     */
    public InventoryData getInventoryData(String pageId) {
        return storageInventory.get(pageId);
    }
}

