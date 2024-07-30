package felnull.dev.Listeners;

import felnull.dev.betterstorage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

public class CommonListener implements Listener {

    public static void init(betterstorage plugin) {
        Bukkit.getServer().getPluginManager().registerEvents(new CommonListener(), plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event){
        //blockに右クリックしたブロックを取得して入れている
        Block block = event.getClickedBlock();

        //ブロックがエンダーチェストかを判定
        if(block != null && block.getType() == Material.ENDER_CHEST){
            event.setCancelled(true);

        }
    }
}
