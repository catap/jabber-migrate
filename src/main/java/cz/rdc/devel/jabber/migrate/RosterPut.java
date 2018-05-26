package cz.rdc.devel.jabber.migrate;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.XMPPConnection;
import org.jxmpp.jid.impl.JidCreate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Puts contacts to a roster.
 */
public class RosterPut implements Command {

    private static final Logger LOG = LoggerFactory.getLogger(RosterPut.class);

    private BufferedReader in;

    public RosterPut(BufferedReader in) {
        this.in = in;
    }

    /**
     * Imports contact to the new roster.
     * It first reads all contacts to ensure their validity.
     * They are then imported to the roster.
     * Input file format:
     * nickname;user;[group,group,...]
     */
    public void work(XMPPConnection con) throws Exception {
        Roster roster = Roster.getInstanceFor(con);

        if (!roster.isLoaded()) {
            roster.reloadAndWait();
        }

        List<Contact> contacts = parseContacts();
        for (Contact contact : contacts) {
            if (contact.isRemove()) {
                LOG.info("Removing contact: {}", contact);
                RosterEntry entry = roster.getEntry(JidCreate.bareFrom(contact.getUser()));
                if (entry != null) {
                    roster.removeEntry(entry);
                }
            } else {
                LOG.info("Importing contact: {}", contact);
                roster.createEntry(JidCreate.bareFrom(contact.getUser()), contact.getNickname(), contact.getGroups());
            }
        }

        waitForRosterUpdate(roster, contacts);
    }

    /**
     * Ensures that all the new contacts are in roster before quit.
     * See the issue: http://www.jivesoftware.org/issues/browse/SMACK-10
     */
    @SuppressWarnings("unchecked")
    private void waitForRosterUpdate(Roster roster, List<Contact> contacts) throws InterruptedException, SmackException.NotLoggedInException, SmackException.NotConnectedException {
        Set<String> newUsers = new HashSet<String>();
        for (Contact contact : contacts) {
            if (!contact.isRemove()) {
                newUsers.add(contact.getUser());
            }
        }

        while (newUsers.size() > 0) {
            roster.reloadAndWait();
            for (RosterEntry entry : roster.getEntries()) {
                newUsers.remove(entry.getUser());
            }

            LOG.info("Waiting for roster update: {}/{}",
                    contacts.size() - newUsers.size(), contacts.size());
        }
    }

    private List<Contact> parseContacts() throws IOException {
        List<Contact> contacts = new ArrayList<Contact>();

        String line;
        while ((line = in.readLine()) != null) {
            String[] parts = Re.deformat("([+-]);([^;]*);([^;]+);\\[([^\\]]*)\\]", line);
            Contact contact = new Contact();
            contact.setRemove("-".equals(parts[0]));
            contact.setNickname("".equals(parts[1]) ? null : parts[1]);
            contact.setUser(parts[2]);

            String groups = parts[3];
            for (String group : groups.split(",")) {
                if (group.length() > 0) {
                    contact.addGroup(group);
                }
            }

            contacts.add(contact);
        }
        return contacts;
    }
}
