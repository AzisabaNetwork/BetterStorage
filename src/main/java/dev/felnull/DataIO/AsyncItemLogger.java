package dev.felnull.DataIO;

import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public final class AsyncItemLogger {
    private static final BlockingQueue<ItemLogEvent> QUEUE = new LinkedBlockingQueue<>(5000);
    private static volatile boolean running = false;

    public static void start(DatabaseManager db) {
        if (running) return;
        running = true;
        Thread t = new Thread(() -> loop(db), "BS-ItemLog-Worker");
        t.setDaemon(true);
        t.start();
    }
    public static void stop() { running = false; }
    public static void enqueue(ItemLogEvent ev) { QUEUE.offer(ev); }

    private static void loop(DatabaseManager db) {
        while (running) {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO inventory_item_log " +
                                 "(group_uuid, plugin_name, page_id, slot, operation_type, itemstack, display_name, display_name_plain, material, amount, player_uuid, timestamp) " +
                                 "VALUES (?,?,?,?,?,?,?,?,?,?,?,NOW())")) {
                conn.setAutoCommit(false);

                // 1件待ってからまとめて吸う（50ms or 200件）
                ItemLogEvent first = QUEUE.poll(1, java.util.concurrent.TimeUnit.SECONDS);
                if (first == null) continue;

                int batch = 0;
                long start = System.currentTimeMillis();
                add(ps, first); batch++;

                while (batch < 200 && System.currentTimeMillis() - start < 50) {
                    ItemLogEvent ev = QUEUE.poll();
                    if (ev == null) break;
                    add(ps, ev); batch++;
                }

                ps.executeBatch();
                conn.commit();
            } catch (Exception e) {
                // 少し待って再開（接続枯渇・一時障害に強く）
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                Bukkit.getLogger().warning("[BetterStorage] 非同期ログワーカーエラー: " + e.getMessage());
            }
        }
    }

    private static void add(PreparedStatement ps, ItemLogEvent e) throws SQLException {
        ps.setString(1, e.groupUUID.toString());
        ps.setString(2, e.pluginName);
        ps.setString(3, e.pageId);
        ps.setInt(4, e.slot);
        ps.setString(5, e.operation);
        ps.setString(6, e.itemstack);
        ps.setString(7, e.displayName);
        ps.setString(8, e.displayNamePlain);
        ps.setString(9, e.material);
        ps.setInt(10, e.amount);
        ps.setString(11, e.playerUUID != null ? e.playerUUID.toString() : null);
        ps.addBatch();
    }

    public static final class ItemLogEvent {
        final UUID groupUUID; final String pluginName; final String pageId; final int slot;
        final String operation; final String itemstack; final String displayName; final String displayNamePlain;
        final String material; final int amount; final UUID playerUUID;
        public ItemLogEvent(UUID g, String p, String page, int s, String op, String it, String d, String dp, String m, int a, UUID u) {
            groupUUID=g; pluginName=p; pageId=page; slot=s; operation=op; itemstack=it; displayName=d; displayNamePlain=dp; material=m; amount=a; playerUUID=u;
        }
    }
}