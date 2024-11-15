package felnull.dev;

import felnull.dev.Listeners.CommonListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class betterstorage extends JavaPlugin {

    public static final String PLUGIN_NAME = "BetterStorage";

    public static final String PLUGIN_ID = "betterstorage";

    @Override
    public void onEnable() {
        initEventListeners();
        //SQLiteに接続
        //MariaDBデータベースに接続
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
    }
    
    
}
