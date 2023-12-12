package com.yumi.http.client.connection;


import java.io.IOException;

public interface HttpClientConnection extends HttpConnection{
    boolean isResponseAvailable(int timeout) throws IOException;
    void sendRequestHeader(String line, String... headers) throws IOException;
    String receiveResponseHeader() throws IOException;
    void flush() throws IOException;
}
