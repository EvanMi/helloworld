package com.yumi.http.client.connection;

import java.util.concurrent.atomic.AtomicLong;

public class ManagedHttpClientConnectionFactory  implements HttpConnectionFactory<String, ManagedHttpClientConnection>{
    private static final AtomicLong COUNTER = new AtomicLong();

    @Override
    public ManagedHttpClientConnection create(String route) {
        final String id = "http-outgoing-" + Long.toString(COUNTER.getAndIncrement());
        return new DefaultManagedHttpClientConnection(id, 8192, 8192, null, null);
    }
}
