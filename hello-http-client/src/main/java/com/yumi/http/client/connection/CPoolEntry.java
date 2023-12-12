package com.yumi.http.client.connection;

import com.yumi.http.client.pool.PoolEntry;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

class CPoolEntry extends PoolEntry<String, ManagedHttpClientConnection> {

    private volatile boolean routeComplete;

    public CPoolEntry(
            final String id,
            final String route,
            final ManagedHttpClientConnection conn,
            final long timeToLive, final TimeUnit timeUnit) {
        super(id, route, conn, timeToLive, timeUnit);
    }

    public void markRouteComplete() {
        this.routeComplete = true;
    }

    public boolean isRouteComplete() {
        return this.routeComplete;
    }

    public void closeConnection() throws IOException {
        final HttpClientConnection conn = getConnection();
        conn.close();
    }

    public void shutdownConnection() throws IOException {
        final HttpClientConnection conn = getConnection();
        // TODO: 2023/12/11 do noting
        //conn.shutdown();
    }

    @Override
    public boolean isExpired(final long now) {
        return super.isExpired(now);
    }

    @Override
    public boolean isClosed() {
        final HttpClientConnection conn = getConnection();
        return !conn.isOpen();
    }

    @Override
    public void close() {
        try {
            closeConnection();
        } catch (final IOException ex) {
            ex.printStackTrace();
        }
    }
}
