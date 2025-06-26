package dev.felnull.Data;

import java.util.UUID;

public class GroupStorageBackup {
    public UUID groupUUID;
    public StorageDataBackup storageData;
    public long version;

    public GroupStorageBackup(UUID groupUUID, StorageDataBackup storageData, long version) {
        this.groupUUID = groupUUID;
        this.storageData = storageData;
        this.version = version;
    }
}
