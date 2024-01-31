package com.yumi.utils.thread.factory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadFactoryTest {

    public static void main(String[] args) {
        ExecutorService executorService = Executors.newFixedThreadPool(10, new ThreadFactoryImpl("yumi-"));
        for (int i = 0; i < 100; i++) {
            final int x = i;
            executorService.submit(() -> {
                System.out.println(Thread.currentThread().getName() + "  == " + x);
            });
        }
        executorService.shutdown();
    }
}
