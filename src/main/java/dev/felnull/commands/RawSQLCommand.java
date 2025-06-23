package dev.felnull.commands;

import dev.felnull.DataIO.DatabaseManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.sql.*;

public class RawSQLCommand implements CommandExecutor {
    private final DatabaseManager db;

    public RawSQLCommand(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "SQL文を指定してください！");
            return false;
        }

        String sql = String.join(" ", args);

        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement()) {

            String lowerSQL = sql.trim().toLowerCase();

            if (lowerSQL.startsWith("select") || lowerSQL.startsWith("show") || lowerSQL.startsWith("desc")) {
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();

                    // ヘッダ行
                    StringBuilder header = new StringBuilder(ChatColor.YELLOW + "[結果]");
                    for (int i = 1; i <= columnCount; i++) {
                        header.append(" | ").append(meta.getColumnLabel(i));
                    }
                    sender.sendMessage(header.toString());

                    // データ行
                    int rowCount = 0;
                    while (rs.next() && rowCount < 10) { // 上限10行まで表示（多すぎ防止）
                        StringBuilder row = new StringBuilder();
                        for (int i = 1; i <= columnCount; i++) {
                            row.append(" | ").append(rs.getString(i));
                        }
                        sender.sendMessage(row.toString());
                        rowCount++;
                    }

                    if (rs.next()) {
                        sender.sendMessage(ChatColor.GRAY + "...さらに結果があります（10行のみ表示）");
                    }

                }
            } else {
                int rows = stmt.executeUpdate(sql);
                sender.sendMessage(ChatColor.GREEN + "SQL実行成功にゃ！影響行数: " + rows);
            }

        } catch (SQLException e) {
            sender.sendMessage(ChatColor.RED + "SQL実行エラー: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }
}