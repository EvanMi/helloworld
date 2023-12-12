package com.yumi.connection;

import com.yumi.concurrent.Cancellable;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public interface ConnectionRequest extends Cancellable {

    HttpClientConnection get(long timeout, TimeUnit timeUnit) throws InterruptedException, ExecutionException;
}
