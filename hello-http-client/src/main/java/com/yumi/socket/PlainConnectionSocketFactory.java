package com.yumi.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class PlainConnectionSocketFactory implements ConnectionSocketFactory{
    @Override
    public Socket createSocket() throws IOException {
        return new Socket();
    }

    @Override
    public Socket connectSocket(int connectTimeout, Socket socket,
                                InetSocketAddress remoteAddress,
                                InetSocketAddress localAddress) throws IOException {
        final Socket sock = socket != null ? socket : createSocket();
        if (localAddress != null) {
            sock.bind(localAddress);
        }
        try {
            sock.connect(remoteAddress, connectTimeout);
        } catch (final IOException ex) {
            try {
                sock.close();
            } catch (final IOException ignore) {
            }
            throw ex;
        }
        return sock;
    }
}
