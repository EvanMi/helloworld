package com.yumi.connection;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

public class DefaultManagedHttpClientConnection extends DefaultBHttpClientConnection
        implements ManagedHttpClientConnection{

    private final String id;
    public DefaultManagedHttpClientConnection(final String id, int bufferSize, int fragmentSizeHint,
                                              CharsetDecoder charDecoder, CharsetEncoder charEncoder) {
        super(bufferSize, fragmentSizeHint, charDecoder, charEncoder);
        this.id = id;
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public void bind(Socket socket) throws IOException {
        super.bind(socket);
    }

    @Override
    public Socket getSocket() {
        return super.getSocket();
    }

}
