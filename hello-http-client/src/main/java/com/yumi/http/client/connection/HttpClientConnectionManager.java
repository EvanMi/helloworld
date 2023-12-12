package com.yumi.http.client.connection;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public interface HttpClientConnectionManager {
    ConnectionRequest requestConnection(String route, Object state);
    void releaseConnection(HttpClientConnection conn, Object newState, long validDuration, TimeUnit timeUnit);
    void connect(HttpClientConnection conn, String route, int connectTimeout) throws IOException;
    void upgrade(HttpClientConnection conn, String route) throws IOException;
    void routeComplete(HttpClientConnection conn, String route) throws IOException;
    void closeIdleConnections(long idleTime, TimeUnit timeUnit);
    void closeExpiredConnections();
    void shutdown();
}
