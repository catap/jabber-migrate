package cz.rdc.devel.jabber.migrate;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smack.util.stringencoder.Base64;
import org.jivesoftware.smackx.ping.PingManager;
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

    public static XMPPConnection connectAndLogin(String username, String serviceName, String password, String host, Integer port) throws IOException, InterruptedException, XMPPException, SmackException {
        XMPPTCPConnectionConfiguration.Builder builder = XMPPTCPConnectionConfiguration.builder();

        if (host == null || host.isEmpty()) {
            builder = builder
                .setHost(host)
                .setPort(port);
        }

        XMPPTCPConnectionConfiguration config = builder
            .setXmppDomain(serviceName)
            .setSecurityMode(ConnectionConfiguration.SecurityMode.ifpossible)
            .setHostnameVerifier((s, sslSession) -> true)
            .setCustomSSLContext(sslContext)
            .build();

        AbstractXMPPConnection conn = new XMPPTCPConnection(config)
            .connect();

        conn.login(username, password);

        PingManager pm = PingManager.getInstanceFor(conn);
        pm.setPingInterval(5);
        pm.pingMyServer();
        pm.registerPingFailedListener(() -> LOG.error("pingFailed"));

        return conn;
    }

    public static AbstractXMPPConnection connect(String serviceName) throws IOException, InterruptedException, XMPPException, SmackException {
        XMPPTCPConnectionConfiguration config = XMPPTCPConnectionConfiguration.builder()
            .setXmppDomain(serviceName)
            .setSecurityMode(ConnectionConfiguration.SecurityMode.ifpossible)
            .setHostnameVerifier((s, sslSession) -> true)
            .setCustomSSLContext(sslContext)
            .build();

        return new XMPPTCPConnection(config)
            .connect();
    }
}
