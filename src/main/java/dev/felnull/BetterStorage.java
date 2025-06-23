package dev.felnull;

import dev.felnull.DataIO.DatabaseManager;
import dev.felnull.DataIO.TableInitializer;
import dev.felnull.Listeners.CommonListener;
import dev.felnull.commands.BetterStorageCommand;
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
        try {
            Class.forName("org.mariadb.jdbc.Driver");
            //shadeしても読み込まれないバグを直すおまじないコード
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        initEventListeners();
        initCommands();
        dbManager = new DatabaseManager();
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
    private void initCommands() {
        getCommand("bstorage").setExecutor(new BetterStorageCommand());
        getCommand("bstorage").setTabCompleter(new BetterStorageCommand());
    }


    @Override
    public void onDisable() {
        dbManager.disconnect();
    }

    public DatabaseManager getDatabaseManager() {
        return dbManager;
    }

}
