package cz.rdc.devel.jabber.migrate;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smack.util.stringencoder.Base64;

import java.io.IOException;
import java.util.Arrays;

public class XMPPConnectionFactory {

    public static XMPPConnection connectAndLogin(
            String username, String serviceName, String password, String host, int port) throws IOException, InterruptedException, XMPPException, SmackException {

        if (host == null || host.isEmpty()) {
            host = serviceName;
        }

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

        XMPPTCPConnectionConfiguration config = XMPPTCPConnectionConfiguration.builder()
            .setHost(host)
            .setPort(port)
            .setXmppDomain(serviceName)
            .setSecurityMode(ConnectionConfiguration.SecurityMode.ifpossible)
            .setHostnameVerifier((s, sslSession) -> true)
            .build();

        AbstractXMPPConnection conn = new XMPPTCPConnection(config)
            .connect();

        conn.login(username, password);

        return conn;
    }
}
