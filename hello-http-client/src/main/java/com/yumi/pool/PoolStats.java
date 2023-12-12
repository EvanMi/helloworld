package com.yumi.pool;

import java.io.Serializable;

public class PoolStats implements Serializable {

    private static final long serialVersionUID = -2807686144795228544L;

    private final int leased;
    private final int pending;
    private final int available;
    private final int max;

    public PoolStats(final int leased, final int pending, final int free, final int max) {
        super();
        this.leased = leased;
        this.pending = pending;
        this.available = free;
        this.max = max;
    }

    public int getLeased() {
        return this.leased;
    }
    public int getPending() {
        return this.pending;
    }
    public int getAvailable() {
        return this.available;
    }
    public int getMax() {
        return this.max;
    }

    @Override
    public String toString() {
        return "[leased: " +
                this.leased +
                "; pending: " +
                this.pending +
                "; available: " +
                this.available +
                "; max: " +
                this.max +
                "]";
    }

}
