package com.zhiquan.sport.api.chat;

import com.zhiquan.sport.api.chat.ZhiquanChatRows.GroupRow;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * 群行和消息行共用 group# 前缀，列群会把整个分区读完。
 * 结果放内存里，过期后先返回上一份，只放一个任务去重读。
 */
final class GroupCatalog {

    static final long TTL_MS = 45_000L;
    private static final long WAIT_SECONDS = 9;

    private final long ttlMs;
    private final Executor executor;
    private final Object lock = new Object();
    private volatile Snapshot snapshot = Snapshot.EMPTY;
    private volatile CompletableFuture<Map<Long, GroupRow>> inflight;
    private volatile boolean refreshing;

    GroupCatalog(long ttlMs, Executor executor) {
        this.ttlMs = ttlMs;
        this.executor = executor;
    }

    void prefetch(long now, Supplier<Map<Long, GroupRow>> loader) {
        start(now, loader);
    }

    Map<Long, GroupRow> get(long now, Supplier<Map<Long, GroupRow>> loader) {
        Snapshot current = snapshot;
        if (current.loaded && now - current.at < ttlMs) {
            return current.groups;
        }
        if (current.loaded) {
            refresh(now, loader);
            return current.groups;
        }
        try {
            return start(now, loader).get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            throw new IllegalStateException("群列表还没读完");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("群列表还没读完");
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(ex.getCause());
        }
    }

    private void refresh(long now, Supplier<Map<Long, GroupRow>> loader) {
        synchronized (lock) {
            Snapshot current = snapshot;
            if (refreshing || (current.loaded && now - current.at < ttlMs)) {
                return;
            }
            refreshing = true;
        }
        executor.execute(() -> {
            try {
                store(now, loader.get());
            } catch (RuntimeException ignored) {
                Snapshot current = snapshot;
                if (current.loaded) {
                    snapshot = new Snapshot(current.groups, now);
                }
            } finally {
                refreshing = false;
            }
        });
    }

    private CompletableFuture<Map<Long, GroupRow>> start(long now, Supplier<Map<Long, GroupRow>> loader) {
        synchronized (lock) {
            if (snapshot.loaded && now - snapshot.at < ttlMs) {
                return CompletableFuture.completedFuture(snapshot.groups);
            }
            if (inflight != null) {
                return inflight;
            }
            CompletableFuture<Map<Long, GroupRow>> flight = new CompletableFuture<>();
            inflight = flight;
            executor.execute(() -> {
                try {
                    flight.complete(store(now, loader.get()));
                } catch (Throwable ex) {
                    flight.completeExceptionally(ex);
                } finally {
                    synchronized (lock) {
                        if (inflight == flight) {
                            inflight = null;
                        }
                    }
                }
            });
            return flight;
        }
    }

    private Map<Long, GroupRow> store(long now, Map<Long, GroupRow> groups) {
        Map<Long, GroupRow> frozen = Map.copyOf(groups);
        snapshot = new Snapshot(frozen, now);
        return frozen;
    }

    private record Snapshot(Map<Long, GroupRow> groups, long at, boolean loaded) {
        private static final Snapshot EMPTY = new Snapshot(Map.of(), 0, false);

        private Snapshot(Map<Long, GroupRow> groups, long at) {
            this(groups, at, true);
        }
    }
}
