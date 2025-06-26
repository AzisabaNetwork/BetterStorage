package dev.felnull.Data;

import java.util.UUID;

public class GroupStorageBackup {
    public UUID groupUUID;
    public StorageDataBackup storageData;

    public GroupStorageBackup(UUID groupUUID, StorageDataBackup storageData) {
        this.groupUUID = groupUUID;
        this.storageData = storageData;
    }
}
