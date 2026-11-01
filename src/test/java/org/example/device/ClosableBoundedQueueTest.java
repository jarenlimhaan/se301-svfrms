package org.example.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** The control queue: bounded, FIFO, and closable so that no producer can wait forever. */
class ClosableBoundedQueueTest {
    private static final Duration LIMIT = Duration.ofSeconds(5);

    private static CompletableFuture<Void> putAsync(ClosableBoundedQueue<String> queue, String item) {
        return CompletableFuture.runAsync(() -> {
            try {
                queue.put(item, LIMIT);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void fifoAndBlocksWhenFull() {
        assertTimeoutPreemptively(LIMIT, () -> {
            ClosableBoundedQueue<String> queue = new ClosableBoundedQueue<>(2);
            assertTrue(queue.put("a", LIMIT));
            assertTrue(queue.put("b", LIMIT));
            CompletableFuture<Void> third = putAsync(queue, "c");
            TimeUnit.MILLISECONDS.sleep(200);
            assertTrue(!third.isDone(), "put waits while the queue is full");
            assertEquals("a", queue.take());
            third.get();
            assertEquals("b", queue.take());
            assertEquals("c", queue.take());
        });
    }

    @Test
    void closeLetsTheConsumerDrainThenReturnsNull() throws Exception {
        ClosableBoundedQueue<String> queue = new ClosableBoundedQueue<>(3);
        assertTrue(queue.put("a", LIMIT));
        assertTrue(queue.put("b", LIMIT));
        queue.close();
        assertThrows(IllegalStateException.class, () -> queue.put("late", LIMIT), "no request slips in after close");
        assertEquals("a", queue.take());
        assertEquals("b", queue.take());
        assertNull(queue.take());
    }

    @Test
    void closeWakesABlockedConsumer() {
        assertTimeoutPreemptively(LIMIT, () -> {
            ClosableBoundedQueue<String> queue = new ClosableBoundedQueue<>(1);
            CompletableFuture<String> consumer = CompletableFuture.supplyAsync(() -> {
                try {
                    return queue.take();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            TimeUnit.MILLISECONDS.sleep(100);
            queue.close();
            assertNull(consumer.get());
        });
    }

    @Test
    void abortWakesEveryBlockedProducerWithTheCause() {
        assertTimeoutPreemptively(LIMIT, () -> {
            ClosableBoundedQueue<String> queue = new ClosableBoundedQueue<>(1);
            assertTrue(queue.put("full", LIMIT));
            List<CompletableFuture<Void>> producers = List.of(putAsync(queue, "x"), putAsync(queue, "y"), putAsync(queue, "z"));
            TimeUnit.MILLISECONDS.sleep(200);
            producers.forEach(p -> assertTrue(!p.isDone()));
            RuntimeException cause = new RuntimeException("consumer died");
            queue.abort(cause);
            for (CompletableFuture<Void> producer : producers) {
                ExecutionException e = assertThrows(ExecutionException.class, producer::get);
                assertTrue(e.getCause() instanceof IllegalStateException, String.valueOf(e.getCause()));
                assertSame(cause, e.getCause().getCause());
            }
            assertEquals(List.of("full"), queue.drain(), "the unconsumed item is reported, not lost");
        });
    }

    @Test
    void capacityMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new ClosableBoundedQueue<String>(0));
    }
}
