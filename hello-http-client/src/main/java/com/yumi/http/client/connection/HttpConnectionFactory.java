package com.yumi.http.client.connection;

public interface HttpConnectionFactory<T, C extends HttpConnection> {
    C create(T route);
}
