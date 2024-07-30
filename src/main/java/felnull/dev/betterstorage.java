package felnull.dev;

import felnull.dev.Listeners.CommonListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class betterstorage extends JavaPlugin {

    public static final String PLUGIN_NAME = "BetterStorage";

    public static final String PLUGIN_ID = "betterstorage";

    @Override
    public void onEnable() {
        // Plugin startup logic
        initEventListeners();

    }
    private void initEventListeners() {
        CommonListener.init(this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
