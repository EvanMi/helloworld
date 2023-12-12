package com.yumi.http.client.connection;

import java.io.IOException;
import java.net.InetSocketAddress;

public interface HttpClientConnectionOperator {
    void connect(
            ManagedHttpClientConnection conn,
            String host,
            InetSocketAddress localAddress,
            int connectTimeout) throws IOException;

    void upgrade(
            ManagedHttpClientConnection conn,
            String host) throws IOException;
}
