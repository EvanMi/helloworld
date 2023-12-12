package com.yumi.http.client.pool;

import com.yumi.http.client.concurrent.FutureCallback;

import java.util.concurrent.Future;

public interface ConnPool<T, E> {
    Future<E> lease(final T route, final Object state, final FutureCallback<E> callback);
    void release(E entry, boolean reusable);
}
