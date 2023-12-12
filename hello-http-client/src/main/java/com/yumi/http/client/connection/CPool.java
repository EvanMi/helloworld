package com.yumi.http.client.connection;

import com.yumi.http.client.pool.PoolEntryCallback;
import com.yumi.http.client.pool.AbstractConnPool;
import com.yumi.http.client.pool.ConnFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

class CPool extends AbstractConnPool<String, ManagedHttpClientConnection, CPoolEntry> {
    private static final AtomicLong COUNTER = new AtomicLong();

    private final long timeToLive;
    private final TimeUnit timeUnit;

    public CPool(
            final ConnFactory<String, ManagedHttpClientConnection> connFactory,
            final int defaultMaxPerRoute, final int maxTotal,
            final long timeToLive, final TimeUnit timeUnit) {
        super(connFactory, defaultMaxPerRoute, maxTotal);
        this.timeToLive = timeToLive;
        this.timeUnit = timeUnit;
    }

    @Override
    protected CPoolEntry createEntry(final String route, final ManagedHttpClientConnection conn) {
        final String id = Long.toString(COUNTER.getAndIncrement());
        return new CPoolEntry(id, route, conn, this.timeToLive, this.timeUnit);
    }

    @Override
    protected boolean validate(final CPoolEntry entry) {
        return !entry.getConnection().isStale();
    }

    @Override
    protected void enumAvailable(final PoolEntryCallback<String, ManagedHttpClientConnection> callback) {
        super.enumAvailable(callback);
    }

    @Override
    protected void enumLeased(final PoolEntryCallback<String, ManagedHttpClientConnection> callback) {
        super.enumLeased(callback);
    }
}
