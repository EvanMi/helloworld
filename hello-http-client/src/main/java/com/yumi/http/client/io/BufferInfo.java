package com.yumi.http.client.io;

public interface BufferInfo {
    int length();
    int capacity();
    int available();
}
