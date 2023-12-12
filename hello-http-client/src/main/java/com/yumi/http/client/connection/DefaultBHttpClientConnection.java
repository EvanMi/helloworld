package com.yumi.http.client.connection;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

public class DefaultBHttpClientConnection extends BHttpConnectionBase
        implements HttpClientConnection {


    public DefaultBHttpClientConnection(int bufferSize, int fragmentSizeHint, CharsetDecoder charDecoder, CharsetEncoder charEncoder) {
        super(bufferSize, fragmentSizeHint, charDecoder, charEncoder);
    }

    @Override
    public boolean isResponseAvailable(int timeout) throws IOException {
        ensureOpen();
        try {
            return awaitInput(timeout);
        } catch (final SocketTimeoutException ex) {
            return false;
        }
    }

    @Override
    public void sendRequestHeader(String line, String... headers) throws IOException {
        ensureOpen();
        this.getSessionOutputBuffer().writeLine(line);
        for (String header : headers) {
            this.getSessionOutputBuffer().writeLine(header);
        }
        this.getSessionOutputBuffer().writeLine("");
    }

    @Override
    public String receiveResponseHeader() throws IOException {
        ensureOpen();
        StringBuilder sb = new StringBuilder();
        String s;
        do {
            s = this.getSessionInputBuffer().readLine();
            sb.append(s).append("\n");
        } while (!"".equals(s));
        return sb.toString();
    }

    @Override
    public void flush() throws IOException {
        ensureOpen();
        doFlush();
    }
}
