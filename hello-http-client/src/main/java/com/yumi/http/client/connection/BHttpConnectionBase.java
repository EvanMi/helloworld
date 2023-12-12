package com.yumi.http.client.connection;

import com.yumi.http.client.io.SessionInputBuffer;
import com.yumi.http.client.io.SessionInputBufferImpl;
import com.yumi.http.client.io.SessionOutputBuffer;
import com.yumi.http.client.io.SessionOutputBufferImpl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.concurrent.atomic.AtomicReference;

public class BHttpConnectionBase implements HttpInetConnection{
    private final AtomicReference<Socket> socketHolder;
    private final SessionInputBufferImpl inBuffer;
    private final SessionOutputBufferImpl outBuffer;

    public BHttpConnectionBase(final int bufferSize,
                               final int fragmentSizeHint,
                               final CharsetDecoder charDecoder,
                               final CharsetEncoder charEncoder) {
        this.inBuffer =  new SessionInputBufferImpl(bufferSize, -1 , charDecoder);
        this.outBuffer = new SessionOutputBufferImpl(bufferSize, fragmentSizeHint, charEncoder);
        this.socketHolder = new AtomicReference<Socket>();
    }

    protected Socket getSocket() {
        return this.socketHolder.get();
    }

    protected void ensureOpen() throws IOException {
        final Socket socket = this.socketHolder.get();
        if (socket == null) {
            throw new IllegalStateException();
        }
        if (!this.inBuffer.isBound()) {
            this.inBuffer.bind(getSocketInputStream(socket));
        }
        if (!this.outBuffer.isBound()) {
            this.outBuffer.bind(getSocketOutputStream(socket));
        }
    }

    protected InputStream getSocketInputStream(final Socket socket) throws IOException {
        return socket.getInputStream();
    }

    protected OutputStream getSocketOutputStream(final Socket socket) throws IOException {
        return socket.getOutputStream();
    }
    protected void bind(final Socket socket) throws IOException {
        this.socketHolder.set(socket);
        this.inBuffer.bind(null);
        this.outBuffer.bind(null);
    }

    protected SessionInputBuffer getSessionInputBuffer() {
        return this.inBuffer;
    }

    protected SessionOutputBuffer getSessionOutputBuffer() {
        return this.outBuffer;
    }
    protected void doFlush() throws IOException {
        this.outBuffer.flush();
    }
    protected boolean awaitInput(final int timeout) throws IOException {
        if (this.inBuffer.hasBufferedData()) {
            return true;
        }
        fillInputBuffer(timeout);
        return this.inBuffer.hasBufferedData();
    }

    @Override
    public void close() throws IOException {
        final Socket socket = this.socketHolder.getAndSet(null);
        if (socket != null) {
            try {
                this.inBuffer.clear();
                this.outBuffer.flush();
            } finally {
                socket.close();
            }
        }
    }

    @Override
    public boolean isOpen() {
        return this.socketHolder.get() != null;
    }

    @Override
    public boolean isStale() {
        if (!isOpen()) {
            //已经关闭
            return true;
        }
        try {
            final int bytesRead = fillInputBuffer(1);
            //读到-1
            return bytesRead < 0;
        } catch (final SocketTimeoutException ex) {
            return false;
        } catch (final IOException ex) {
            //发生IO异常 例如被reset
            return true;
        }
    }

    private int fillInputBuffer(final int timeout) throws IOException {
        final Socket socket = this.socketHolder.get();
        final int oldTimeout = socket.getSoTimeout();
        try {
            socket.setSoTimeout(timeout);
            return this.inBuffer.fillBuffer();
        } finally {
            socket.setSoTimeout(oldTimeout);
        }
    }

    @Override
    public void setSocketTimeout(int timeout) {
        final Socket socket = this.socketHolder.get();
        if (socket != null) {
            try {
                socket.setSoTimeout(timeout);
            } catch (final SocketException ignore) {
                // It is not quite clear from the Sun's documentation if there are any
                // other legitimate cases for a socket exception to be thrown when setting
                // SO_TIMEOUT besides the socket being already closed
            }
        }
    }

    @Override
    public int getSocketTimeout() {
        final Socket socket = this.socketHolder.get();
        if (socket != null) {
            try {
                return socket.getSoTimeout();
            } catch (final SocketException ignore) {
                // ignore
            }
        }
        return -1;
    }

    @Override
    public InetAddress getLocalAddress() {
        final Socket socket = this.socketHolder.get();
        return socket != null ? socket.getLocalAddress() : null;
    }

    @Override
    public int getLocalPort() {
        final Socket socket = this.socketHolder.get();
        return socket != null ? socket.getLocalPort() : -1;
    }

    @Override
    public InetAddress getRemoteAddress() {
        final Socket socket = this.socketHolder.get();
        return socket != null ? socket.getInetAddress() : null;
    }

    @Override
    public int getRemotePort() {
        final Socket socket = this.socketHolder.get();
        return socket != null ? socket.getPort() : -1;
    }
}
