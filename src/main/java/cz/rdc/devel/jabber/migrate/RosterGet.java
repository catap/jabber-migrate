package cz.rdc.devel.jabber.migrate;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.RosterGroup;
import org.jivesoftware.smack.roster.RosterListener;
import org.jivesoftware.smack.util.DNSUtil;
import org.jivesoftware.smack.util.dns.HostAddress;
import org.jivesoftware.smackx.iqregister.AccountManager;
import org.jivesoftware.smackx.iqversion.VersionManager;
import org.jivesoftware.smackx.ping.PingManager;
import org.jivesoftware.smackx.time.EntityTimeManager;
import org.jivesoftware.smackx.vcardtemp.VCardManager;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.parts.Domainpart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;

/**
 * Exports contacts from a roster.
 */
@Parameters(commandDescription = "Export roster into specified file")
public class RosterGet {
    private static final Logger LOG = LoggerFactory.getLogger(RosterGet.class);

    @Parameter(names = {"-f", "--file"},
        description = "Roster file path by default is stdout/stdin")
    private String file;

    @Parameter(names = {"--onlyUnreachable"},
        description = "Creates a list of users that can't be reach anymore")
    private boolean onlyUnreachable;

    final XMPPConnectionFactory connectionFactory;

    public RosterGet(XMPPConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @SuppressWarnings("unchecked")
    public void work(XMPPConnection con) throws Exception {
        Roster roster = Roster.getInstanceFor(con);

        while (!roster.isLoaded()) {
            roster.reloadAndWait();
        }

        Set<Domainpart> unavailableDomain = new HashSet<>();
        Set<Domainpart> availableDomain = new HashSet<>();

        Set<RosterEntry> rosterEntries = roster.getEntries();
        int passed = 0;

        PrintStream out = IOSupport.createOutput(file);

        for (RosterEntry entry : rosterEntries) {
            passed++;
            BareJid jid = entry.getJid();

            Contact contact = new Contact();
            contact.setNickname(entry.getName());
            contact.setUser(jid.toString());

            for (RosterGroup group : entry.getGroups()) {
                contact.addGroup(group.getName());
            }

            if (onlyUnreachable) {
                LOG.info("Checking {}, {}/{}", jid, passed, rosterEntries.size());
                if (!isJidReachable(con, roster, jid, availableDomain, unavailableDomain)) {
                    contact.setRemove(true);
                } else {
                    continue;
                }
            }

            out.println(contact);
        }
    }

    private boolean isJidReachable(XMPPConnection con, Roster roster, BareJid jid, Set<Domainpart> availableDomain, Set<Domainpart> unavailableDomain) throws SmackException.NotConnectedException, InterruptedException {
        if (unavailableDomain.contains(jid.getDomain())) {
            return false;
        }

        try {
            Presence presence = roster.getPresence(jid);
            if (presence.getType() == Presence.Type.error) {
                if (presence.getError() != null) {
                    throw new XMPPException.XMPPErrorException(presence, presence.getError());
                }
                LOG.info("JID: " + jid.toString() + " has presence error: " + presence.toString());
                return false;
            }

            // try send ping to jid
            try {
                if (PingManager.getInstanceFor(con).ping(jid)) {
                    return true;
                }
            } catch (SmackException.NoResponseException ignore) {

            }

            try {
                // does jid support vcard?
                if (VCardManager.getInstanceFor(con).isSupported(jid)) {
                    return true;
                }
            } catch (SmackException.NoResponseException ignore) {

            }

            try {
                // does jid support version?
                if (VersionManager.getInstanceFor(con).isSupported(jid)) {
                    return true;
                }
            } catch (SmackException.NoResponseException ignore) {

            }

            try {
                // does jid support time?
                if (EntityTimeManager.getInstanceFor(con).isTimeSupported(jid)) {
                    return true;
                }
            } catch (SmackException.NoResponseException ignore) {

            }

        } catch (XMPPException.XMPPErrorException e) {
            switch (e.getXMPPError().getCondition()) {
                case remote_server_not_found:
                    unavailableDomain.add(jid.getDomain());
                    return false;

                case recipient_unavailable:
                    return false;

                case subscription_required:
                case service_unavailable:
                    break;

                default:
                    LOG.info("Unhandled error happened: " + e.getMessage());
            }
        }

        if (!availableDomain.contains(jid.getDomain())) {
            // oky, try to s2s connection
            List<HostAddress> resolved = new ArrayList<>();
            String domain = jid.getDomain().toString();

            // direct connection to domain:5259
            try {
                InetAddress[] inetAddresses = InetAddress.getAllByName(domain);
                if (inetAddresses != null) {
                    resolved.add(new HostAddress(domain, 5269, Arrays.asList(inetAddresses)));
                }
            } catch (UnknownHostException ignore) {

            }

            // s2s over SRV records
            try {
                resolved.addAll(DNSUtil.resolveXMPPServerDomain(domain, null, ConnectionConfiguration.DnssecMode.disabled));
            } catch (Exception ignore) {
                // can't resolve domains or something very bad happened, ignore it
            }

            boolean reached = false;
            for (HostAddress hostAddress : resolved) {
                if (reached) {
                    break;
                }
                for (InetAddress inetAddress : hostAddress.getInetAddresses()) {
                    try {
                        Socket socket = new Socket();
                        socket.connect(new InetSocketAddress(inetAddress, hostAddress.getPort()));
                        socket.close();
                        reached = true;
                        break;
                    } catch (IOException ignore) {
                        // error happened
                    }
                }
            }

            if (!reached) {
                // I can't reach this domain at all
                unavailableDomain.add(jid.getDomain());
                return false;
            } else {
                availableDomain.add(jid.getDomain());
            }
        }

        // server is available, try to register jid that we're checking
        // if it happened => remove registered jid and marked to remove from our roster
        AbstractXMPPConnection subConn = null;
        try {
            subConn = connectionFactory.connect(jid.getDomain().toString());
            AccountManager accountManager = AccountManager.getInstance(subConn);
            if (accountManager.isSupported()) {
                // don't create account that spammers may use
                String password = UUID.randomUUID().toString();
                accountManager.createAccount(jid.getLocalpartOrNull(), password);

                subConn.login(jid.getLocalpartOrNull().asUnescapedString(), password);
                accountManager.deleteAccount();
                LOG.info("JID {} is free to register!", jid.toString());
                return false;
            }

        } catch (XMPPException.XMPPErrorException e) {
            switch (e.getXMPPError().getCondition()) {
                case not_allowed:
                case forbidden:
                    break;

                default:
                    LOG.info("Unhandled error happened on sub connection: " + e.getMessage());
            }
        } catch (SmackException | XMPPException | IOException e) {
            // can't connect to server, or jit existed
            LOG.info("Unhandled error happened on sub connection: " + e.getMessage());
        } finally {
            if (subConn != null) {
                subConn.disconnect();
            }
        }

        return true;
    }
}
