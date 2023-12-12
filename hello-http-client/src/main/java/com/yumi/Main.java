package com.yumi;

import com.yumi.connection.ConnectionRequest;
import com.yumi.connection.HttpClientConnection;
import com.yumi.connection.HttpClientConnectionManager;
import com.yumi.connection.ManagedHttpClientConnection;
import com.yumi.connection.PoolingHttpClientConnectionManager;

import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws Exception {
        HttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager(
                30,
                TimeUnit.SECONDS
        );
        final String state = "state";
        try {

            for (int i = 0; i < 100; i++) {
                ConnectionRequest connRequest = connManager.requestConnection("localhost:8080", state);

                HttpClientConnection managedConn = connRequest.get(2000, TimeUnit.MILLISECONDS);
                System.out.println(((ManagedHttpClientConnection) managedConn).getId());
                if (!managedConn.isOpen()) {
                    connManager.connect(managedConn, "localhost:8080", 2000);
                }
                connManager.routeComplete(managedConn, "localhost:8080");

                managedConn.setSocketTimeout(2000);
                managedConn.sendRequestHeader("GET /?name=lily&age=18 HTTP/1.1", "host: 127.0.0.1");
                managedConn.flush();

                System.out.println(managedConn.receiveResponseHeader());


                connManager.releaseConnection(managedConn, state, 20, TimeUnit.SECONDS);
                TimeUnit.SECONDS.sleep(5);
            }
        } finally {
            connManager.shutdown();
        }
    }
}