package org.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.example.device.ControlDispatcher;
import org.example.device.Device;
import org.example.device.Devices;
import org.example.alert.Alert;
import org.example.alert.Severity;
import org.example.report.ChamberSummaryTracker;
import org.example.sensor.ChamberControl;
import org.example.sensor.ChamberRegistry;
import org.example.sensor.ChamberState;
import org.example.sensor.SensorType;
import org.junit.jupiter.api.Test;

/** Concurrency guarantees of the control dispatcher under contention. */
class DispatcherConcurrencyTest {
    private static final Duration LIMIT = Duration.ofSeconds(10);

    @Test
    void deviceCodeRunsWithNoChamberLockHeld() throws Exception {
        ChamberRegistry chambers = new ChamberRegistry();
        ChamberControl state = chambers.control("CH1"); // the chamber's ChamberState
        AtomicBoolean lockHeld = new AtomicBoolean(true);
        Device probe = new Device() {
            @Override public String name() { return "Probe"; }
            @Override public String activate(ChamberControl chamber) {
                lockHeld.set(Thread.holdsLock(state));
                chamber.adjust(org.example.sensor.SensorType.LIGHT, 1, 1000);
                return "probed";
            }
        };
        ControlDispatcher dispatcher = Chambers.dispatcher(chambers, 2, List.of(), Duration.ofSeconds(30));
        dispatcher.start();
        dispatcher.request("CH1", probe);
        dispatcher.close();
        assertFalse(lockHeld.get(), "no foreign code runs while the chamber lock is held");
        assertEquals(1, dispatcher.stats().executed());
    }

    /** Close races 8 producers: every request is either executed or rejected; none is lost or hangs. */
    @Test
    void closeDuringHeavyProductionLosesNothing() {
        assertTimeoutPreemptively(LIMIT, () -> {
            for (int round = 0; round < 20; round++) {
                ChamberRegistry chambers = new ChamberRegistry();
                ControlDispatcher dispatcher = Chambers.dispatcher(chambers, 3, List.of(), Duration.ofSeconds(30));
                dispatcher.start();
                AtomicInteger accepted = new AtomicInteger(), rejected = new AtomicInteger();
                CountDownLatch go = new CountDownLatch(1);
                List<Future<?>> producers = new ArrayList<>();
                try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
                    for (int p = 0; p < 8; p++) {
                        producers.add(pool.submit(() -> {
                            go.await();
                            for (int i = 0; i < 200; i++) {
                                try {
                                    dispatcher.request("CH" + (i % 4), Devices.GROW_LIGHT);
                                    accepted.incrementAndGet();
                                } catch (IllegalStateException e) {
                                    rejected.incrementAndGet();
                                }
                            }
                            return null;
                        }));
                    }
                    go.countDown();
                    Thread.sleep(round % 5); // close at varying moments
                    dispatcher.close();
                    for (Future<?> producer : producers) producer.get();
                }
                assertEquals(8 * 200, accepted.get() + rejected.get());
                assertEquals(accepted.get(), dispatcher.stats().executed(), "every accepted request ran (round " + round + ")");
            }
        });
    }

    @Test
    void concurrentFirstAccessUsesOneChamberState() {
        assertTimeoutPreemptively(LIMIT, () -> {
            ChamberRegistry chambers = new ChamberRegistry();
            var states = ConcurrentHashMap.<ChamberControl>newKeySet();
            CountDownLatch go = new CountDownLatch(1);
            try (ExecutorService pool = Executors.newFixedThreadPool(32)) {
                List<Future<?>> tasks = new ArrayList<>();
                for (int i = 0; i < 2_000; i++) {
                    tasks.add(pool.submit(() -> {
                        go.await();
                        states.add(chambers.control("CH1"));
                        return null;
                    }));
                }
                go.countDown();
                for (Future<?> task : tasks) task.get();
            }
            assertEquals(1, states.size(), "atomic get-or-create must publish one chamber state");
        });
    }

    @Test
    void concurrentDeviceAdjustmentsLoseNoUpdates() {
        assertTimeoutPreemptively(LIMIT, () -> {
            ChamberState state = new ChamberState();
            int threads = 16, changes = 5_000;
            CountDownLatch go = new CountDownLatch(1);
            try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
                for (int i = 0; i < threads; i++) {
                    pool.submit(() -> {
                        go.await();
                        for (int j = 0; j < changes; j++) state.adjust(SensorType.LIGHT, 1, 200_000);
                        return null;
                    });
                }
                go.countDown();
            }
            assertEquals(600 + threads * changes, Chambers.read(state, SensorType.LIGHT));
        });
    }

    @Test
    void concurrentSummaryUpdatesLoseNoCounts() {
        assertTimeoutPreemptively(LIMIT, () -> {
            ChamberSummaryTracker summaries = new ChamberSummaryTracker();
            int threads = 16, changes = 5_000;
            CountDownLatch go = new CountDownLatch(1);
            try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
                for (int i = 0; i < threads; i++) {
                    boolean alerts = i % 2 == 0;
                    pool.submit(() -> {
                        go.await();
                        for (int j = 0; j < changes; j++) {
                            if (alerts) summaries.onAlert(new Alert("CH1", "Tomato", "x", Severity.WARNING));
                            else summaries.onActivated("CH1", Devices.GROW_LIGHT, "x");
                        }
                        return null;
                    });
                }
                go.countDown();
            }
            String line = summaries.lines().getFirst();
            assertTrue(line.contains("Alerts=40000, Actions=40000"), line);
        });
    }
}
