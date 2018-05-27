package cz.rdc.devel.jabber.migrate;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jxmpp.jid.BareJid;
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
@Parameters(commandDescription = "Import existed file into roster")
public class RosterPut {

    private static final Logger LOG = LoggerFactory.getLogger(RosterPut.class);

    @Parameter(names = {"-f", "--file"},
        description = "Roster file path by default is stdout/stdin")
    private String file;

    @Parameter(names = {"--adium"},
        description = "Roster file in Adium (blist.xml) format")
    private boolean adiumFormat;

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

        Set<BareJid> modified = new HashSet<>();
        Collection<Contact> contacts = parseContacts();
        boolean isSubscriptionPreApprovalSupported = roster.isSubscriptionPreApprovalSupported();
        for (Contact contact : contacts) {
            if (contact.isRemove()) {
                LOG.info("Removing contact: {}", contact);
                RosterEntry entry = roster.getEntry(JidCreate.bareFrom(contact.getUser()));
                if (entry != null) {
                    roster.removeEntry(entry);
                }
            } else {
                LOG.info("Importing contact: {}", contact);
                BareJid jid = JidCreate.bareFrom(contact.getUser());
                if (isSubscriptionPreApprovalSupported) {
                    roster.preApproveAndCreateEntry(jid, contact.getNickname(), contact.getGroups());
                } else {
                    roster.createEntry(jid, contact.getNickname(), contact.getGroups());
                }
                modified.add(jid);
            }
        }

        waitForRosterUpdate(roster, contacts);

        if (!modified.isEmpty()) {
            for (BareJid jid : modified) {
                RosterEntry entry = roster.getEntry(jid);
                if (!entry.canSeeHisPresence() || !isSubscriptionPreApprovalSupported) {
                    LOG.info("Sending auth request to: {}", jid.toString());
                    roster.sendSubscriptionRequest(jid);
                    // if we're send presence sleep for 1 seconds to overstep antispam on major hosting
                    Thread.sleep(1000);
                }
            }
        }
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
        BufferedReader in = IOSupport.createInput(file);
        if (adiumFormat) {
            return parseAdiumContacts(in);
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

    private Collection<Contact> parseAdiumContacts(BufferedReader in) throws Exception  {
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
