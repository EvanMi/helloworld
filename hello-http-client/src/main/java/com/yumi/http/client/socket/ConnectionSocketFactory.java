package com.yumi.http.client.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public interface ConnectionSocketFactory {
    Socket createSocket() throws IOException;
    Socket connectSocket(
            int connectTimeout,
            Socket socket,
            InetSocketAddress remoteAddress,
            InetSocketAddress localAddress) throws IOException;
}
