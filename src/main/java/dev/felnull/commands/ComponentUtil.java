package dev.felnull.commands;

import net.kyori.adventure.text.format.NamedTextColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import static net.kyori.adventure.text.format.NamedTextColor.*;

public class ComponentUtil {
    private static final boolean USE_ADVENTURE = isAdventureSupported();

    private static boolean isAdventureSupported() {
        String version = Bukkit.getBukkitVersion(); // 例: 1.16.5-R0.1-SNAPSHOT
        String[] parts = version.split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        return major > 1 || (major == 1 && minor >= 16);
    }

    public static void sendClickableMessage(CommandSender sender, String label, String command, String hoverText) {
        if (USE_ADVENTURE) {
            // Paper 1.16.5以降
            net.kyori.adventure.text.Component msg =
                    net.kyori.adventure.text.Component.text(" - [ ")
                            .append(net.kyori.adventure.text.Component.text(label)
                                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(command))
                                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                            net.kyori.adventure.text.Component.text(hoverText))))
                            .append(net.kyori.adventure.text.Component.text(" ]"));

            sender.sendMessage(msg);
        } else {
            // Spigot 1.15.2以前
            if (sender instanceof Player) {
                Player player = (Player) sender;
                TextComponent base = new TextComponent(" - [ ");
                TextComponent clickable = new TextComponent(label);
                clickable.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command));
                clickable.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder(hoverText).create()));
                base.addExtra(clickable);
                base.addExtra(" ]");
                player.spigot().sendMessage(base);
            } else {
                sender.sendMessage(" - [ " + label + " ] (" + hoverText + ")");
            }
        }
    }

    public static void sendPlainMessage(CommandSender sender, String text, NamedTextColor color) {
        if (USE_ADVENTURE) {
            net.kyori.adventure.text.Component msg = net.kyori.adventure.text.Component.text(text).color(color);
            sender.sendMessage(msg);
        } else {
            sender.sendMessage(namedTextColorToLegacy(color) + text);
        }
    }

    private static String namedTextColorToLegacy(NamedTextColor color) {
        // 最小限の変換（必要に応じて追加）
        if (color.equals(RED)) {
            return ChatColor.RED.toString();
        } else if (color.equals(GREEN)) {
            return ChatColor.GREEN.toString();
        } else if (color.equals(BLUE)) {
            return ChatColor.BLUE.toString();
        } else if (color.equals(YELLOW)) {
            return ChatColor.YELLOW.toString();
        } else if (color.equals(AQUA)) {
            return ChatColor.AQUA.toString();
        } else if (color.equals(GRAY)) {
            return ChatColor.GRAY.toString();
        } else if (color.equals(DARK_GRAY)) {
            return ChatColor.DARK_GRAY.toString();
        } else if (color.equals(GOLD)) {
            return ChatColor.GOLD.toString();
        } else if (color.equals(WHITE)) {
            return ChatColor.WHITE.toString();
        } else if (color.equals(BLACK)) {
            return ChatColor.BLACK.toString();
        } else if (color.equals(DARK_RED)) {
            return ChatColor.DARK_RED.toString();
        } else if (color.equals(DARK_GREEN)) {
            return ChatColor.DARK_GREEN.toString();
        } else if (color.equals(DARK_BLUE)) {
            return ChatColor.DARK_BLUE.toString();
        } else if (color.equals(DARK_PURPLE)) {
            return ChatColor.DARK_PURPLE.toString();
        } else if (color.equals(LIGHT_PURPLE)) {
            return ChatColor.LIGHT_PURPLE.toString();
        }
        return ChatColor.RESET.toString();
    }
}
