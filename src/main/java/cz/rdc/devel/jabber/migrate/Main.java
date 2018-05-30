package cz.rdc.devel.jabber.migrate;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;

import java.io.IOException;

public class Main {
    @Parameter(names = {"--help", "-H"}, help = true,
        description = "Display help")
    private boolean help;

    public static void main(String[] args) throws Exception {
        Main main = new Main();
        XMPPConnectionFactory connectionFactory = new XMPPConnectionFactory();
        RosterPut rosterPut = new RosterPut();
        RosterGet rosterGet = new RosterGet(connectionFactory);

        JCommander jct = JCommander.newBuilder()
            .addObject(main)
            .addObject(connectionFactory)
            .addCommand("import", rosterPut)
            .addCommand("export", rosterGet)
            .build();

        jct.setProgramName("roster-migrate");
        try {
            jct.parse(args);
        } catch (ParameterException e) {
            JCommander.getConsole().println(e.getMessage());
            jct.usage();
            return;
        }

        if (main.help) {
            jct.usage();
            return;
        }

        String command = jct.getParsedCommand();
        if (command == null) {
            JCommander.getConsole().println("Please, specified command: export or import");
            jct.usage();
            return;
        }

        AbstractXMPPConnection conn;
        try {
            conn = connectionFactory.connectAndLogin();
        } catch (IOException e) {
            System.err.println("Can't connect to server: " + e.getMessage());
            return;
        } catch (InterruptedException e) {
            return;
        } catch (XMPPException | SmackException e) {
            System.err.println(e.getMessage());
            return;
        }

        switch (command) {
            case "import":
                rosterPut.work(conn);
                break;

            case "export":
                rosterGet.work(conn);
                break;
        }

        conn.disconnect();
    }
}
