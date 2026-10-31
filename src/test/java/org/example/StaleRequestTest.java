package org.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.example.alert.AlertService;
import org.example.crop.CropCatalog;
import org.example.device.ControlDispatcher;
import org.example.device.Device;
import org.example.device.StaleRequestPolicy;
import org.example.monitoring.ResponsePolicy;
import org.example.monitoring.SensorProcessor;
import org.example.report.ChamberSummaryTracker;
import org.example.resource.ResourceMeter;
import org.example.sensor.ChamberRegistry;
import org.example.sensor.ChamberControl;
import org.example.sensor.ChamberState;
import org.example.sensor.Reading;
import org.example.sensor.SensorLog;
import org.example.sensor.SensorType;
import org.junit.jupiter.api.Test;

/**
 * Finding 4: the review's scenario. Tomato reads 35 C (cooling fan queued), then 20 C, then the
 * queued fan runs. Legacy behaviour (EXECUTE) runs it and counts it as stale; SKIP drops it.
 */
class StaleRequestTest {
    private static final Duration LIMIT = Duration.ofSeconds(5);

    private final ChamberRegistry chambers = new ChamberRegistry();
    private final ChamberSummaryTracker tracker = new ChamberSummaryTracker();

    /** Holds the worker busy so later requests stay queued until {@code release} opens. */
    private static Device hold(CountDownLatch entered, CountDownLatch release) {
        return new Device() {
            @Override public String name() { return "Hold"; }
            @Override public String activate(ChamberControl chamber) {
                entered.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "held";
            }
        };
    }

    private ControlDispatcher runScenario(StaleRequestPolicy policy) throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        ControlDispatcher dispatcher = new ControlDispatcher(chambers, 5, List.of(tracker), Duration.ofSeconds(5), policy);
        SensorProcessor processor = new SensorProcessor(CropCatalog.defaultRegistry(), chambers, new SensorLog(),
                new AlertService(List.of()), new ResourceMeter(), ResponsePolicy.standard(), dispatcher);
        dispatcher.start();
        dispatcher.request("CH9", hold(entered, release)); // a different chamber keeps the worker busy
        entered.await();
        processor.process("CH1", "Tomato", SensorType.TEMPERATURE, 35); // queues Cooling Fan
        processor.process("CH1", "Tomato", SensorType.TEMPERATURE, 20); // newer reading (Low Temp: alert only)
        release.countDown();
        dispatcher.close();
        return dispatcher;
    }

    @Test
    void legacyPolicyStillRunsTheStaleFanButCountsIt() {
        assertTimeoutPreemptively(LIMIT, () -> {
            ControlDispatcher dispatcher = runScenario(StaleRequestPolicy.EXECUTE);
            assertEquals(18.0, chambers.state("CH1").get(SensorType.TEMPERATURE), "fan acted on the old 35 C reading");
            assertEquals(1, dispatcher.stats().stale());
            assertEquals(0, dispatcher.stats().skipped());
        });
    }

    @Test
    void skipPolicyDropsTheStaleFan() {
        assertTimeoutPreemptively(LIMIT, () -> {
            ControlDispatcher dispatcher = runScenario(StaleRequestPolicy.SKIP);
            assertEquals(20.0, chambers.state("CH1").get(SensorType.TEMPERATURE), "latest reading left untouched");
            assertEquals(1, dispatcher.stats().stale());
            assertEquals(1, dispatcher.stats().skipped());
            assertTrue(tracker.lines().stream().noneMatch(l -> l.startsWith("CH1") && l.contains("Cooling Fan")),
                    tracker.lines().toString());
        });
    }

    @Test
    void currentRequestRunsUnderSkipPolicy() throws Exception {
        ControlDispatcher dispatcher = new ControlDispatcher(chambers, 5, List.of(tracker), Duration.ofSeconds(5),
                StaleRequestPolicy.SKIP);
        dispatcher.start();
        Reading reading = chambers.state("CH1").set(SensorType.TEMPERATURE, 35);
        dispatcher.request("CH1", org.example.device.Devices.COOLING_FAN, reading);
        dispatcher.close();
        assertEquals(30.0, chambers.state("CH1").get(SensorType.TEMPERATURE));
        assertEquals(0, dispatcher.stats().stale());
    }

    @Test
    void readingVersionsIncreasePerSensor() {
        ChamberState state = new ChamberState();
        Reading first = state.set(SensorType.TEMPERATURE, 25);
        Reading humidity = state.set(SensorType.HUMIDITY, 70);
        assertTrue(state.isCurrent(first), "a reading of another sensor does not supersede it");
        Reading second = state.set(SensorType.TEMPERATURE, 26);
        assertTrue(!state.isCurrent(first) && state.isCurrent(second) && state.isCurrent(humidity));
        state.adjust(SensorType.TEMPERATURE, -5, 18);
        assertTrue(state.isCurrent(second), "device effects are not readings");
    }
}
