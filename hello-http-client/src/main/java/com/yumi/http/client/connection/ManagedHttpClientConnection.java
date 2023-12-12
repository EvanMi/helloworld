package com.yumi.http.client.connection;

import java.io.IOException;
import java.net.Socket;

public interface ManagedHttpClientConnection extends HttpClientConnection, HttpInetConnection {
    String getId();
    void bind(Socket socket) throws IOException;
    Socket getSocket();
}
