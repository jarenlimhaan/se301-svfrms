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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import org.example.device.ControlDispatcher;
import org.example.device.Device;
import org.example.device.Devices;
import org.example.report.ChamberSummaryTracker;
import org.example.sensor.ChamberRegistry;
import org.example.sensor.ChamberControl;
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
            ControlDispatcher dispatcher = Chambers.dispatcher(chambers, 2, List.of(), Duration.ofSeconds(30));
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
            ControlDispatcher dispatcher = Chambers.dispatcher(chambers, 1, List.of(), Duration.ofSeconds(30));
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
            ControlDispatcher dispatcher = Chambers.dispatcher(chambers, 2, List.of(tracker), Duration.ofSeconds(30));
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
        ControlDispatcher dispatcher = Chambers.dispatcher(chambers, 2, List.of(), Duration.ofSeconds(30));
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
            ControlDispatcher dispatcher = Chambers.dispatcher(chambers, 2, List.of(), Duration.ofMillis(300));
            dispatcher.start();
            dispatcher.request("CH1", blocking(entered, never, false));
            entered.await();

            IllegalStateException e = assertThrows(IllegalStateException.class, dispatcher::close);
            assertTrue(e.getMessage().contains("did not finish"), e.getMessage());
            IllegalStateException repeated = assertThrows(IllegalStateException.class, dispatcher::close);
            assertEquals(e.getMessage(), repeated.getMessage());
        });
    }

    @Test
    void interruptIgnoringDeviceCannotKeepTheApplicationAlive() {
        assertTimeoutPreemptively(LIMIT, () -> {
            CountDownLatch entered = new CountDownLatch(1);
            AtomicBoolean release = new AtomicBoolean();
            Device stubborn = new Device() {
                @Override public String name() { return "Stubborn"; }

                @Override public String activate(ChamberControl chamber) {
                    entered.countDown();
                    while (!release.get()) {
                        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                        Thread.interrupted(); // deliberately ignore and clear interruption
                    }
                    return "done";
                }
            };
            ControlDispatcher dispatcher = Chambers.dispatcher(chambers, 1, List.of(), Duration.ofMillis(50));
            dispatcher.start();
            dispatcher.request("CH1", stubborn);
            entered.await();
            try {
                assertThrows(IllegalStateException.class, dispatcher::close);
                Thread worker = Thread.getAllStackTraces().keySet().stream()
                        .filter(t -> t.isAlive() && t.getName().equals("control-dispatcher"))
                        .findFirst().orElseThrow();
                assertTrue(worker.isDaemon(), "a stuck device worker must not hold the JVM open");
            } finally {
                release.set(true);
            }
        });
    }

    @Test
    void stuckDeviceCannotLeaveProducersBlockedForever() {
        assertTimeoutPreemptively(LIMIT, () -> {
            CountDownLatch entered = new CountDownLatch(1);
            AtomicBoolean release = new AtomicBoolean();
            Device stubborn = new Device() {
                @Override public String name() { return "Stubborn"; }

                @Override public String activate(ChamberControl chamber) {
                    entered.countDown();
                    while (!release.get()) {
                        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                        Thread.interrupted();
                    }
                    return "done";
                }
            };
            ControlDispatcher dispatcher = Chambers.dispatcher(chambers, 1, List.of(), Duration.ofMillis(100));
            dispatcher.start();
            dispatcher.request("CH1", stubborn);
            entered.await();
            dispatcher.request("CH1", Devices.GROW_LIGHT); // fills the queue while the worker is stuck
            CompletableFuture<Void> producer = CompletableFuture.runAsync(() -> {
                try {
                    dispatcher.request("CH1", Devices.GROW_LIGHT);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            try {
                ExecutionException failure = assertThrows(ExecutionException.class, producer::get);
                assertTrue(failure.getCause() instanceof IllegalStateException, String.valueOf(failure.getCause()));
                assertThrows(IllegalStateException.class, dispatcher::close);
            } finally {
                release.set(true);
            }
        });
    }

    @Test
    void listenerErrorDoesNotCountAnExecutedRequestAsLost() {
        assertTimeoutPreemptively(LIMIT, () -> {
            ControlDispatcher dispatcher = Chambers.dispatcher(chambers, 1,
                    List.of((chamber, device, effect) -> { throw new AssertionError("boom"); }), Duration.ofSeconds(30));
            dispatcher.start();
            dispatcher.request("CH1", Devices.GROW_LIGHT);

            IllegalStateException e = assertThrows(IllegalStateException.class, dispatcher::close);
            assertTrue(e.getMessage().contains("0 queued"), e.getMessage());
            assertEquals(1, dispatcher.stats().executed());
            assertTrue(e.getCause() instanceof AssertionError);
        });
    }

    @Test
    void closeBeforeStartRejectsLaterRequests() {
        assertTimeoutPreemptively(LIMIT, () -> {
            ControlDispatcher dispatcher = Chambers.dispatcher(chambers, 1, List.of(), Duration.ofSeconds(30));
            dispatcher.close();
            for (int i = 0; i < 3; i++) { // more than the capacity: none may block
                assertThrows(IllegalStateException.class, () -> dispatcher.request("CH1", Devices.GROW_LIGHT));
            }
        });
    }

    @Test
    void secondCloseWaitsForTheDrain() {
        assertTimeoutPreemptively(LIMIT, () -> {
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            ChamberSummaryTracker tracker = new ChamberSummaryTracker();
            ControlDispatcher dispatcher = Chambers.dispatcher(chambers, 2, List.of(tracker), Duration.ofSeconds(30));
            dispatcher.start();
            dispatcher.request("CH1", blocking(entered, release, false));
            entered.await();
            CompletableFuture<Void> first = CompletableFuture.runAsync(dispatcher::close);
            CompletableFuture.delayedExecutor(300, TimeUnit.MILLISECONDS).execute(release::countDown);
            Thread.sleep(100); // let the first close start draining
            dispatcher.close(); // must not return before the worker has drained
            assertEquals(1, dispatcher.stats().executed());
            first.get();
        });
    }

    @Test
    void zeroOrNegativeShutdownTimeoutIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Chambers.dispatcher(chambers, 2, List.of(), Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> Chambers.dispatcher(chambers, 2, List.of(), Duration.ofMillis(-1)));
    }

    /** A timeout below 1 ms must not become join(0), which would wait forever. */
    @Test
    void subMillisecondTimeoutStillBoundsClose() {
        assertTimeoutPreemptively(LIMIT, () -> {
            CountDownLatch entered = new CountDownLatch(1), never = new CountDownLatch(1);
            ControlDispatcher dispatcher = Chambers.dispatcher(chambers, 2, List.of(), Duration.ofNanos(500_000));
            dispatcher.start();
            dispatcher.request("CH1", blocking(entered, never, false));
            entered.await();

            IllegalStateException e = assertThrows(IllegalStateException.class, dispatcher::close);
            assertTrue(e.getMessage().contains("did not finish"), e.getMessage());
        });
    }
}
