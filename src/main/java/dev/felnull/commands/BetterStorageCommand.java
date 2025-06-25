package dev.felnull.commands;

import dev.felnull.BetterStorage;
import dev.felnull.Data.GroupData;
import dev.felnull.DataIO.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;


public class BetterStorageCommand implements CommandExecutor, TabCompleter {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        DatabaseManager db = BetterStorage.BSPlugin.getDatabaseManager();

        switch (args[0].toLowerCase()) {
            case "rollback": {
                if (args.length < 3) {
                    sender.sendMessage("引数が不足しています。/bstorage rollback <groupName/playerName> <yyyy-MM-dd HH:mm:ss>");
                    return true;
                }

                String nameOrGroup = args[1];
                String timestampStr = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                sender.sendMessage("DBアクセス中...");
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        UUID groupUUID = resolveGroupUUID(nameOrGroup);
                        if (groupUUID == null) {
                            Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () ->
                                    sender.sendMessage("指定された名前またはグループに対応するUUIDが見つかりませんでした。"));
                            return;
                        }

                        try {
                            LocalDateTime time = LocalDateTime.parse(timestampStr, FORMATTER);
                            boolean result = RollbackLogManager.restoreGroupFromRollback(groupUUID, time);
                            Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () -> {
                                if (result) {
                                    sender.sendMessage("グループ " + nameOrGroup + " を " + timestampStr + " に巻き戻しました。");
                                } else {
                                    sender.sendMessage("指定の時点のログが見つかりませんでした。");
                                }
                            });
                        } catch (DateTimeParseException e) {
                            Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () ->
                                    sender.sendMessage("日時の形式が正しくありません。yyyy-MM-dd HH:mm:ss で指定してください。"));
                        }
                    }
                }.runTaskAsynchronously(BetterStorage.BSPlugin);

                return true;
            }
            case "list": {
                if (args.length < 2) {
                    sender.sendMessage("/bstorage list <groupName/playerName>");
                    return true;
                }

                String nameOrGroup = args[1];
                sender.sendMessage("DBアクセス中...");
                // 非同期で実行
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        UUID groupUUID = resolveGroupUUID(nameOrGroup);
                        if (groupUUID == null) {
                            Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () -> {
                                sender.sendMessage("指定された名前またはグループに対応するUUIDが見つかりませんでした。");
                            });
                            return;
                        }

                        List<LocalDateTime> logs = RollbackLogManager.getRollbackTimestamps(groupUUID.toString());

                        if (logs.isEmpty()) {
                            Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () -> {
                                sender.sendMessage("ログが見つかりませんでした。");
                            });
                        } else {
                            Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () -> {
                                sender.sendMessage("[ " + nameOrGroup + " ] のログ一覧:");
                                for (LocalDateTime log : logs) {
                                    sender.sendMessage(" - " + log.format(FORMATTER));
                                }
                            });
                        }
                    }
                }.runTaskAsynchronously(BetterStorage.BSPlugin);

                return true;
            }
            case "diff": {
                if (args.length < 3) {
                    sender.sendMessage("引数が不足しています。/bstorage diff <groupName/playerName> <yyyy-MM-dd HH:mm:ss>");
                    return true;
                }

                String targetName = args[1];
                String timestampStr = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                sender.sendMessage("DBアクセス中...");
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        UUID groupUUID = resolveGroupUUID(targetName);
                        if (groupUUID == null) {
                            Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () ->
                                    sender.sendMessage("指定された名前またはグループに対応するUUIDが見つかりませんでした。"));
                            return;
                        }

                        try {
                            LocalDateTime time = LocalDateTime.parse(timestampStr, FORMATTER);
                            GroupData groupData = DataIO.loadGroupData(groupUUID);
                            if (groupData == null) {
                                Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () ->
                                        sender.sendMessage("指定したグループが見つかりません。"));
                                return;
                            }

                            boolean result = DiffLogManager.restoreGroupFromDiffLog(db, groupData, time);
                            Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () -> {
                                if (result) {
                                    sender.sendMessage("グループ " + targetName + " を差分ログから復元しました。");
                                } else {
                                    sender.sendMessage("差分ログが見つかりませんでした。");
                                }
                            });
                        } catch (DateTimeParseException e) {
                            Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () ->
                                    sender.sendMessage("日時の形式が正しくありません。yyyy-MM-dd HH:mm:ss で指定してください。"));
                        }
                    }
                }.runTaskAsynchronously(BetterStorage.BSPlugin);
                return true;
            }
            case "difflist": {
                if (args.length < 2) {
                    sender.sendMessage("/bstorage difflist <groupName/playerName>");
                    return true;
                }

                String nameOrGroup = args[1];
                sender.sendMessage("DBアクセス中...");
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        UUID groupUUID = resolveGroupUUID(nameOrGroup);
                        if (groupUUID == null) {
                            Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () ->
                                    sender.sendMessage("指定された名前またはグループに対応するUUIDが見つかりませんでした。"));
                            return;
                        }

                        List<LocalDateTime> logs = DiffLogManager.getDiffTimestamps(groupUUID.toString());

                        if (logs.isEmpty()) {
                            Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () ->
                                    sender.sendMessage("差分ログが見つかりませんでした。"));
                        } else {
                            Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () -> {
                                sender.sendMessage(Component.text("[ " + nameOrGroup + " ] の差分ログ一覧:").color(NamedTextColor.YELLOW));

                                for (LocalDateTime log : logs) {
                                    String formatted = log.format(FORMATTER);
                                    Component msg = Component.text(" - [ ")
                                            .append(Component.text(formatted)
                                                    .color(NamedTextColor.AQUA)
                                                    .clickEvent(ClickEvent.suggestCommand("/bstorage diff " + nameOrGroup + " " + formatted))
                                                    .hoverEvent(HoverEvent.showText(Component.text("クリックでコマンドを入力"))))
                                            .append(Component.text(" ]"));

                                    sender.sendMessage(msg);
                                }
                            });
                        }
                    }
                }.runTaskAsynchronously(BetterStorage.BSPlugin);

                return true;
            }
            case "backup": {
                if (args.length < 2) {
                    sender.sendMessage("引数が不足しています。/bstorage rollbacksave <groupName/playerName>");
                    return true;
                }

                String nameOrGroup = args[1];
                sender.sendMessage("バックアップ処理実行中...");
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        UUID groupUUID = resolveGroupUUID(nameOrGroup);
                        if (groupUUID == null) {
                            Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () ->
                                    sender.sendMessage("UUIDが見つかりません。"));
                            return;
                        }

                        GroupData groupData = DataIO.loadGroupData(groupUUID);
                        if (groupData == null) {
                            Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () ->
                                    sender.sendMessage("グループデータの読み込みに失敗しました。"));
                            return;
                        }

                        RollbackLogManager.saveRollbackLog(groupData);
                        Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () ->
                                sender.sendMessage("ロールバック用バックアップを保存しました。"));
                    }
                }.runTaskAsynchronously(BetterStorage.BSPlugin);
                return true;
            }
            case "exportlog": {
                if (args.length < 3) {
                    sender.sendMessage("引数が不足しています。/bstorage exportlog <playerName> <display_name_plain>");
                    return true;
                }

                String playerName = args[1];
                String displayNamePlain = args[2];

                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
                if (offlinePlayer == null || offlinePlayer.getUniqueId() == null) {
                    sender.sendMessage("プレイヤーが見つかりませんでした。");
                    return true;
                }

                UUID playerUUID = offlinePlayer.getUniqueId();

                sender.sendMessage("操作履歴を出力中...");

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        List<Map<String, Object>> records = new ArrayList<>();
                        String query =
                                "SELECT date, group_uuid, plugin_name, page_id, material, display_name, operation_type, total_amount " +
                                        "FROM inventory_item_summary " +
                                        "WHERE player_uuid = ? AND display_name_plain = ? " +
                                        "ORDER BY date ASC";

                        try (Connection conn = db.getConnection();
                             PreparedStatement ps = conn.prepareStatement(query)) {

                            ps.setString(1, playerUUID.toString());
                            ps.setString(2, displayNamePlain);

                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) {
                                    Map<String, Object> entry = new LinkedHashMap<>();
                                    entry.put("date", rs.getDate("date").toString());
                                    entry.put("group_uuid", rs.getString("group_uuid"));
                                    entry.put("plugin_name", rs.getString("plugin_name"));
                                    entry.put("page_id", rs.getString("page_id"));
                                    entry.put("material", rs.getString("material"));
                                    entry.put("display_name", rs.getString("display_name"));
                                    entry.put("operation_type", rs.getString("operation_type"));
                                    entry.put("total_amount", rs.getInt("total_amount"));
                                    records.add(entry);
                                }
                            }
                        } catch (SQLException e) {
                            Bukkit.getScheduler().runTask(BetterStorage.BSPlugin,
                                    () -> sender.sendMessage("データベースエラー: " + e.getMessage()));
                            return;
                        }

                        if (records.isEmpty()) {
                            Bukkit.getScheduler().runTask(BetterStorage.BSPlugin,
                                    () -> sender.sendMessage("該当するログが見つかりませんでした。"));
                            return;
                        }

                        // ファイル出力
                        File exportFile = new File(BetterStorage.BSPlugin.getDataFolder(), "logs/" + playerName + "_" + displayNamePlain + ".yml");
                        exportFile.getParentFile().mkdirs();
                        YamlConfiguration config = new YamlConfiguration();
                        config.set("records", records);

                        try {
                            config.save(exportFile);
                            Bukkit.getScheduler().runTask(BetterStorage.BSPlugin,
                                    () -> sender.sendMessage("操作ログを " + exportFile.getName() + " に保存しました。"));
                        } catch (IOException e) {
                            Bukkit.getScheduler().runTask(BetterStorage.BSPlugin,
                                    () -> sender.sendMessage("ファイル保存に失敗しました: " + e.getMessage()));
                        }
                    }
                }.runTaskAsynchronously(BetterStorage.BSPlugin);

                return true;
            }
            default:
                sender.sendMessage("使用可能なコマンド:");
                sender.sendMessage("/bstorage rollback <groupName/playerName> <yyyy-MM-dd HH:mm:ss> - 指定した時点に巻き戻す");
                sender.sendMessage("/bstorage list <groupName/playerName> - 利用可能なログ日時を表示");
                sender.sendMessage("/bstorage diff <groupName/playerName> <yyyy-MM-dd HH:mm:ss> - 差分ログから状態を復元");
                sender.sendMessage("/bstorage difflist <groupName/playerName> - 差分ログの日時一覧を表示");
                sender.sendMessage("/bstorage backup <groupName/playerName> - 現在の状態で強制バックアップ");
                break;

        }
        return true;
    }

    private @Nullable UUID resolveGroupUUID(String input) {
        // 1. 入力がUUID文字列ならそのまま使う
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException ignored) {
            // UUID形式ではない
        }

        // 2. グループ名から取得（DB経由）
        UUID groupUUID = DataIO.getGroupUUIDFromName(input);
        if (groupUUID != null) return groupUUID;


        return null;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            suggestions.addAll(Arrays.asList("rollback", "list", "diff", "difflist", "help", "backup", "exportlog"));
        } else if (args.length == 2 && Arrays.asList("rollback", "list", "diff", "difflist", "backup").contains(args[0].toLowerCase())) {
            // グループ名とプレイヤー名だけを対象にする
            suggestions.addAll(GroupManager.getAllGroupNames());

            for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                if (player.getName() != null) {
                    suggestions.add(player.getName());
                }
            }
        }

        // 第3引数以降の補完は提供しない
        return suggestions;
    }
}
