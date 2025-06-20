package dev.felnull;

import dev.felnull.DataIO.DatabaseManager;
import dev.felnull.task.LogCleanerTask;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class LogCleanerScheduler {
    public static void schedule(DatabaseManager db) {
        long delay = getInitialDelayToMidnight();
        long period = 20L * 60 * 60 * 24; // 24時間ごと

        new LogCleanerTask(db).runTaskTimerAsynchronously(
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
}