package dev.felnull;

import dev.felnull.Listeners.CommonListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class BetterStorage extends JavaPlugin {
    public static BetterStorage BSPlugin; //プラグインのインスタンス

    @Override
    public void onEnable() {
        BSPlugin = this;
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
