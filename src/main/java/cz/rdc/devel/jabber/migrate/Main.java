package cz.rdc.devel.jabber.migrate;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smackx.ping.PingManager;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static java.lang.System.*;

public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(RosterPut.class);

    @Argument(required=true,
             metaVar="MODE", usage="export, or import")
    private List<String> mode = new ArrayList<String>();

    @Option(name="-u", aliases="--username", required=true, metaVar="NAME",
            usage="Username; usually the portion of JID before at (@) sign, but some severs uses whole JID as an username.")
    private String username;

    @Option(name="-s", aliases="--service", required=true, metaVar="HOST",
            usage="Service (domain) name; the portion of JID after at (@) sign.")
    private String serviceName;

    @Option(name="-w", aliases="--password", required=true,
            metaVar="PASS", usage="Password")
    private String password;

    @Option(name="-h", aliases="--host", metaVar="HOST",
            usage="Server hostname (default is using SRV record)")
    private String host;

    @Option(name="-p", aliases="--port",
            metaVar="PORT", usage="Server port if server specified (default is 5222)")
    private int port = 5222;

    @Option(name="-f", aliases="--file",
            metaVar="PATH", usage="Roster file path (default is stdout/stdin)")
    private String file;

    @Option(name="--adium",
        usage="Roster file path in Adium (blist.xml) format, import only (default is off)")
    private boolean adiumFormat;

    @Option(name="--onlyUnreachable",
        usage="Creates a list fo users that can't be reach anymore, only export (default is off)")
    private boolean onlyUnreachable;

    @Option(name="--help", usage="Show help")
    private boolean help;


    public static void main(String[] args) throws Exception {
        new Main().doMain(args);
    }


    public void doMain(String[] args) throws Exception {
        CmdLineParser parser = new CmdLineParser(this);

        try {
            parser.parseArgument(args);

        } catch (CmdLineException ex) {
            if (help || args.length == 0) {
                printUsage(parser);
                exit(0);
            } else {
                LOG.error("Error: " + ex.getMessage());
                exit(1);
            }
        }

        Command command = null;
        if (mode.contains("export")) {
            command = new RosterGet(IOSupport.createOutput(file), onlyUnreachable);

        } else if (mode.contains("import")) {
            command = new RosterPut(IOSupport.createInput(file), adiumFormat);

        } else {
            LOG.error("Unknown mode: " + mode.get(0));
            exit(1);
        }

        XMPPConnection conn = XMPPConnectionFactory.connectAndLogin(username, serviceName, password, host, port);
        command.work(conn);
    }

    private void printUsage(CmdLineParser parser) {
        out.println();
        out.println("Usage:");
        parser.printUsage(out);
        out.println();
        out.println("Example:");
        out.println(" roster-migrate export -u kevin@flynn.com -w top-secret -h talk.google.com -p 5222 -f export.txt");
    }
}
