package dev.felnull.commands;

import dev.felnull.BetterStorage;
import dev.felnull.Data.DeletedGroupBackup;
import dev.felnull.Data.DeletedGroupInfo;
import dev.felnull.Data.GroupData;
import dev.felnull.DataIO.*;
import dev.felnull.task.ItemLogSummaryTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;


public class BetterStorageCommand implements CommandExecutor, TabCompleter {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        long first = System.currentTimeMillis();

        DatabaseManager db = BetterStorage.BSPlugin.getDatabaseManager();

        switch (args[0].toLowerCase()) {
            case "rollback": {
                if (args.length < 3) {
                    sender.sendMessage("引数が不足しています。/bstorage rollback <groupName/playerName> <yyyy-MM-dd HH:mm:ss>");
                    return true;
                }

                String nameOrGroup = args[1];
                String timestampStr = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).replace("\"", "");

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
                            Bukkit.getLogger().info("GroupUUID:" + groupUUID + "Time:" + time);
                            boolean result = UnifiedLogManager.restoreGroupToTimestamp(groupUUID, time);
                            Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () -> {
                                if (result) {
                                    sender.sendMessage("グループ " + nameOrGroup + " を " + timestampStr + " に巻き戻しました。");
                                } else {
                                    sender.sendMessage("指定時点のログが見つかりませんでした。");
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
            case "rollbacklist": {
                if (args.length < 2) {
                    sender.sendMessage("/bstorage rollbacklist <groupName/playerName>");
                    return true;
                }

                String nameOrGroup = args[1];
                sender.sendMessage("DBアクセス中...");
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

                        List<LocalDateTime> logs = UnifiedLogManager.getRollbackTimestamps(groupUUID);

                        if (logs.isEmpty()) {
                            Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () -> {
                                sender.sendMessage("ログが見つかりませんでした。");
                            });
                        } else {
                            Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () -> {
                                sender.sendMessage("[ " + nameOrGroup + " ] のログ一覧:");
                                for (LocalDateTime log : logs) {
                                    String formatted = log.format(FORMATTER);
                                    Component msg = Component.text(" - [ ")
                                            .append(Component.text(formatted)
                                                    .color(NamedTextColor.AQUA)
                                                    .clickEvent(ClickEvent.suggestCommand("/bstorage rollback " + nameOrGroup + " \"" + formatted + "\""))

                                                    .hoverEvent(HoverEvent.showText(Component.text("クリックでロールバックコマンドをチャット欄に入力"))))
                                            .append(Component.text(" ]"));
                                    sender.sendMessage(msg);
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
                            LocalDateTime to = LocalDateTime.parse(timestampStr, FORMATTER);

                            // ✅ ロールバック＋差分適用（GroupDataの事前取得は不要）
                            boolean result = UnifiedLogManager.restoreGroupToTimestamp(groupUUID, to);

                            Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () -> {
                                if (result) {
                                    sender.sendMessage("グループ " + targetName + " を " + to + " に復元しました。");
                                } else {
                                    sender.sendMessage("ロールバックと差分適用に失敗しました。");
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
                        long start = System.currentTimeMillis();

                        UUID groupUUID = resolveGroupUUID(nameOrGroup);
                        long afterUUID = System.currentTimeMillis();

                        if (groupUUID == null) {
                            Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () ->
                                    sender.sendMessage("UUIDが見つかりませんでした（" + (afterUUID - start) + "ms）"));
                            return;
                        }

                        List<LocalDateTime> diffLogs = UnifiedLogManager.getDiffLogTimestamps(groupUUID);
                        List<LocalDateTime> snapshots = UnifiedLogManager.getRollbackTimestamps(groupUUID);
                        long afterLogs = System.currentTimeMillis();

                        // 統合して時刻順にソート
                        Set<LocalDateTime> combined = new TreeSet<>();
                        combined.addAll(diffLogs);
                        combined.addAll(snapshots);

                        Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () -> {
                            sender.sendMessage("処理完了！UUID: " + (afterUUID - start) + "ms, Logs: " + (afterLogs - afterUUID) + "ms");

                            if (combined.isEmpty()) {
                                sender.sendMessage("ログは見つかりませんでした。");
                            } else {
                                sender.sendMessage(Component.text("[ " + nameOrGroup + " ] のログ一覧:").color(NamedTextColor.YELLOW));
                                for (LocalDateTime log : combined) {
                                    String formatted = log.format(FORMATTER);
                                    boolean isSnapshot = snapshots.contains(log);

                                    NamedTextColor color = isSnapshot ? NamedTextColor.GREEN : NamedTextColor.AQUA;
                                    String command = isSnapshot
                                            ? "/bstorage rollback " + nameOrGroup + " " + formatted
                                            : "/bstorage diff " + nameOrGroup + " " + formatted;
                                    String hoverText = isSnapshot
                                            ? "クリックでロールバックコマンドを入力"
                                            : "クリックで差分表示コマンドを入力";

                                    Component msg = Component.text(" - [ ")
                                            .append(Component.text(formatted)
                                                    .color(color)
                                                    .clickEvent(ClickEvent.suggestCommand(command))
                                                    .hoverEvent(HoverEvent.showText(Component.text(hoverText))))
                                            .append(Component.text(" ]"));

                                    sender.sendMessage(msg);
                                }
                            }
                        });

                        long end = System.currentTimeMillis();
                        Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () ->
                                sender.sendMessage((end - start) + "msで処理しました"));
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

                        boolean success = UnifiedLogManager.saveBackupSnapshot(groupData);
                        Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () -> {
                                    if (success) {
                                        sender.sendMessage("ロールバック用バックアップを保存しました。");
                                    } else {
                                        sender.sendMessage("ロールバック用バックアップを保存できませんでした...");
                                    }
                                }
                                );
                    }
                }.runTaskAsynchronously(BetterStorage.BSPlugin);
                return true;
            }
            case "wipealldata": {
                sender.sendMessage(ChatColor.YELLOW + "全テーブル削除を開始します...");

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement()) {

                            // テーブル削除（順序に注意：子 → 親）
                            String[] tables = {
                                    "inventory_item_summary",
                                    "rollback_log",
                                    "diff_log_inventory_items",
                                    "diff_log_tags",
                                    "inventory_item_log",
                                    "inventory_item_table",
                                    "inventory_table",
                                    "tag_table",
                                    "storage_table",
                                    "group_member_table",
                                    "group_deleted_backup",
                                    "group_table"
                                    // ← ★注意：`inventory_page_snapshot` が含まれてないならCREATE側にもないという判断
                            };

                            for (String table : tables) {
                                stmt.executeUpdate("DROP TABLE IF EXISTS " + table);
                            }

                            // 初期化（テーブルとインデックスを再作成）
                            TableInitializer.initTables();
                            TableInitializer.ensureIndexes(BetterStorage.BSPlugin.getDatabaseManager());

                            Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () ->
                                    sender.sendMessage(ChatColor.GREEN + "[BetterStorage] 全テーブルを削除して再作成しましたにゃ。"));

                        } catch (SQLException e) {
                            Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () ->
                                    sender.sendMessage(ChatColor.RED + "[BetterStorage] テーブル削除または再作成に失敗したにゃ: " + e.getMessage()));
                        }
                    }
                }.runTaskAsynchronously(BetterStorage.BSPlugin);
                return true;
            }

            case "recover": {
                if (args.length < 2) {
                    sender.sendMessage("/bstorage recover <groupUUID>");
                    return true;
                }

                String rawUUID = args[1];
                UUID groupUUID;
                try {
                    groupUUID = UUID.fromString(rawUUID);
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("UUIDの形式が不正です。");
                    return true;
                }

                sender.sendMessage("復元中...");

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        GroupData restored = UnifiedLogManager.loadDeletedGroupBackup(groupUUID);
                        if (restored == null) {
                            Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () ->
                                    sender.sendMessage("指定されたUUIDの削除バックアップが見つかりませんでした。"));
                            return;
                        }


                        if (!DataIO.saveGroupData(restored, null)) {
                            sender.sendMessage("復元保存に失敗しました: " + restored.groupName);
                            return;
                        }

                        Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () -> {
                            sender.sendMessage("グループ「" + restored.groupName + "」を復元しました！");
                        });
                    }
                }.runTaskAsynchronously(BetterStorage.BSPlugin);
                return true;
            }
            case "recoverlist": {
                sender.sendMessage("削除されたグループの一覧を取得中...");

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        List<DeletedGroupInfo> deletedGroups = UnifiedLogManager.getDeletedGroupList();

                        if (deletedGroups.isEmpty()) {
                            Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () ->
                                    sender.sendMessage("削除されたグループは見つかりませんでした。"));
                            return;
                        }

                        Bukkit.getScheduler().runTask(BetterStorage.BSPlugin, () -> {
                            sender.sendMessage(Component.text("削除されたグループ一覧:").color(NamedTextColor.YELLOW));

                            for (DeletedGroupInfo info : deletedGroups) {
                                String label = info.displayName != null ? info.displayName : info.groupName;
                                String uuid = info.groupUUID.toString();
                                String time = info.timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                                Component msg = Component.text(" - [ ")
                                        .append(Component.text(label + " (" + time + ")")
                                                .color(NamedTextColor.AQUA)
                                                .clickEvent(ClickEvent.suggestCommand("/bstorage recover " + uuid))
                                                .hoverEvent(HoverEvent.showText(Component.text("クリックで復元コマンドを入力"))))
                                        .append(Component.text(" ]"))
                                        .color(NamedTextColor.GRAY);

                                sender.sendMessage(msg);
                            }
                        });
                    }
                }.runTaskAsynchronously(BetterStorage.BSPlugin);

                return true;
            }
            case "wipedata": {
                if (args.length < 2) {
                    sender.sendMessage("使用方法: /bstorage wipedata <GroupName/PlayerName>");
                    return true;
                }

                UUID groupUUID;
                try {
                    groupUUID = resolveGroupUUID(args[1]);
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("UUIDの形式が不正です。");
                    return true;
                }

                int code = PendingWipeConfirm.register(sender.getName(), groupUUID);
                sender.sendMessage("確認コードを発行しました。30秒以内に以下のコマンドを実行してください：");
                sender.sendMessage("§e/bstorage confirmwipe " + code);
                return true;
            }
            case "confirmwipe": {
                if (args.length < 2) {
                    sender.sendMessage("使用方法: /bstorage confirmwipe <確認コード4桁>");
                    return true;
                }

                int inputCode;
                try {
                    inputCode = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("確認コードは4桁の数字で入力してください。");
                    return true;
                }

                if (!PendingWipeConfirm.isConfirmed(sender.getName(), inputCode)) {
                    sender.sendMessage("確認がされていません。まず /bstorage wipealldata を実行してください。");
                    return true;
                }

                UUID groupUUID = PendingWipeConfirm.getGroupUUID(sender.getName());
                if (groupUUID == null) {
                    sender.sendMessage("確認情報が見つかりませんでした。再度 /bstorage wipealldata を実行してください。");
                    return true;
                }

                GroupData groupData = DataIO.loadGroupData(groupUUID);
                if (groupData == null) {
                    sender.sendMessage("指定されたグループは見つかりませんでした。");
                    return true;
                }

                UnifiedLogManager.saveDeleteHistory(groupData, sender.getName());
                if (!DataIO.deleteGroupData(groupUUID, groupData.ownerPlugin, groupData.groupName, sender.getName())) {
                    sender.sendMessage("削除中にエラーが発生しました: " + groupData.groupName);
                    return true;
                }

                PendingWipeConfirm.clear(sender.getName());
                sender.sendMessage("グループ「" + groupData.groupName + "」を完全に削除しました。");
                return true;
            }
            case "rollbacklog": {
                if (args.length < 2) {
                    sender.sendMessage("使い方: /bstorage rollbacklog <groupUUID>");
                    return true;
                }

                UUID groupUUID;
                try {
                    groupUUID = resolveGroupUUID(args[1]);
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("UUIDの形式が不正です。");
                    return true;
                }

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        try (Connection conn = db.getConnection();
                             PreparedStatement ps = conn.prepareStatement(
                                     "SELECT timestamp, target_time, plugin_name " +
                                             "FROM rollback_operation_log " +
                                             "WHERE group_uuid = ? ORDER BY timestamp DESC LIMIT 10")) {

                            ps.setString(1, groupUUID.toString());

                            try (ResultSet rs = ps.executeQuery()) {
                                List<String> lines = new ArrayList<>();
                                while (rs.next()) {
                                    String time = rs.getString("timestamp");
                                    String target = rs.getString("target_time");
                                    String plugin = rs.getString("plugin_name");
                                    lines.add("[" + time + "] → " + target + " (" + plugin + ")");
                                }

                                if (lines.isEmpty()) {
                                    sender.sendMessage("ロールバック履歴は見つかりませんでした。");
                                } else {
                                    sender.sendMessage("=== ロールバック履歴 ===");
                                    for (String line : lines) {
                                        sender.sendMessage(line);
                                    }
                                }
                            }
                        } catch (SQLException e) {
                            sender.sendMessage("エラーが発生しました: " + e.getMessage());
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
            // 1. グループ名から取得
            UUID groupUUID = DataIO.getGroupUUIDFromName(input);
            if (groupUUID != null) return groupUUID;

            // 2. UUID形式ならそのまま返す
            try {
                return UUID.fromString(input);
            } catch (IllegalArgumentException ignored) {
            }

            // 3. プレイヤー名 → UUID（オンライン or キャッシュにいるプレイヤー）
            OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(input);
            if (offline != null && (offline.hasPlayedBefore() || offline.isOnline())) {
                return DataIO.getGroupUUIDFromName(offline.getUniqueId().toString());
            }

            return null;
        }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            suggestions.addAll(Arrays.asList("rollback", "rollbacklist", "diff", "difflist", "help", "backup", "rollbacklog", "recover", "recoverlist", "wipedata", "confirmwipe"));
        } else if (args.length == 2 && Arrays.asList("rollback", "rollbacklist", "diff", "difflist" ).contains(args[0].toLowerCase())) {
            suggestions.addAll(
                    DataIO.loadAllGroups().stream()
                            .map(g -> g.groupName)
                            .collect(Collectors.toList())
            );
            for (Player player : Bukkit.getOnlinePlayers()) {
                suggestions.add(player.getName());
            }

        }

        // 第3引数以降の補完は提供しない
        return suggestions;
    }
}
