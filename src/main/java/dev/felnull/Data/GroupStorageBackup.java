package dev.felnull.Data;

import java.util.UUID;

public class GroupStorageBackup {
    public UUID groupUUID;
    public StorageData storageData;
    public long version;

    public GroupStorageBackup(UUID groupUUID, StorageData storageData, long version) {
        this.groupUUID = groupUUID;
        this.storageData = storageData;
        this.version = version;
    }
}
