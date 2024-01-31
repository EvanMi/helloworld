package com.yumi.utils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class ServiceThread implements Runnable {
    private static final long JOIN_TIME = 90 * 1000;

    private Thread thread;
    protected boolean isDaemon = false;
    private final AtomicBoolean started = new AtomicBoolean(false);
    protected volatile boolean stopped = false;
    protected volatile AtomicBoolean hasNotified = new AtomicBoolean(false);
    protected final CountDownLatch2 waitPoint = new CountDownLatch2(1);

    public ServiceThread() {

    }

    public abstract String getServiceName();

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        stopped = false;
        this.thread = new Thread(this, this.getServiceName());
        this.thread.setDaemon(isDaemon);
        this.thread.start();
    }

    public void shutdown() {
        this.shutdown(false);
    }

    public void shutdown(final boolean interrupt) {
        if (!started.compareAndSet(true, false)) {
            return;
        }
        this.stopped = true;
        if (hasNotified.compareAndSet(false, true)) {
            waitPoint.countDown();
        }
        try {
            if (interrupt) {
                this.thread.interrupt();
            }
            if (!this.thread.isDaemon()) {
                this.thread.join(this.getJointime());
            }
        } catch (InterruptedException e) {
            //igonre
        }
    }

    public long getJointime() {
        return JOIN_TIME;
    }

    public void makeStop() {
        if (!started.get()) {
            return;
        }
        this.stopped = true;
    }

    public void wakeup() {
        if (hasNotified.compareAndSet(false, true)) {
            waitPoint.countDown(); // notify
        }
    }

    protected void waitForRunning(long interval) {
        if (hasNotified.compareAndSet(true, false)) {
            this.onWaitEnd();
            return;
        }

        waitPoint.reset();
        try {
            waitPoint.await(interval, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            //igonre
        } finally {
            hasNotified.set(false);
            this.onWaitEnd();
        }
    }

    protected void onWaitEnd() {
    }

    public boolean isStopped() {
        return stopped;
    }

    public boolean isDaemon() {
        return isDaemon;
    }

    public void setDaemon(boolean daemon) {
        isDaemon = daemon;
    }
}
