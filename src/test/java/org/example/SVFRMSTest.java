package org.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.example.alert.Alert;
import org.example.alert.AlertService;
import org.example.crop.CropCatalog;
import org.example.crop.CropProfile;
import org.example.crop.CropRegistry;
import org.example.device.ControlDispatcher;
import org.example.device.Devices;
import org.example.monitoring.ResponsePolicy;
import org.example.monitoring.SensorProcessor;
import org.example.report.ChamberSummaryTracker;
import org.example.resource.ResourceMeter;
import org.example.sensor.ChamberRegistry;
import org.example.sensor.SensorLog;
import org.example.sensor.SensorType;
import org.junit.jupiter.api.Test;

class SVFRMSTest {
    private final CropRegistry crops = CropCatalog.defaultRegistry();
    private final ResourceMeter meter = new ResourceMeter();
    private final AlertService alerts = new AlertService(List.of());
    private final List<String> requested = Collections.synchronizedList(new ArrayList<>());
    private final SensorProcessor processor = new SensorProcessor(crops, new ChamberRegistry(), new SensorLog(),
            alerts, meter, ResponsePolicy.standard(), (chamber, device) -> requested.add(chamber + "|" + device.name()));

    private List<String> alertMessages() {
        alerts.archiveActive();
        return alerts.archived().stream().map(Alert::message).toList();
    }

    @Test
    void tomatoHighTempMatchesLegacyBehaviour() throws Exception {
        processor.process("CH1", "Tomato", SensorType.TEMPERATURE, 31);
        assertEquals(List.of("CH1 Tomato High Temp"), alertMessages());
        assertEquals(10, meter.energy());
        assertEquals(List.of("CH1|Cooling Fan"), requested);
    }

    @Test
    void soilAboveMaxIsNotAnAlertAsInLegacy() throws Exception {
        processor.process("CH1", "Tomato", SensorType.SOIL_MOISTURE, 70);
        processor.process("CH1", "Tomato", SensorType.SOIL_MOISTURE, 40);
        assertEquals(List.of("CH1 Tomato Low Soil Moisture"), alertMessages());
        assertEquals(50, meter.water());
    }

    @Test
    void unknownCropStillGetsFacilityWideChecks() throws Exception {
        processor.process("CH9", "Mystery", SensorType.TEMPERATURE, 99);
        processor.process("CH9", "Mystery", SensorType.LIGHT, 400);
        assertEquals(List.of("CH9 Low Light Level"), alertMessages());
    }

    @Test
    void newCropNeedsOnlyARegistryEntry() throws Exception {
        crops.register(CropProfile.builder("Okra").temperature(24, 30).build());
        processor.process("CH5", "Okra", SensorType.TEMPERATURE, 35);
        processor.process("CH6", "Kale", SensorType.HUMIDITY, 90);
        assertEquals(List.of("CH5 Okra High Temp", "CH6 Kale High Humidity"), alertMessages());
        crops.remove("Okra");
        assertTrue(crops.find("Okra").isEmpty());
    }

    @Test
    void countersSurviveConcurrentUpdates() throws Exception {
        int threads = 8, perThread = 5_000;
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int t = 0; t < threads; t++) {
                pool.execute(() -> {
                    for (int i = 0; i < perThread; i++) {
                        alerts.raise(new Alert("CH1", "Tomato", "x"));
                        meter.addWater(1);
                    }
                });
            }
        }
        assertEquals(threads * perThread, alerts.generatedCount());
        assertEquals(threads * perThread, alerts.activeCount());
        assertEquals(threads * perThread, meter.water());
    }

    @Test
    void dispatcherDrainsEveryRequestBeforeClosing() throws Exception {
        ChamberSummaryTracker tracker = new ChamberSummaryTracker();
        ChamberRegistry chambers = new ChamberRegistry();
        try (ControlDispatcher dispatcher = new ControlDispatcher(chambers, 2, List.of(tracker))) {
            dispatcher.start();
            for (int i = 0; i < 100; i++) dispatcher.request("CH1", Devices.GROW_LIGHT);
        }
        assertEquals(1000.0, chambers.state("CH1").get(SensorType.LIGHT));
        assertTrue(tracker.lines().getFirst().contains("Actions=100"));
    }
}
