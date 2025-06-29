package dev.felnull.commands;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PendingWipeConfirm {

    private static final Map<String, PendingEntry> pendingMap = new HashMap<>();
    private static final long TIMEOUT_MILLIS = 30 * 1000; // 30秒

    public static int register(String playerName, UUID groupUUID) {
        int confirmCode = (int)(Math.random() * 9000) + 1000; // 1000〜9999
        pendingMap.put(playerName.toLowerCase(), new PendingEntry(groupUUID, System.currentTimeMillis(), confirmCode));
        return confirmCode;
    }

    public static boolean isConfirmed(String playerName, int inputCode) {
        PendingEntry entry = pendingMap.get(playerName.toLowerCase());
        if (entry == null) return false;

        long now = System.currentTimeMillis();
        if (now - entry.timestamp > TIMEOUT_MILLIS) {
            pendingMap.remove(playerName.toLowerCase());
            return false;
        }

        return entry.confirmCode == inputCode;
    }

    public static UUID getGroupUUID(String playerName) {
        PendingEntry entry = pendingMap.get(playerName.toLowerCase());
        return (entry != null) ? entry.groupUUID : null;
    }

    public static void clear(String playerName) {
        pendingMap.remove(playerName.toLowerCase());
    }

    private static class PendingEntry {
        UUID groupUUID;
        long timestamp;
        int confirmCode;

        public PendingEntry(UUID groupUUID, long timestamp, int confirmCode) {
            this.groupUUID = groupUUID;
            this.timestamp = timestamp;
            this.confirmCode = confirmCode;
        }
    }
}
