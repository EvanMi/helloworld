package com.yumi.http.client.io;

import com.yumi.http.client.util.CharArrayBuffer;

import java.io.IOException;

public interface SessionInputBuffer {
    int read(byte[] b, int off, int len) throws IOException;
    int read(byte[] b) throws IOException;
    int read() throws IOException;
    int readLine(CharArrayBuffer buffer) throws IOException;
    String readLine() throws IOException;
}
