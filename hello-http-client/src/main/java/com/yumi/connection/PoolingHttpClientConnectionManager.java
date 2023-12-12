package com.yumi.connection;

import com.yumi.pool.ConnFactory;
import com.yumi.pool.ConnPoolControl;
import com.yumi.pool.PoolEntry;
import com.yumi.pool.PoolEntryCallback;
import com.yumi.pool.PoolStats;
import com.yumi.socket.ConnectionSocketFactory;
import com.yumi.socket.PlainConnectionSocketFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class PoolingHttpClientConnectionManager implements HttpClientConnectionManager, ConnPoolControl<String>, Closeable {

    private final CPool pool;
    private final AtomicBoolean isShutDown;

    private final ConnectionSocketFactory sf;

    public PoolingHttpClientConnectionManager(final long timeToLive, final TimeUnit timeUnit) {
        super();
        this.pool = new CPool(new InternalConnectionFactory(), 2, 20, timeToLive, timeUnit);
        this.pool.setValidateAfterInactivity(2000);
        this.sf = new PlainConnectionSocketFactory();
        this.isShutDown = new AtomicBoolean(false);
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            shutdown();
        } finally {
            super.finalize();
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    @Override
    public ConnectionRequest requestConnection(
            final String route,
            final Object state) {
        final Future<CPoolEntry> future = this.pool.lease(route, state, null);
        return new ConnectionRequest() {
            @Override
            public boolean cancel() {
                return future.cancel(true);
            }

            @Override
            public HttpClientConnection get(
                    final long timeout,
                    final TimeUnit timeUnit) throws InterruptedException, ExecutionException {
                final HttpClientConnection conn = leaseConnection(future, timeout, timeUnit);
                if (conn.isOpen()) {
                    conn.setSocketTimeout(2000);
                }
                return conn;
            }

        };
    }

    protected HttpClientConnection leaseConnection(
            final Future<CPoolEntry> future,
            final long timeout,
            final TimeUnit timeUnit) throws InterruptedException, ExecutionException {
        final CPoolEntry entry;
        try {
            entry = future.get(timeout, timeUnit);
            if (entry == null || future.isCancelled()) {
                throw new ExecutionException(new CancellationException("Operation cancelled"));
            }
            return CPoolProxy.newProxy(entry);
        } catch (final TimeoutException ex) {
            throw new IllegalStateException("Timeout waiting for connection from pool");
        }
    }

    @Override
    public void releaseConnection(
            final HttpClientConnection managedConn,
            final Object state,
            final long keepalive, final TimeUnit timeUnit) {
        synchronized (managedConn) {
            final CPoolEntry entry = CPoolProxy.detach(managedConn);
            if (entry == null) {
                return;
            }
            final ManagedHttpClientConnection conn = entry.getConnection();
            try {
                if (conn.isOpen()) {
                    final TimeUnit effectiveUnit = timeUnit != null ? timeUnit : TimeUnit.MILLISECONDS;
                    entry.setState(state);
                    entry.updateExpiry(keepalive, effectiveUnit);
                    //
                    conn.setSocketTimeout(0);
                }
            } finally {
                this.pool.release(entry, conn.isOpen() && entry.isRouteComplete());
            }
        }
    }

    @Override
    public void connect(
            final HttpClientConnection managedConn,
            final String route,
            final int connectTimeout) throws IOException {
        final ManagedHttpClientConnection conn;
        synchronized (managedConn) {
            final CPoolEntry entry = CPoolProxy.getPoolEntry(managedConn);
            conn = entry.getConnection();
        }

        String[] split = route.split(":");
        final InetAddress[] addresses = new InetAddress[] {InetAddress.getByName(split[0])};
        final int port = Integer.parseInt(split[1]);
        final InetAddress address = addresses[0];
        Socket sock = sf.createSocket();
        sock.setSoTimeout(2000);
        sock.setReuseAddress(true);
        sock.setTcpNoDelay(true);
        sock.setKeepAlive(true);
        conn.bind(sock);
        final InetSocketAddress remoteAddress = new InetSocketAddress(address, port);
        try {
            sock = sf.connectSocket(connectTimeout, sock,  remoteAddress, null);
            conn.bind(sock);
        } catch (final SocketTimeoutException |  ConnectException ex) {
            throw new IllegalStateException(ex);
        } catch (final NoRouteToHostException ex) {
            throw ex;
        }
    }

    @Override
    public void upgrade(HttpClientConnection managedConn, String route) throws IOException {

    }

    @Override
    public void routeComplete(
            final HttpClientConnection managedConn,
            final String route) throws IOException {
        synchronized (managedConn) {
            final CPoolEntry entry = CPoolProxy.getPoolEntry(managedConn);
            entry.markRouteComplete();
        }
    }

    @Override
    public void shutdown() {
        if (this.isShutDown.compareAndSet(false, true)) {
            try {
                this.pool.enumLeased(new PoolEntryCallback<String, ManagedHttpClientConnection>() {

                    @Override
                    public void process(final PoolEntry<String, ManagedHttpClientConnection> entry) {
                        final ManagedHttpClientConnection connection = entry.getConnection();
                        if (connection != null) {
                            try {
                                connection.close();
                            } catch (final IOException ignored) {
                            }
                        }
                    }

                });
                this.pool.shutdown();
            } catch (final IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public void closeIdleConnections(final long idleTimeout, final TimeUnit timeUnit) {
        this.pool.closeIdle(idleTimeout, timeUnit);
    }

    @Override
    public void closeExpiredConnections() {
        this.pool.closeExpired();
    }

    protected void enumAvailable(final PoolEntryCallback<String, ManagedHttpClientConnection> callback) {
        this.pool.enumAvailable(callback);
    }

    protected void enumLeased(final PoolEntryCallback<String, ManagedHttpClientConnection> callback) {
        this.pool.enumLeased(callback);
    }

    @Override
    public int getMaxTotal() {
        return this.pool.getMaxTotal();
    }

    @Override
    public void setMaxTotal(final int max) {
        this.pool.setMaxTotal(max);
    }

    @Override
    public int getDefaultMaxPerRoute() {
        return this.pool.getDefaultMaxPerRoute();
    }

    @Override
    public void setDefaultMaxPerRoute(final int max) {
        this.pool.setDefaultMaxPerRoute(max);
    }

    @Override
    public int getMaxPerRoute(final String route) {
        return this.pool.getMaxPerRoute(route);
    }

    @Override
    public void setMaxPerRoute(final String route, final int max) {
        this.pool.setMaxPerRoute(route, max);
    }

    @Override
    public PoolStats getTotalStats() {
        return this.pool.getTotalStats();
    }

    @Override
    public PoolStats getStats(final String route) {
        return this.pool.getStats(route);
    }

    public Set<String> getRoutes() {
        return this.pool.getRoutes();
    }

    public int getValidateAfterInactivity() {
        return pool.getValidateAfterInactivity();
    }

    public void setValidateAfterInactivity(final int ms) {
        pool.setValidateAfterInactivity(ms);
    }

    static class InternalConnectionFactory implements ConnFactory <String, ManagedHttpClientConnection> {
        private final HttpConnectionFactory<String, ManagedHttpClientConnection> connFactory;

        InternalConnectionFactory() {
            this.connFactory = new ManagedHttpClientConnectionFactory();
        }

        @Override
        public ManagedHttpClientConnection create(String route) throws IOException {
            return this.connFactory.create(route);
        }
    }
}
