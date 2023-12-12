package com.yumi.http.client.pool;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.Future;

abstract class RouteSpecificPool<T, C, E extends PoolEntry<T, C>> {
    private final T route;
    private final Set<E> leased;
    private final LinkedList<E> available;
    private final LinkedList<Future<E>> pending;

    RouteSpecificPool(final T route) {
        super();
        this.route = route;
        this.leased = new HashSet<E>();
        this.available = new LinkedList<E>();
        this.pending = new LinkedList<Future<E>>();
    }

    protected abstract E createEntry(C conn);

    public final T getRoute() {
        return route;
    }

    public int getLeasedCount() {
        return this.leased.size();
    }

    public int getPendingCount() {
        return this.pending.size();
    }

    public int getAvailableCount() {
        return this.available.size();
    }

    public int getAllocatedCount() {
        return this.available.size() + this.leased.size();
    }

    public E getFree(final Object state) {
        if (!this.available.isEmpty()) {
            if (state != null) {
                final Iterator<E> it = this.available.iterator();
                while (it.hasNext()) {
                    final E entry = it.next();
                    if (state.equals(entry.getState())) {
                        it.remove();
                        this.leased.add(entry);
                        return entry;
                    }
                }
            }
            final Iterator<E> it = this.available.iterator();
            while (it.hasNext()) {
                final E entry = it.next();
                if (entry.getState() == null) {
                    it.remove();
                    this.leased.add(entry);
                    return entry;
                }
            }
        }
        return null;
    }

    public E getLastUsed() {
        return this.available.isEmpty() ? null : this.available.getLast();
    }

    public boolean remove(final E entry) {
        if (!this.available.remove(entry)) {
            return this.leased.remove(entry);
        }
        return true;
    }

    public void free(final E entry, final boolean reusable) {
        final boolean found = this.leased.remove(entry);
        if (!found) {
            throw new IllegalStateException("Entry has not been leased from this pool");
        }
        if (reusable) {
            this.available.addFirst(entry);
        }
    }

    public E add(final C conn) {
        final E entry = createEntry(conn);
        this.leased.add(entry);
        return entry;
    }

    public void queue(final Future<E> future) {
        if (future == null) {
            return;
        }
        this.pending.add(future);
    }
    public Future<E> nextPending() {
        return this.pending.poll();
    }
    public void unqueue(final Future<E> future) {
        if (future == null) {
            return;
        }
        this.pending.remove(future);
    }
    public void shutdown() {
        for (final Future<E> future: this.pending) {
            future.cancel(true);
        }
        this.pending.clear();
        for (final E entry: this.available) {
            entry.close();
        }
        this.available.clear();
        for (final E entry: this.leased) {
            entry.close();
        }
        this.leased.clear();
    }
    @Override
    public String toString() {
        return "[route: " +
                this.route +
                "][leased: " +
                this.leased.size() +
                "][available: " +
                this.available.size() +
                "][pending: " +
                this.pending.size() +
                "]";
    }
}
