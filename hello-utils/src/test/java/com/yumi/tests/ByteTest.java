package com.yumi.tests;

import java.nio.ByteBuffer;

public class ByteTest {
    public static void main(String[] args) {
        ByteBuffer allocate = ByteBuffer.allocate(1024);
        allocate.putInt(1);
        allocate.putInt(2);
        allocate.putInt(3);
        allocate.putInt(4);
        allocate.putInt(5);

        allocate.flip();

        System.out.println(allocate.getInt(4));
        System.out.println(allocate.getInt());
        System.out.println(allocate.getInt());

        ByteBuffer allocate1 = ByteBuffer.allocate(64);
        allocate1.putInt(1);
        allocate1.putInt(2);
        allocate1.putInt(3);
        allocate1.putInt(4);
        allocate1.putInt(5);
        allocate1.flip();

        byte[] arr = new byte[20];
        allocate1.get(arr, 0, 4);
        allocate1.get(arr, 0, 4);

    }
}