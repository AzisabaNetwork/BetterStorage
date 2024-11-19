package dev.felnull.Data;


public enum GroupPermENUM {

    OWNER("owner"),
    MANAGER("manager"),
    MEMBER("member");

    private final String permName;

    GroupPermENUM(String permName) {
        this.permName = permName;
    }

    public String getPermName() {
        return permName;
    }

    public String fromPermName(String perm) {
        for (GroupPermENUM groupPermENUM : GroupPermENUM.values()){
            if(groupPermENUM.getPermName().equalsIgnoreCase(perm)){
                return groupPermENUM.getPermName();
            }
        }
        return null;
    }
}
