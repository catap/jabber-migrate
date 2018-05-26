package cz.rdc.devel.jabber.migrate;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.XMPPConnection;
import org.jxmpp.jid.impl.JidCreate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.BufferedReader;
import java.util.*;

/**
 * Puts contacts to a roster.
 */
public class RosterPut implements Command {

    private static final Logger LOG = LoggerFactory.getLogger(RosterPut.class);

    private BufferedReader in;

    private boolean adiumFormat;

    public RosterPut(BufferedReader in, boolean adiumFormat) {
        this.in = in;
        this.adiumFormat = adiumFormat;
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

        Collection<Contact> contacts = parseContacts();
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
    private void waitForRosterUpdate(Roster roster, Collection<Contact> contacts) throws InterruptedException, SmackException.NotLoggedInException, SmackException.NotConnectedException {
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

    private Collection<Contact> parseContacts() throws Exception {
        if (adiumFormat) {
            return parseAdiumContacts();
        }
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

    private Collection<Contact> parseAdiumContacts() throws Exception  {
        Document document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(new InputSource(in));

        Map<String, Contact> contactMap = new HashMap<>();
        XPath xPath =  XPathFactory.newInstance().newXPath();

        NodeList groups = (NodeList) xPath.evaluate("/purple/blist/group", document, XPathConstants.NODESET);
        for (int i = 0; i < groups.getLength(); i++) {
            Node group = groups.item(i);
            String groupName = (String) xPath.evaluate("@name", group, XPathConstants.STRING);
            NodeList buddies = (NodeList) xPath.evaluate("contact/buddy[@proto='prpl-jabber']", group, XPathConstants.NODESET);
            for (int j = 0; j < buddies.getLength(); j++) {
                Node buddy = buddies.item(j);
                String jid = (String) xPath.evaluate("name", buddy, XPathConstants.STRING);
                String alias = (String) xPath.evaluate("alias", buddy, XPathConstants.STRING);
                Contact contact = contactMap.computeIfAbsent(jid, (a) -> new Contact());
                contact.setUser(jid);
                contact.setNickname(alias);
                contact.setRemove(false);
                if (groupName != null) {
                    contact.addGroup(groupName);
                }
            }
        }

        if (contactMap.isEmpty()) {
            LOG.error("Incorrect format or it doesn't contain any buddy");
        }

        return contactMap.values();
    }
}
