package com.yumi.utils.system;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

public class StoreUtil {
    public static final long TOTAL_PHYSICAL_MEMORY_SIZE = getTotalPhysicalMemorySize();

    @SuppressWarnings("restriction")
    public static long getTotalPhysicalMemorySize() {
        long physicalTotal = 1024 * 1024 * 1024 * 24L;
        OperatingSystemMXBean osmxb = ManagementFactory.getOperatingSystemMXBean();
        if (osmxb instanceof com.sun.management.OperatingSystemMXBean) {
            physicalTotal = ((com.sun.management.OperatingSystemMXBean) osmxb).getTotalPhysicalMemorySize();
        }

        return physicalTotal;
    }

    public static long getTotalDiskSpace(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return -1;
        }
        return file.getTotalSpace();
    }
}
