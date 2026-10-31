package org.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.example.device.ControlDispatcher;
import org.example.device.Device;
import org.example.device.Devices;
import org.example.sensor.ChamberControl;
import org.example.sensor.ChamberRegistry;
import org.example.sensor.ChamberState;
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
        ControlDispatcher dispatcher = new ControlDispatcher(chambers, 2, List.of(), Duration.ofSeconds(30));
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
                ControlDispatcher dispatcher = new ControlDispatcher(chambers, 3, List.of(), Duration.ofSeconds(30));
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
}
