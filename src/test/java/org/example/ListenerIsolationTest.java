package org.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.example.alert.Alert;
import org.example.alert.AlertListener;
import org.example.alert.AlertService;
import org.example.alert.Severity;
import org.example.crop.CropCatalog;
import org.example.device.ControlDispatcher;
import org.example.device.DeviceListener;
import org.example.device.Devices;
import org.example.monitoring.ResponsePolicy;
import org.example.monitoring.SensorProcessor;
import org.example.report.ChamberSummaryTracker;
import org.example.resource.ResourceMeter;
import org.example.sensor.ChamberRegistry;
import org.example.sensor.SensorType;
import org.junit.jupiter.api.Test;

/** Finding 7: a throwing listener is isolated; other listeners and the caller carry on. */
class ListenerIsolationTest {
    private static final AlertListener BROKEN_ALERT_LISTENER = alert -> { throw new IllegalStateException("listener down"); };

    @Test
    void failingAlertListenerDoesNotStopLaterListenersOrTheChamber() throws Exception {
        ChamberSummaryTracker tracker = new ChamberSummaryTracker();
        List<String> printed = Collections.synchronizedList(new ArrayList<>());
        // The broken listener is first, so without isolation neither later listener would run.
        AlertService alerts = Chambers.alerts(List.of(BROKEN_ALERT_LISTENER, tracker, a -> printed.add(a.message())));
        SensorProcessor processor = new SensorProcessor(CropCatalog.defaultRegistry(), new ChamberRegistry(),
                alerts, new ResourceMeter(), ResponsePolicy.standard(), (chamber, device) -> {});

        processor.process("CH1", "Tomato", SensorType.TEMPERATURE, 31); // returns normally
        processor.process("CH1", "Tomato", SensorType.HUMIDITY, 90);

        assertEquals(2, alerts.generatedCount());
        assertEquals(List.of("CH1 Tomato High Temp", "CH1 Tomato High Humidity"), printed);
        assertTrue(tracker.lines().getFirst().contains("Alerts=2"), "summary agrees with the alert count: " + tracker.lines());
        assertEquals(2, alerts.listenerFailures());
    }

    @Test
    void alertsRaisedAfterAFailureAreStillDelivered() {
        List<String> seen = new ArrayList<>();
        AlertService alerts = Chambers.alerts(List.of(a -> { }, BROKEN_ALERT_LISTENER, a -> seen.add(a.message())));
        for (int i = 0; i < 3; i++) alerts.raise(new Alert("CH1", "Tomato", "a" + i, Severity.WARNING));
        assertEquals(List.of("a0", "a1", "a2"), seen);
        assertEquals(3, alerts.listenerFailures());
    }

    @Test
    void failingDeviceListenerDoesNotStopOtherDeviceListeners() throws Exception {
        ChamberSummaryTracker tracker = new ChamberSummaryTracker();
        DeviceListener broken = (chamber, device, effect) -> { throw new IllegalStateException("display down"); };
        ChamberRegistry chambers = new ChamberRegistry();
        ControlDispatcher dispatcher = Chambers.dispatcher(chambers, 2, List.of(broken, tracker), Duration.ofSeconds(30));
        dispatcher.start();
        for (int i = 0; i < 3; i++) dispatcher.request("CH1", Devices.GROW_LIGHT);
        dispatcher.close();

        assertTrue(tracker.lines().getFirst().contains("Actions=3"), tracker.lines().toString());
        assertEquals(3, dispatcher.stats().listenerFailures());
        assertEquals(0, dispatcher.stats().failed(), "the devices themselves worked");
        assertEquals(1000.0, Chambers.read(chambers.control("CH1"), SensorType.LIGHT));
    }
}
