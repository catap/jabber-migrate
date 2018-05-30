package cz.rdc.devel.jabber.migrate;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smack.util.stringencoder.Base64;
import org.jivesoftware.smackx.ping.PingManager;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

public class XMPPConnectionFactory {

    private static final Logger LOG = LoggerFactory.getLogger(XMPPConnectionFactory.class);

    private static SSLContext sslContext = null;

    static {
        Base64.setEncoder(new Base64.Encoder() {
            @Override
            public byte[] decode(String string) {
                return java.util.Base64.getDecoder().decode(string);
            }

            @Override
            public byte[] decode(byte[] input, int offset, int len) {
                return java.util.Base64.getDecoder().decode(Arrays.copyOfRange(input, offset, len));
            }

            @Override
            public String encodeToString(byte[] input, int offset, int len) {
                return new String(encode(input, offset, len));
            }

            @Override
            public byte[] encode(byte[] input, int offset, int len) {
                return java.util.Base64.getEncoder().encode(Arrays.copyOfRange(input, offset, len));
            }
        });

        TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
                public void checkClientTrusted(
                    java.security.cert.X509Certificate[] certs, String authType) {
                }
                public void checkServerTrusted(
                    java.security.cert.X509Certificate[] certs, String authType) {
                }
            }
        };

        try {
            sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            LOG.error("Can't create SSL context that will trust any certificate: " + e.getMessage(), e);
        }
    }

    public static class String2BareJidValidate implements IParameterValidator {
        @Override
        public void validate(String name, String value) throws ParameterException {
            try {
                JidCreate.bareFrom(value);
            } catch (XmppStringprepException e) {
                throw new ParameterException(e.getCausingString());
            }
        }
    }

    public static class String2BareJid implements IStringConverter<BareJid> {
        @Override
        public BareJid convert(String value) {
            try {
                return JidCreate.bareFrom(value);
            } catch (XmppStringprepException e) {
                // never happened
                return null;
            }
        }
    }

    @Parameter(names = {"--jid", "-j"}, required = true,
        converter = String2BareJid.class, validateWith = String2BareJidValidate.class,
        description = "JID to perform an action")
    public BareJid jid;

    @Parameter(names = {"--password", "-w"},
        description = "password to connect as provided JID", password = true, required = true)
    public String password;

    @Parameter(names = {"--host", "--H"},
        description = "Host to overwrite value from SRV records")
    public String host;

    @Parameter(names = {"--port", "-p"},
        description = "Port that will be used when host is overwritten from SRV records")
    public int port = 5222;

    @Parameter(names = {"--debug"},
        description = "dump all send and received stanzas")
    public boolean debug;

    public AbstractXMPPConnection connectAndLogin() throws IOException, InterruptedException, XMPPException, SmackException {
        XMPPTCPConnectionConfiguration.Builder builder = XMPPTCPConnectionConfiguration.builder();

        if (host == null || host.isEmpty()) {
            builder = builder
                .setHost(host)
                .setPort(port);
        }

        XMPPTCPConnectionConfiguration config = builder
            .setXmppDomain(jid.getDomain().toString())
            .setSecurityMode(ConnectionConfiguration.SecurityMode.ifpossible)
            .setHostnameVerifier((s, sslSession) -> true)
            .setCustomSSLContext(sslContext)
            .setDebuggerEnabled(debug)
            .build();

        AbstractXMPPConnection conn = new XMPPTCPConnection(config);
        conn.setParsingExceptionCallback(stanzaData -> LOG.warn("Can't parse: " + stanzaData.getContent()));
        conn.connect();

        try {
            conn.login(jid.getLocalpartOrNull(), password);

            PingManager pm = PingManager.getInstanceFor(conn);
            pm.setPingInterval(5);
            pm.pingMyServer();
            pm.registerPingFailedListener(() -> LOG.error("pingFailed"));

            return conn;
        } catch (IOException | InterruptedException | XMPPException | SmackException e) {
            conn.disconnect();
            throw e;
        }
    }

    public AbstractXMPPConnection connect(String serviceName) throws IOException, InterruptedException, XMPPException, SmackException {
        XMPPTCPConnectionConfiguration config = XMPPTCPConnectionConfiguration.builder()
            .setXmppDomain(serviceName)
            .setSecurityMode(ConnectionConfiguration.SecurityMode.ifpossible)
            .setHostnameVerifier((s, sslSession) -> true)
            .setCustomSSLContext(sslContext)
            .setDebuggerEnabled(debug)
            .build();

        AbstractXMPPConnection conn = new XMPPTCPConnection(config);
        conn.setParsingExceptionCallback(stanzaData -> LOG.warn("Can't parse: " + stanzaData.getContent()));
        conn.connect();

        return conn;
    }
}
