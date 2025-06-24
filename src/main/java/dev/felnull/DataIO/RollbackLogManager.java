package dev.felnull.DataIO;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.felnull.BetterStorage;
import dev.felnull.Data.GroupData;
import dev.felnull.Data.GroupStorageBackup;
import org.bukkit.Bukkit;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class RollbackLogManager {
    private static final Gson gson = new Gson();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static DatabaseManager db = BetterStorage.BSPlugin.getDatabaseManager();

    public static void saveRollbackLog(GroupData groupData) {
        try (Connection conn = db.getConnection()) {
            String sql = "INSERT INTO rollback_log (group_uuid, timestamp, json_data) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                GroupStorageBackup backup = new GroupStorageBackup(groupData.groupUUID, groupData.storageData, groupData.version);

                // データ圧縮
                String json = gson.toJson(backup);
                byte[] compressed = compress(json);

                ps.setString(1, groupData.groupUUID.toString());
                ps.setString(2, LocalDateTime.now().format(FORMATTER));
                ps.setBytes(3, compressed); // BLOBで保存
                ps.executeUpdate();
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[BetterStorage] ロールバックログ保存失敗: " + e.getMessage());
        }
    }

    public static boolean restoreGroupFromRollback(UUID groupUUID, LocalDateTime timestamp) {
        try (Connection conn = db.getConnection()) {
            String sql = "SELECT json_data FROM rollback_log WHERE group_uuid = ? AND timestamp = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, groupUUID.toString());
                ps.setString(2, timestamp.format(FORMATTER));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        byte[] compressed = rs.getBytes("json_data");
                        String json = decompress(compressed);

                        GroupStorageBackup backup = gson.fromJson(json, GroupStorageBackup.class);
                        GroupData group = GroupData.resolveByUUID(groupUUID);

                        if (group == null) return false;

                        backup.storageData.attach(group);
                        group.storageData = backup.storageData;
                        group.version = backup.version;

                        return DataIO.saveGroupData(group, backup.version);
                    }
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[BetterStorage] ロールバック失敗: " + e.getMessage());
        }
        return false;
    }

    public static List<LocalDateTime> getRollbackTimestamps(String groupUUID) {
        List<LocalDateTime> timestamps = new ArrayList<>();
        try (Connection conn = db.getConnection()) {
            String sql = "SELECT timestamp FROM rollback_log WHERE group_uuid = ? ORDER BY timestamp DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, groupUUID);
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

    public static Set<String> getAllGroupUUIDs() {
        Set<String> groupUUIDs = new HashSet<>();
        try (Connection conn = db.getConnection()) {
            String sql = "SELECT DISTINCT group_uuid FROM rollback_log";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    groupUUIDs.add(rs.getString("group_uuid"));
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().warning("[BetterStorage] グループUUID一覧取得失敗: " + e.getMessage());
        }
        return groupUUIDs;
    }

    // 圧縮してバイナリ化
    public static byte[] compress(String str) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(byteStream)) {
            gzip.write(str.getBytes(StandardCharsets.UTF_8));
        }
        return byteStream.toByteArray();
    }

    // 展開して文字列化
    public static String decompress(byte[] compressed) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
             InputStreamReader reader = new InputStreamReader(gzip, StandardCharsets.UTF_8);
             BufferedReader in = new BufferedReader(reader)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }
}