package com.yumi.connection;

public interface HttpConnectionFactory<T, C extends HttpConnection> {
    C create(T route);
}
