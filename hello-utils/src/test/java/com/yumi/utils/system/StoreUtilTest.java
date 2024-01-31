package com.yumi.utils.system;

public class StoreUtilTest {

    public static void main(String[] args) {
        System.out.println(StoreUtil.TOTAL_PHYSICAL_MEMORY_SIZE);
        System.out.println(StoreUtil.TOTAL_PHYSICAL_MEMORY_SIZE / 1024 / 1024 / 1024);

        System.out.println(StoreUtil.getTotalDiskSpace("/"));
        System.out.println(StoreUtil.getTotalDiskSpace("/") / 1024 / 1024 / 1024);
    }
}
