package cz.rdc.devel.jabber.migrate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Contact information holder.
 */
public class Contact {

    private boolean remove;
    private String nickname;
    private String user;
    private Set<String> groups = new HashSet<>();

    public void addGroup(String group) {
        groups.add(group);
    }

    public String[] getGroups() {
        return groups.toArray(new String[0]);
    }

    public String getUser() {
        return user;
    }
    public void setUser(String user) {
        this.user = user;
    }
    public String getNickname() {
        return nickname;
    }
    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
    public boolean isRemove() {
        return remove;
    }
    public void setRemove(boolean remove) {
        this.remove = remove;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String group : groups) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(group);
        }
        return (remove ? "-" : "+")
            + ";" + (nickname == null ? "" : nickname)
            + ";" + user + ";[" + sb + "]";
    }
}
