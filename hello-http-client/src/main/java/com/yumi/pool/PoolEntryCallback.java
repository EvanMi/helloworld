package com.yumi.pool;

public interface PoolEntryCallback<T, C> {

    void process(PoolEntry<T, C> entry);

}
