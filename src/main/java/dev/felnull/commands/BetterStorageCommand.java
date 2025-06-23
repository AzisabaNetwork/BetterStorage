package dev.felnull.commands;

import dev.felnull.BetterStorage;
import dev.felnull.Data.GroupData;
import dev.felnull.DataIO.DataIO;
import dev.felnull.DataIO.RollbackLogManager;
import dev.felnull.DataIO.DatabaseManager;
import dev.felnull.DataIO.DiffLogManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

public class BetterStorageCommand implements CommandExecutor, TabCompleter {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("/bstorage rollback <groupName/playerName> <yyyy-MM-dd HH:mm:ss>");
            return true;
        }

        DatabaseManager db = BetterStorage.BSPlugin.getDatabaseManager();

        switch (args[0].toLowerCase()) {
            case "rollback": {
                if (args.length < 3) {
                    sender.sendMessage("引数が不足しています。/bstorage rollback <groupName/playerName> <yyyy-MM-dd HH:mm:ss>");
                    return true;
                }

                String nameOrGroup = args[1];
                String groupUUID = resolveGroupUUID(nameOrGroup);
                if (groupUUID == null) {
                    sender.sendMessage("指定された名前またはグループに対応するUUIDが見つかりませんでした。");
                    return true;
                }

                String timestampStr = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                try {
                    LocalDateTime time = LocalDateTime.parse(timestampStr, FORMATTER);
                    boolean result = RollbackLogManager.restoreGroupFromRollback(groupUUID, time);
                    if (result) {
                        sender.sendMessage("グループ " + nameOrGroup + " を " + timestampStr + " に巻き戻しました。");
                    } else {
                        sender.sendMessage("指定の時点のログが見つかりませんでした。");
                    }
                } catch (DateTimeParseException e) {
                    sender.sendMessage("日時の形式が正しくありません。yyyy-MM-dd HH:mm:ss で指定してください。");
                }
                break;
            }
            case "list": {
                if (args.length < 2) {
                    sender.sendMessage("/bstorage list <groupName/playerName>");
                    return true;
                }
                String nameOrGroup = args[1];
                String groupUUID = resolveGroupUUID(nameOrGroup);
                if (groupUUID == null) {
                    sender.sendMessage("指定された名前またはグループに対応するUUIDが見つかりませんでした。");
                    return true;
                }
                List<LocalDateTime> logs = RollbackLogManager.getRollbackTimestamps(groupUUID);
                if (logs.isEmpty()) {
                    sender.sendMessage("ログが見つかりませんでした。");
                } else {
                    sender.sendMessage("[ " + nameOrGroup + " ] のログ一覧:");
                    for (LocalDateTime log : logs) {
                        sender.sendMessage(" - " + log.format(FORMATTER));
                    }
                }
                break;
            }
            case "diff": {
                if (args.length < 3) {
                    sender.sendMessage("引数が不足しています。/bstorage diff <groupName/playerName> <yyyy-MM-dd HH:mm:ss>");
                    return true;
                }
                String nameOrGroup = args[1];
                String groupUUID = resolveGroupUUID(nameOrGroup);
                if (groupUUID == null) {
                    sender.sendMessage("指定された名前またはグループに対応するUUIDが見つかりませんでした。");
                    return true;
                }
                String timestampStr = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                try {
                    LocalDateTime time = LocalDateTime.parse(timestampStr, FORMATTER);
                    GroupData groupData = DataIO.loadGroupData(groupUUID);
                    if (groupData == null) {
                        sender.sendMessage("指定したグループが見つかりません。");
                        return true;
                    }
                    boolean result = DiffLogManager.restoreGroupFromDiffLog(db, groupData, time);
                    if (result) {
                        sender.sendMessage("グループ " + nameOrGroup + " を差分ログから復元しました。");
                    } else {
                        sender.sendMessage("差分ログが見つかりませんでした。");
                    }
                } catch (DateTimeParseException e) {
                    sender.sendMessage("日時の形式が正しくありません。yyyy-MM-dd HH:mm:ss で指定してください。");
                }
                break;
            }
            case "help":
                sender.sendMessage("使用可能なコマンド:");
                sender.sendMessage("/bstorage rollback <groupName/playerName> <yyyy-MM-dd HH:mm:ss> - 指定した時点に巻き戻す");
                sender.sendMessage("/bstorage list <groupName/playerName> - 利用可能なログ日時を表示");
                sender.sendMessage("/bstorage diff <groupName/playerName> <yyyy-MM-dd HH:mm:ss> - 差分ログから状態を復元");
                break;
            default:
                sender.sendMessage("不明なサブコマンドです。 /bstorage help で確認できます。");
                break;
        }
        return true;
    }

    private @Nullable String resolveGroupUUID(String input) {
        // プレイヤー名と照合してUUIDを返す
        OfflinePlayer player = Bukkit.getOfflinePlayer(input);
        if (player.hasPlayedBefore() || player.isOnline()) {
            return player.getUniqueId().toString();
        }
        // 直接UUIDとして使えるかチェック
        try {
            UUID uuid = UUID.fromString(input);
            return uuid.toString();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            suggestions.addAll(Arrays.asList("rollback", "list", "diff", "help"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("rollback") || args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("diff"))) {
            suggestions.addAll(RollbackLogManager.getAllGroupUUIDs());
            for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                if (player.getName() != null) {
                    suggestions.add(player.getName());
                }
            }
        }

        return suggestions;
    }
}