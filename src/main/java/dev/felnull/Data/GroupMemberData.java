package dev.felnull.Data;

import java.util.UUID;

public class GroupMemberData {
    public UUID memberUUID;
    public String[] role; // ← String にしないよう注意

    public GroupMemberData(UUID memberUUID, String[] role) {
        this.memberUUID = memberUUID;
        this.role = role;
    }
}