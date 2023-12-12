package com.yumi.http.client.connection;

import java.io.Closeable;
import java.io.IOException;

public interface HttpConnection extends Closeable {
    @Override
    void close() throws IOException;
    boolean isOpen();
    boolean isStale();
    void setSocketTimeout(int timeout);
    int getSocketTimeout();
}
