package com.yumi.connection;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

public class CPoolProxy implements ManagedHttpClientConnection {
    private volatile CPoolEntry poolEntry;

    CPoolProxy(final CPoolEntry entry) {
        super();
        this.poolEntry = entry;
    }

    CPoolEntry getPoolEntry() {
        return this.poolEntry;
    }

    CPoolEntry detach() {
        final CPoolEntry local = this.poolEntry;
        this.poolEntry = null;
        return local;
    }

    ManagedHttpClientConnection getConnection() {
        final CPoolEntry local = this.poolEntry;
        if (local == null) {
            return null;
        }
        return local.getConnection();
    }

    ManagedHttpClientConnection getValidConnection() {
        final ManagedHttpClientConnection conn = getConnection();
        if (conn == null) {
            throw new IllegalStateException();
        }
        return conn;
    }

    @Override
    public void close() throws IOException {
        final CPoolEntry local = this.poolEntry;
        if (local != null) {
            local.closeConnection();
        }
    }

    @Override
    public boolean isOpen() {
        final CPoolEntry local = this.poolEntry;
        return local != null && !local.isClosed();
    }

    @Override
    public boolean isStale() {
        final HttpClientConnection conn = getConnection();
        return conn == null || conn.isStale();
    }

    @Override
    public void setSocketTimeout(final int timeout) {
        getValidConnection().setSocketTimeout(timeout);
    }

    @Override
    public int getSocketTimeout() {
        return getValidConnection().getSocketTimeout();
    }

    @Override
    public String getId() {
        return getValidConnection().getId();
    }

    @Override
    public void bind(final Socket socket) throws IOException {
        getValidConnection().bind(socket);
    }

    @Override
    public Socket getSocket() {
        return getValidConnection().getSocket();
    }
    @Override
    public boolean isResponseAvailable(final int timeout) throws IOException {
        return getValidConnection().isResponseAvailable(timeout);
    }

    @Override
    public void sendRequestHeader(String line, String... headers) throws IOException {
        getValidConnection().sendRequestHeader(line, headers);
    }

    @Override
    public String receiveResponseHeader() throws IOException {
       return getValidConnection().receiveResponseHeader();
    }

    @Override
    public void flush() throws IOException {
        getValidConnection().flush();
    }

    @Override
    public InetAddress getLocalAddress() {
        return getValidConnection().getLocalAddress();
    }

    @Override
    public int getLocalPort() {
        return getValidConnection().getLocalPort();
    }

    @Override
    public InetAddress getRemoteAddress() {
        return getValidConnection().getRemoteAddress();
    }

    @Override
    public int getRemotePort() {
        return getValidConnection().getRemotePort();
    }

    public static HttpClientConnection newProxy(final CPoolEntry poolEntry) {
        return new CPoolProxy(poolEntry);
    }

    private static CPoolProxy getProxy(final HttpClientConnection conn) {
        if (!(conn instanceof CPoolProxy)) {
            throw new IllegalStateException("Unexpected connection proxy class: " + conn.getClass());
        }
        return (CPoolProxy) conn;
    }

    public static CPoolEntry getPoolEntry(final HttpClientConnection proxy) {
        final CPoolEntry entry = getProxy(proxy).getPoolEntry();
        if (entry == null) {
            throw new IllegalStateException();
        }
        return entry;
    }

    public static CPoolEntry detach(final HttpClientConnection conn) {
        return getProxy(conn).detach();
    }
}
