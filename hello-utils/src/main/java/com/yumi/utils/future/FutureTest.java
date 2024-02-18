package com.yumi.utils.future;

import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class FutureTest {

    public static void main(String[] args) throws Exception {
        testCompletableFuture();
    }


    public static void testSynFuture() throws Exception {

        Future<String> future = new Future<String>() {
            private final AtomicBoolean cancelled = new AtomicBoolean(false);
            private final AtomicBoolean done = new AtomicBoolean(false);
            private final AtomicReference<String> entryRef = new AtomicReference<String>(null);

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                if (done.compareAndSet(false, true)) {
                    cancelled.set(true);
                    return true;
                }
                return false;
            }

            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }

            @Override
            public boolean isDone() {
                return done.get();
            }

            @Override
            public String get() throws InterruptedException, ExecutionException {
                try {
                    return get(0L, TimeUnit.MILLISECONDS);
                } catch (final TimeoutException ex) {
                    throw new ExecutionException(ex);
                }
            }

            @Override
            public String get(long timeout, TimeUnit timeUnit) throws InterruptedException, TimeoutException {
                Date deadline = null;
                if (timeout > 0) {
                    deadline = new Date(System.currentTimeMillis() + timeUnit.toMillis(timeout));
                }
                for (; ; ) {
                    synchronized (this) {
                        final String entry = entryRef.get();
                        if (entry != null) {
                            return entry;
                        }
                        if (done.get()) {
                            throw new RuntimeException("操作已被取消");
                        }

                        TimeUnit.SECONDS.sleep(5);
                        if (deadline != null && deadline.getTime() > System.currentTimeMillis()) {
                            throw new TimeoutException();
                        }
                        if (done.compareAndSet(false, true)) {
                            entryRef.set("yumi");
                        } else {
                            throw new RuntimeException("操作已被取消");
                        }
                    }
                }
            }
        };

        String s = future.get();
        System.out.println(s);
    }

    public static void testRunnableFuture() throws Exception {
        RunnableFuture<String> future = new FutureTask<>(
                () -> {
                    TimeUnit.SECONDS.sleep(5);
                    return "yumi";
                }
        );
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(future);
        for (int i = 0; i < 3; i++) {
            TimeUnit.SECONDS.sleep(1);
            System.out.println("主线程开始执行自身逻辑");
        }
        String s = future.get();
        System.out.println(s);
        executorService.shutdown();
    }

    public static void testCompletableFuture() throws Exception{
        CompletableFuture<Void> voidCompletableFuture = CompletableFuture.supplyAsync(() -> {
            try {
                TimeUnit.SECONDS.sleep(5);
                return "yumi";
            } catch (Exception e) {
                //ignore
                return "exception";
            }
        }).thenAccept(System.out::println);

        for (int i = 0; i < 3; i++) {
            TimeUnit.SECONDS.sleep(1);
            System.out.println("主线程开始执行自身逻辑");
        }

        while (!voidCompletableFuture.isDone()) {

            TimeUnit.SECONDS.sleep(1);
            System.out.println("等待...");
        }
    }
}
