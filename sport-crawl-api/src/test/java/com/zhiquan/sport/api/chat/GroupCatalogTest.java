package com.zhiquan.sport.api.chat;

import com.zhiquan.sport.api.chat.ZhiquanChatRows.GroupRow;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class GroupCatalogTest {

    @Test
    void keepsTheListUntilItExpires() {
        GroupCatalog catalog = new GroupCatalog(45_000, Runnable::run);
        AtomicInteger calls = new AtomicInteger();
        GroupRow first = row(1);
        Map<Long, GroupRow> loaded = catalog.get(0, () -> {
            calls.incrementAndGet();
            return Map.of(1L, first);
        });
        Map<Long, GroupRow> again = catalog.get(44_000, () -> {
            calls.incrementAndGet();
            return Map.of();
        });
        assertEquals(1, calls.get());
        assertSame(loaded, again);
        assertEquals(1L, again.get(1L).id());
    }

    @Test
    void expiredReadReturnsThePreviousListAndReloadsOnce() {
        GroupCatalog catalog = new GroupCatalog(45_000, Runnable::run);
        catalog.get(0, () -> Map.of(1L, row(1)));
        AtomicInteger calls = new AtomicInteger();
        Map<Long, GroupRow> stale = catalog.get(45_001, () -> {
            calls.incrementAndGet();
            return Map.of(2L, row(2));
        });
        assertEquals(1L, stale.get(1L).id());
        assertEquals(1, calls.get());
        Map<Long, GroupRow> fresh = catalog.get(45_001, () -> {
            calls.incrementAndGet();
            return Map.of();
        });
        assertEquals(2L, fresh.get(2L).id());
        assertEquals(1, calls.get());
    }

    @Test
    void concurrentMissesShareOneRead() throws Exception {
        ExecutorService loader = Executors.newSingleThreadExecutor();
        ExecutorService callers = Executors.newFixedThreadPool(4);
        GroupCatalog catalog = new GroupCatalog(45_000, loader);
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try {
            Future<Map<Long, GroupRow>> first = callers.submit(() -> catalog.get(0, () -> {
                calls.incrementAndGet();
                inside.countDown();
                await(release);
                return Map.of(7L, row(7));
            }));
            inside.await();
            Future<Map<Long, GroupRow>> second = callers.submit(() -> catalog.get(0, () -> {
                calls.incrementAndGet();
                return Map.of(8L, row(8));
            }));
            release.countDown();
            assertEquals(7L, first.get().get(7L).id());
            assertEquals(7L, second.get().get(7L).id());
            assertEquals(1, calls.get());
        } finally {
            loader.shutdownNow();
            callers.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static GroupRow row(long id) {
        return new GroupRow(id, Long.toString(id), "群", "", 0, "", "", null, null, false, "", null, "");
    }
}
