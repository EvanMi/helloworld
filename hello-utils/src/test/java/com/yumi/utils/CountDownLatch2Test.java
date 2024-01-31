package com.yumi.utils;

import java.util.concurrent.TimeUnit;

public class CountDownLatch2Test {

    public static void main(String[] args) throws Exception{
        CountDownLatch2 latch2 = new CountDownLatch2(2);

        for (int j = 0; j < 3; j++) {
            for (int i = 0 ; i < 2; i++) {
                new Thread(() -> {
                    try {
                        TimeUnit.SECONDS.sleep(10);
                        System.out.println(Thread.currentThread().getId() + "-- count down");
                        latch2.countDown();
                    } catch (Exception e) {
                        //
                    }
                }).start();
            }
            latch2.await();
            System.out.println("await success");
            latch2.reset();
        }
    }
}
