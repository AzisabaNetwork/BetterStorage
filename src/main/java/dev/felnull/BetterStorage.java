package dev.felnull;

import dev.felnull.DataIO.DatabaseManager;
import dev.felnull.Listeners.CommonListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public final class BetterStorage extends JavaPlugin {
    public static BetterStorage BSPlugin; //プラグインのインスタンス
    private DatabaseManager dbManager;
    private final Logger logger = getLogger();

    @Override
    public void onEnable() {
        BSPlugin = this;
        initEventListeners();
        dbManager = new DatabaseManager();
        dbManager.connect();
        saveDefaultConfig();
        LogCleanerScheduler.schedule(dbManager);
        //ロガーの起動
        //Vault呼び出し
        //CSDirectorをnewで呼び出してメインクラスのpublic変数に置く
        //コマンドの登録処理メソッド呼び出し
    }

    //initEventListenersでリスナーを登録する
    private void initEventListeners() {
        Bukkit.getServer().getPluginManager().registerEvents(new CommonListener(), this);
    }


    @Override
    public void onDisable() {
        dbManager.disconnect();
    }

    public DatabaseManager getDatabaseManager() {
        return dbManager;
    }
    
}
