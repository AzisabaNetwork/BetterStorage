package dev.felnull.commands;

import dev.felnull.DataIO.DatabaseManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CheckDBCommand implements CommandExecutor {

    private final DatabaseManager db;

    public CheckDBCommand(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage("§e[BetterStorage] DB整合性チェックを開始しますにゃ…");

        try (Connection conn = db.getConnection()) {
            // storage_table に存在する group_uuid で、group_table に存在しないものを探す
            String sql = "SELECT s.group_uuid FROM storage_table s LEFT JOIN group_table g ON s.group_uuid = g.group_uuid WHERE g.group_uuid IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {

                List<String> brokenUUIDs = new ArrayList<>();
                while (rs.next()) {
                    brokenUUIDs.add(rs.getString("group_uuid"));
                }

                if (brokenUUIDs.isEmpty()) {
                    sender.sendMessage("§a整合性チェック完了：異常なしにゃ！");
                } else {
                    sender.sendMessage("§c破損データを検出！以下の group_uuid に対応するグループが存在しないにゃ：");
                    for (String uuid : brokenUUIDs) {
                        sender.sendMessage(" - §e" + uuid);
                    }
                }

            }
        } catch (SQLException e) {
            sender.sendMessage("§cエラーが発生したにゃ：" + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }
}
