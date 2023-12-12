package com.yumi.http.client.pool;

public interface PoolEntryCallback<T, C> {

    void process(PoolEntry<T, C> entry);

}
