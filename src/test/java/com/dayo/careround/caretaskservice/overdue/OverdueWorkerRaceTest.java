package com.dayo.careround.caretaskservice.overdue;

import com.dayo.careround.caretaskservice.data.repositories.CareTaskRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Two workers poll the same queue at once: each member is claimed (ZREM-style) by exactly one of them.
class OverdueWorkerRaceTest {

    static final class InMemoryQueue implements DeadlineQueue {
        final ConcurrentHashMap<UUID, Long> scores = new ConcurrentHashMap<>();
        public void schedule(UUID id, Instant at) { scores.put(id, at.toEpochMilli()); }
        public void remove(UUID id) { scores.remove(id); }
        public List<UUID> due(Instant now, int limit) {
            return scores.entrySet().stream().filter(e -> e.getValue() <= now.toEpochMilli()).map(e -> e.getKey()).limit(limit).toList();
        }
        public boolean claim(UUID id) { return scores.remove(id) != null; } // atomic, like ZREM returning 1
    }

    @Test
    void eachDueTaskFiresExactlyOnceAcrossTwoWorkers() throws Exception {
        InMemoryQueue queue = new InMemoryQueue();
        List<UUID> ids = IntStream.range(0, 500).mapToObj(i -> UUID.randomUUID()).toList();
        ids.forEach(id -> queue.schedule(id, Instant.EPOCH));

        ConcurrentHashMap<UUID, AtomicInteger> fired = new ConcurrentHashMap<>();
        OverdueProcessor processor = mock(OverdueProcessor.class);
        when(processor.fire(any())).thenAnswer(inv -> {
            fired.computeIfAbsent(inv.getArgument(0), k -> new AtomicInteger()).incrementAndGet();
            return OverdueProcessor.Result.OVERDUE_WITH_EVENT;
        });
        CareTaskRepository repo = mock(CareTaskRepository.class);
        OverdueWorker a = new OverdueWorker(queue, processor, repo, Clock.systemUTC(), 1000, 30000);
        OverdueWorker b = new OverdueWorker(queue, processor, repo, Clock.systemUTC(), 1000, 30000);

        CountDownLatch start = new CountDownLatch(1);
        Thread ta = new Thread(() -> { await(start); a.poll(); });
        Thread tb = new Thread(() -> { await(start); b.poll(); });
        ta.start(); tb.start(); start.countDown(); ta.join(); tb.join();

        assertEquals(ids.size(), fired.size());
        fired.values().forEach(n -> assertEquals(1, n.get()));
        assertEquals(0, queue.scores.size());
    }

    @Test
    void failedFiringIsRequeuedWithBackoff() {
        InMemoryQueue queue = new InMemoryQueue();
        UUID id = UUID.randomUUID();
        queue.schedule(id, Instant.EPOCH);
        OverdueProcessor processor = mock(OverdueProcessor.class);
        when(processor.fire(id)).thenThrow(new RuntimeException("db down"));
        Instant now = Instant.parse("2026-11-02T12:00:00Z");
        new OverdueWorker(queue, processor, mock(CareTaskRepository.class), Clock.fixed(now, java.time.ZoneOffset.UTC), 10, 30000).poll();
        assertEquals(now.plusSeconds(30).toEpochMilli(), queue.scores.get(id)); // never lost
    }

    private static void await(CountDownLatch l) {
        try { l.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
