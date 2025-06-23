package dev.felnull.DataIO;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.felnull.BetterStorage;
import dev.felnull.Data.GroupData;
import org.bukkit.Bukkit;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RollbackLogManager {
    private static final Gson gson = new Gson();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static DatabaseManager db = BetterStorage.BSPlugin.getDatabaseManager();

    public static void saveRollbackLog(GroupData groupData) {
        try (Connection conn = db.getConnection()) {
            String sql = "INSERT INTO rollback_log (group_name, timestamp, json_data) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, groupData.groupName);
                ps.setString(2, LocalDateTime.now().format(FORMATTER));
                ps.setString(3, gson.toJson(groupData));
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            Bukkit.getLogger().warning("[BetterStorage] ロールバックログ保存失敗: " + e.getMessage());
        }
    }

    public static boolean restoreGroupFromRollback(String groupName, LocalDateTime timestamp) {
        try (Connection conn = db.getConnection()) {
            String sql = "SELECT json_data FROM rollback_log WHERE group_name = ? AND timestamp = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, groupName);
                ps.setString(2, timestamp.format(FORMATTER));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String json = rs.getString("json_data");
                        Type type = new TypeToken<GroupData>() {}.getType();
                        GroupData restoredData = gson.fromJson(json, type);
                        DataIO.saveGroupData(restoredData, restoredData.version);
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().warning("[BetterStorage] ロールバック失敗: " + e.getMessage());
        }
        return false;
    }

    public static List<LocalDateTime> getRollbackTimestamps(String groupName) {
        List<LocalDateTime> timestamps = new ArrayList<>();
        try (Connection conn = db.getConnection()) {
            String sql = "SELECT timestamp FROM rollback_log WHERE group_name = ? ORDER BY timestamp DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, groupName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        timestamps.add(LocalDateTime.parse(rs.getString("timestamp"), FORMATTER));
                    }
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().warning("[BetterStorage] ログ取得失敗: " + e.getMessage());
        }
        return timestamps;
    }

    public static Set<String> getAllGroupNames() {
        Set<String> groupNames = new HashSet<>();
        try (Connection conn = db.getConnection()) {
            String sql = "SELECT DISTINCT group_name FROM rollback_log";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    groupNames.add(rs.getString("group_name"));
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().warning("[BetterStorage] グループ名一覧取得失敗: " + e.getMessage());
        }
        return groupNames;
    }
}