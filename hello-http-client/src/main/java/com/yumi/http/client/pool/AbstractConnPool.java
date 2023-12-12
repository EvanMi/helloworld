package com.yumi.http.client.pool;

import com.yumi.http.client.concurrent.FutureCallback;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public abstract class AbstractConnPool<T, C, E extends PoolEntry<T, C>>
        implements ConnPool<T, E>, ConnPoolControl<T> {
    private final Lock lock;
    private final Condition condition;
    private final ConnFactory<T, C> connFactory;
    private final Map<T, RouteSpecificPool<T, C, E>> routeToPool;
    private final Set<E> leased;
    private final LinkedList<E> available;
    private final LinkedList<Future<E>> pending;
    private final Map<T, Integer> maxPerRoute;

    private volatile boolean isShutDown;
    private volatile int defaultMaxPerRoute;
    private volatile int maxTotal;
    private volatile int validateAfterInactivity;

    public AbstractConnPool(
            final ConnFactory<T, C> connFactory,
            final int defaultMaxPerRoute,
            final int maxTotal) {
        super();
        this.connFactory = connFactory;
        this.defaultMaxPerRoute = defaultMaxPerRoute;
        this.maxTotal = maxTotal;
        this.lock = new ReentrantLock();
        this.condition = this.lock.newCondition();
        this.routeToPool = new HashMap<T, RouteSpecificPool<T, C, E>>();
        this.leased = new HashSet<E>();
        this.available = new LinkedList<E>();
        this.pending = new LinkedList<Future<E>>();
        this.maxPerRoute = new HashMap<T, Integer>();
    }

    protected abstract E createEntry(T route, C conn);

    protected void onLease(final E entry) {
    }

    protected void onRelease(final E entry) {
    }

    protected void onReuse(final E entry) {
    }

    protected boolean validate(final E entry) {
        return true;
    }

    public boolean isShutdown() {
        return this.isShutDown;
    }

    public void shutdown() throws IOException {
        if (this.isShutDown) {
            return;
        }
        this.isShutDown = true;
        this.lock.lock();
        try {
            for (final E entry : this.available) {
                entry.close();
            }
            for (final E entry : this.leased) {
                entry.close();
            }
            for (final RouteSpecificPool<T, C, E> pool : this.routeToPool.values()) {
                pool.shutdown();
            }
            this.routeToPool.clear();
            this.leased.clear();
            this.available.clear();
        } finally {
            this.lock.unlock();
        }
    }

    private RouteSpecificPool<T, C, E> getPool(final T route) {
        RouteSpecificPool<T, C, E> pool = this.routeToPool.get(route);
        if (pool == null) {
            pool = new RouteSpecificPool<T, C, E>(route) {
                @Override
                protected E createEntry(final C conn) {
                    return AbstractConnPool.this.createEntry(route, conn);
                }

            };
            this.routeToPool.put(route, pool);
        }
        return pool;
    }

    private static Exception operationAborted() {
        return new CancellationException("Operation aborted");
    }

    @Override
    public Future<E> lease(final T route, final Object state, final FutureCallback<E> callback) {
        if (this.isShutDown) {
            throw new IllegalStateException("Connection pool shut down");
        }
        return new Future<E>() {
            private final AtomicBoolean cancelled = new AtomicBoolean(false);
            private final AtomicBoolean done = new AtomicBoolean(false);
            private final AtomicReference<E> entryRef = new AtomicReference<E>(null);

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                if (done.compareAndSet(false, true)) {
                    cancelled.set(true);
                    lock.lock();
                    try {
                        condition.signalAll();
                    } finally {
                        lock.unlock();
                    }
                    if (callback != null) {
                        callback.cancelled();
                    }
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
            public E get() throws InterruptedException, ExecutionException {
                try {
                    return get(0L, TimeUnit.MILLISECONDS);
                } catch (final TimeoutException ex) {
                    throw new ExecutionException(ex);
                }
            }

            @Override
            public E get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                for (; ; ) {
                    synchronized (this) {
                        try {
                            final E entry = entryRef.get();
                            if (entry != null) {
                                return entry;
                            }
                            if (done.get()) {
                                throw new ExecutionException(operationAborted());
                            }
                            final E leasedEntry = getPoolEntryBlocking(route, state, timeout, unit, this);
//                            if (validateAfterInactivity > 0) {
//                                if (leasedEntry.getUpdated() + validateAfterInactivity <= System.currentTimeMillis()) {
//                                    if (!validate(leasedEntry)) {
//                                        leasedEntry.close();
//                                        release(leasedEntry, false);
//                                        continue;
//                                    }
//                                }
//                            }
                            if (done.compareAndSet(false, true)) {
                                entryRef.set(leasedEntry);
                                done.set(true);
                                onLease(leasedEntry);
                                if (callback != null) {
                                    callback.completed(leasedEntry);
                                }
                                return leasedEntry;
                            } else {
                                release(leasedEntry, true);
                                throw new ExecutionException(operationAborted());
                            }
                        } catch (final IOException ex) {
                            if (done.compareAndSet(false, true)) {
                                if (callback != null) {
                                    callback.failed(ex);
                                }
                            }
                            throw new ExecutionException(ex);
                        }
                    }
                }
            }
        };
    }

    public Future<E> lease(final T route, final Object state) {
        return lease(route, state, null);
    }

    private E getPoolEntryBlocking(
            final T route, final Object state,
            final long timeout, final TimeUnit timeUnit,
            final Future<E> future) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        Date deadline = null;
        if (timeout > 0) {
            deadline = new Date(System.currentTimeMillis() + timeUnit.toMillis(timeout));
        }
        this.lock.lock();
        try {
            E entry;
            for (; ; ) {
                if (isShutDown) {
                    throw new IllegalStateException("Connection pool shut down");
                }
                final RouteSpecificPool<T, C, E> pool = getPool(route);
                for (; ; ) {
                    entry = pool.getFree(state);
                    if (entry == null) {
                        break;
                    }
                    if (entry.isExpired(System.currentTimeMillis())) {
                        entry.close();
                    }
                    if (entry.isClosed()) {
                        this.available.remove(entry);
                        pool.free(entry, false);
                    } else {
                        break;
                    }
                }
                if (entry != null) {
                    this.available.remove(entry);
                    this.leased.add(entry);
                    onReuse(entry);
                    return entry;
                }
                final int maxPerRoute = getMax(route);
                /*
                * 子所以会出现这种情况，是由于可以动态调整某个route池的max值
                * */
                final int excess = Math.max(0, pool.getAllocatedCount() + 1 - maxPerRoute);
                if (excess > 0) {
                    for (int i = 0; i < excess; i++) {
                        final E lastUsed = pool.getLastUsed();
                        if (lastUsed == null) {
                            break;
                        }
                        lastUsed.close();
                        this.available.remove(lastUsed);
                        pool.remove(lastUsed);
                    }
                }
                /*
                 * 特定的route池没有超过限定，但是总池超过限定，回收一个其他的route对应的池的连接
                 */
                if (pool.getAllocatedCount() < maxPerRoute) {
                    final int totalUsed = this.leased.size();
                    final int freeCapacity = Math.max(this.maxTotal - totalUsed, 0);
                    if (freeCapacity > 0) {
                        final int totalAvailable = this.available.size();
                        if (totalAvailable > freeCapacity - 1) {
                            final E lastUsed = this.available.removeLast();
                            lastUsed.close();
                            final RouteSpecificPool<T, C, E> otherpool = getPool(lastUsed.getRoute());
                            otherpool.remove(lastUsed);
                        }
                        final C conn = this.connFactory.create(route);
                        entry = pool.add(conn);
                        this.leased.add(entry);
                        return entry;
                    }
                }

                boolean success = false;
                try {
                    pool.queue(future);
                    this.pending.add(future);
                    if (deadline != null) {
                        success = this.condition.awaitUntil(deadline);
                    } else {
                        this.condition.await();
                        success = true;
                    }
                    if (future.isCancelled()) {
                        throw new ExecutionException(operationAborted());
                    }
                } finally {
                    pool.unqueue(future);
                    this.pending.remove(future);
                }
                if (!success && deadline.getTime() <= System.currentTimeMillis()) {
                    break;
                }
            }
            throw new TimeoutException("Timeout waiting for connection");
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public void release(final E entry, final boolean reusable) {
        this.lock.lock();
        try {
            if (this.leased.remove(entry)) {
                final RouteSpecificPool<T, C, E> pool = getPool(entry.getRoute());
                pool.free(entry, reusable);
                if (reusable && !this.isShutDown) {
                    this.available.addFirst(entry);
                } else {
                    entry.close();
                }
                onRelease(entry);
                //优先唤醒池中等待的线程
                Future<E> future = pool.nextPending();
                if (future != null) {
                    this.pending.remove(future);
                } else {
                    future = this.pending.poll();
                }
                if (future != null) {
                    this.condition.signalAll();
                }
            }
        } finally {
            this.lock.unlock();
        }
    }

    private int getMax(final T route) {
        final Integer v = this.maxPerRoute.get(route);
        return v != null ? v : this.defaultMaxPerRoute;
    }

    @Override
    public void setMaxTotal(final int max) {
        this.lock.lock();
        try {
            this.maxTotal = max;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public int getMaxTotal() {
        this.lock.lock();
        try {
            return this.maxTotal;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public void setDefaultMaxPerRoute(final int max) {
        this.lock.lock();
        try {
            this.defaultMaxPerRoute = max;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public int getDefaultMaxPerRoute() {
        this.lock.lock();
        try {
            return this.defaultMaxPerRoute;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public void setMaxPerRoute(final T route, final int max) {
        this.lock.lock();
        try {
            if (max > -1) {
                this.maxPerRoute.put(route, max);
            } else {
                this.maxPerRoute.remove(route);
            }
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public int getMaxPerRoute(final T route) {
        this.lock.lock();
        try {
            return getMax(route);
        } finally {
            this.lock.unlock();
        }
    }
    @Override
    public PoolStats getTotalStats() {
        this.lock.lock();
        try {
            return new PoolStats(
                    this.leased.size(),
                    this.pending.size(),
                    this.available.size(),
                    this.maxTotal);
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public PoolStats getStats(final T route) {
        this.lock.lock();
        try {
            final RouteSpecificPool<T, C, E> pool = getPool(route);
            return new PoolStats(
                    pool.getLeasedCount(),
                    pool.getPendingCount(),
                    pool.getAvailableCount(),
                    getMax(route));
        } finally {
            this.lock.unlock();
        }
    }

    public Set<T> getRoutes() {
        this.lock.lock();
        try {
            return new HashSet<T>(routeToPool.keySet());
        } finally {
            this.lock.unlock();
        }
    }

    protected void enumAvailable(final PoolEntryCallback<T, C> callback) {
        this.lock.lock();
        try {
            final Iterator<E> it = this.available.iterator();
            while (it.hasNext()) {
                final E entry = it.next();
                callback.process(entry);
                if (entry.isClosed()) {
                    final RouteSpecificPool<T, C, E> pool = getPool(entry.getRoute());
                    pool.remove(entry);
                    it.remove();
                }
            }
            purgePoolMap();
        } finally {
            this.lock.unlock();
        }
    }

    protected void enumLeased(final PoolEntryCallback<T, C> callback) {
        this.lock.lock();
        try {
            for (E entry : this.leased) {
                callback.process(entry);
            }
        } finally {
            this.lock.unlock();
        }
    }

    private void purgePoolMap() {
        final Iterator<Map.Entry<T, RouteSpecificPool<T, C, E>>> it = this.routeToPool.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<T, RouteSpecificPool<T, C, E>> entry = it.next();
            final RouteSpecificPool<T, C, E> pool = entry.getValue();
            if (pool.getPendingCount() + pool.getAllocatedCount() == 0) {
                it.remove();
            }
        }
    }

    public void closeIdle(final long idletime, final TimeUnit timeUnit) {
        long time = timeUnit.toMillis(idletime);
        if (time < 0) {
            time = 0;
        }
        final long deadline = System.currentTimeMillis() - time;
        enumAvailable(new PoolEntryCallback<T, C>() {

            @Override
            public void process(final PoolEntry<T, C> entry) {
                if (entry.getUpdated() <= deadline) {
                    entry.close();
                }
            }

        });
    }

    public void closeExpired() {
        final long now = System.currentTimeMillis();
        enumAvailable(new PoolEntryCallback<T, C>() {

            @Override
            public void process(final PoolEntry<T, C> entry) {
                if (entry.isExpired(now)) {
                    entry.close();
                }
            }

        });
    }

    public int getValidateAfterInactivity() {
        return this.validateAfterInactivity;
    }

    public void setValidateAfterInactivity(final int ms) {
        this.validateAfterInactivity = ms;
    }

    @Override
    public String toString() {
        this.lock.lock();
        try {
            return "[leased: " +
                    this.leased +
                    "][available: " +
                    this.available +
                    "][pending: " +
                    this.pending +
                    "]";
        } finally {
            this.lock.unlock();
        }
    }
}
