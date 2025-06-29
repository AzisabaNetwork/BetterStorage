package dev.felnull.Data;

import java.time.LocalDateTime;
import java.util.UUID;

public class DeletedGroupInfo {
    public UUID groupUUID;
    public String groupName;
    public String displayName;
    public LocalDateTime timestamp;

    public DeletedGroupInfo(UUID groupUUID, String groupName, String displayName, LocalDateTime timestamp) {
        this.groupUUID = groupUUID;
        this.groupName = groupName;
        this.displayName = displayName;
        this.timestamp = timestamp;
    }
}
