package dev.felnull;

import dev.felnull.DataIO.DatabaseManager;
import dev.felnull.task.ItemLogSummaryTask;
import dev.felnull.task.LogCleanerTask;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class LogCleanerScheduler {
    private static BukkitTask task;
    private static BukkitTask task2;

    public static void schedule(DatabaseManager db) {
        long delay = getInitialDelayToMidnight();
        long period = 20L * 60 * 60 * 24; // 24時間ごと

        task = new LogCleanerTask(db).runTaskTimerAsynchronously(
                BetterStorage.BSPlugin,
                delay,
                period
        );
        task2 = new ItemLogSummaryTask(db).runTaskTimerAsynchronously(
                BetterStorage.BSPlugin,
                delay,
                period
        );

    }

    // 今から次の午前0時までの ticks を返す
    private static long getInitialDelayToMidnight() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Tokyo"));
        ZonedDateTime nextMidnight = now.plusDays(1).toLocalDate().atStartOfDay(ZoneId.of("Asia/Tokyo"));
        Duration duration = Duration.between(now, nextMidnight);
        return duration.getSeconds() * 20; // 秒 → ticks
    }

    public static void cancelTask() {
        task.cancel();
        task2.cancel();
    }
}