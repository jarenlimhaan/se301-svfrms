package org.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.example.device.ControlDispatcher;
import org.example.device.Device;
import org.example.device.Devices;
import org.example.report.ChamberSummaryTracker;
import org.example.sensor.ChamberRegistry;
import org.example.sensor.ChamberControl;
import org.example.sensor.ChamberState;
import org.junit.jupiter.api.Test;

/** Finding 2: the dispatcher never hangs, and reports abnormal termination instead of hiding it. */
class ControlDispatcherLifecycleTest {
    private static final Duration LIMIT = Duration.ofSeconds(5);
    private final ChamberRegistry chambers = new ChamberRegistry();

    /** A device that blocks the worker until released, then optionally interrupts the worker thread. */
    private static Device blocking(CountDownLatch entered, CountDownLatch release, boolean interruptWorker) {
        return new Device() {
            @Override public String name() { return "Blocker"; }

            @Override public String activate(ChamberControl chamber) {
                entered.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (interruptWorker) Thread.currentThread().interrupt();
                return "done";
            }
        };
    }

    @Test
    void workerInterruptedWithFullQueueDoesNotHangCloseAndIsReported() {
        assertTimeoutPreemptively(LIMIT, () -> {
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            ControlDispatcher dispatcher = new ControlDispatcher(chambers, 2, List.of());
            dispatcher.start();
            dispatcher.request("CH1", blocking(entered, release, true));
            entered.await();
            dispatcher.request("CH1", Devices.GROW_LIGHT); // queue is now full
            dispatcher.request("CH1", Devices.GROW_LIGHT);
            release.countDown(); // worker interrupts itself and exits with 2 requests pending

            IllegalStateException e = assertThrows(IllegalStateException.class, dispatcher::close);
            assertTrue(e.getMessage().contains("stopped abnormally"), e.getMessage());
            assertTrue(e.getMessage().contains("2 queued"), e.getMessage());
            assertTrue(e.getCause() instanceof InterruptedException);
        });
    }

    @Test
    void producerBlockedOnFullQueueIsReleasedWhenWorkerDies() {
        assertTimeoutPreemptively(LIMIT, () -> {
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            ControlDispatcher dispatcher = new ControlDispatcher(chambers, 1, List.of());
            dispatcher.start();
            dispatcher.request("CH1", blocking(entered, release, true));
            entered.await();
            dispatcher.request("CH1", Devices.GROW_LIGHT); // fills the queue

            CompletableFuture<Void> producer = CompletableFuture.runAsync(() -> {
                try {
                    dispatcher.request("CH1", Devices.GROW_LIGHT); // blocks: queue full
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            TimeUnit.MILLISECONDS.sleep(200);
            assertTrue(!producer.isDone(), "producer should be blocked while the queue is full");
            release.countDown();

            ExecutionException e = assertThrows(ExecutionException.class, producer::get);
            assertTrue(e.getCause() instanceof IllegalStateException, String.valueOf(e.getCause()));
            assertThrows(IllegalStateException.class, dispatcher::close);
        });
    }

    @Test
    void failingDeviceIsCountedAndDoesNotStopLaterRequests() {
        assertTimeoutPreemptively(LIMIT, () -> {
            Device broken = new Device() {
                @Override public String name() { return "Broken"; }
                @Override public String activate(ChamberControl chamber) { throw new IllegalStateException("boom"); }
            };
            ChamberSummaryTracker tracker = new ChamberSummaryTracker();
            ControlDispatcher dispatcher = new ControlDispatcher(chambers, 2, List.of(tracker));
            dispatcher.start();
            dispatcher.request("CH1", broken);
            for (int i = 0; i < 5; i++) dispatcher.request("CH1", Devices.GROW_LIGHT);
            dispatcher.close(); // normal drain: no exception

            assertEquals(1, dispatcher.stats().failed());
            assertTrue(tracker.lines().getFirst().contains("Actions=5"), tracker.lines().toString());
        });
    }

    @Test
    void requestsAreRejectedBeforeStartAndAfterClose() throws Exception {
        ControlDispatcher dispatcher = new ControlDispatcher(chambers, 2, List.of());
        assertThrows(IllegalStateException.class, () -> dispatcher.request("CH1", Devices.GROW_LIGHT));
        dispatcher.start();
        dispatcher.close();
        assertThrows(IllegalStateException.class, () -> dispatcher.request("CH1", Devices.GROW_LIGHT));
        dispatcher.close(); // idempotent
    }

    @Test
    void closeIsBoundedWhenADeviceHangs() {
        assertTimeoutPreemptively(LIMIT, () -> {
            CountDownLatch entered = new CountDownLatch(1), never = new CountDownLatch(1);
            ControlDispatcher dispatcher = new ControlDispatcher(chambers, 2, List.of(), Duration.ofMillis(300));
            dispatcher.start();
            dispatcher.request("CH1", blocking(entered, never, false));
            entered.await();

            IllegalStateException e = assertThrows(IllegalStateException.class, dispatcher::close);
            assertTrue(e.getMessage().contains("did not finish"), e.getMessage());
        });
    }
}
