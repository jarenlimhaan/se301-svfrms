package org.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import org.example.alert.Alert;
import org.example.alert.AlertService;
import org.example.alert.Severity;
import org.example.crop.CropCatalog;
import org.example.crop.CropProfile;
import org.example.crop.CropSpecParser;
import org.example.monitoring.ReadingProcessor;
import org.example.monitoring.ResponsePolicy;
import org.example.monitoring.SensorProcessor;
import org.example.resource.ResourceMeter;
import org.example.sensor.ChamberRegistry;
import org.example.sensor.SensorLog;
import org.example.sensor.SensorType;
import org.example.simulation.ChamberSimulation;
import org.example.simulation.RandomReadingSource;
import org.example.simulation.ReadingSource;
import org.junit.jupiter.api.Test;

/** Every "for each sensor" place is driven by {@link SensorType}, and unresponsive sensors are reported. */
class SensorExtensibilityTest {

    @Test
    void cropSpecsAcceptEverySensorKey() {
        for (SensorType sensor : SensorType.values()) {
            CropProfile crop = CropSpecParser.parse("X:" + sensor.key() + "=" + sensor.validMin() + ".." + sensor.validMax());
            assertTrue(crop.range(sensor).isPresent(), sensor.key());
        }
    }

    @Test
    void simulationReadsEverySensorInOrderEachCycle() throws Exception {
        List<SensorType> seen = new ArrayList<>();
        ReadingProcessor recorder = new ReadingProcessor() {
            @Override public void process(String chamber, String crop, SensorType sensor, double value) { seen.add(sensor); }
            @Override public void reportMissing(String chamber, String crop, SensorType sensor) { }
        };
        ResourceMeter meter = new ResourceMeter();
        int completed = new ChamberSimulation("CH1", "Tomato", 3, recorder, RandomReadingSource.legacy(), meter).call();
        assertEquals(3, completed);
        List<SensorType> expected = new ArrayList<>();
        for (int i = 0; i < 3; i++) expected.addAll(Arrays.asList(SensorType.values()));
        assertEquals(expected, seen);
        assertEquals(6, meter.water());
        assertEquals(3, meter.energy());
    }

    @Test
    void legacyRandomSourceStaysInsideTheLegacyRanges() {
        ReadingSource source = RandomReadingSource.legacy();
        for (int i = 0; i < 2_000; i++) {
            double t = source.read("CH1", SensorType.TEMPERATURE).orElseThrow();
            double l = source.read("CH1", SensorType.LIGHT).orElseThrow();
            assertTrue(t >= 10 && t <= 39 && t == Math.rint(t), "temperature " + t);
            assertTrue(l >= 300 && l <= 1199, "light " + l);
        }
    }

    @Test
    void unresponsiveSensorRaisesCriticalSensorFailure() throws Exception {
        AlertService alerts = new AlertService(List.of());
        ChamberRegistry chambers = new ChamberRegistry();
        List<String> devices = new ArrayList<>();
        SensorProcessor processor = new SensorProcessor(CropCatalog.defaultRegistry(), chambers, new SensorLog(), alerts,
                new ResourceMeter(), ResponsePolicy.standard(), (chamber, device, trigger) -> devices.add(device.name()));
        ReadingSource humidityDown = (chamber, sensor) ->
                sensor == SensorType.HUMIDITY ? OptionalDouble.empty() : OptionalDouble.of(sensor.initialValue());

        new ChamberSimulation("CH1", "Tomato", 2, processor, humidityDown, new ResourceMeter()).call();

        alerts.archiveActive();
        List<Alert> archived = alerts.archived();
        assertEquals(2, archived.size(), archived.toString());
        for (Alert alert : archived) {
            assertEquals("CH1 Sensor Failure: no Humidity reading", alert.message());
            assertEquals(Severity.CRITICAL, alert.severity());
        }
        assertEquals(SensorType.HUMIDITY.initialValue(), chambers.state("CH1").get(SensorType.HUMIDITY));
        assertTrue(devices.isEmpty());
    }

    @Test
    void readingSourceMustCoverEverySensor() {
        // RandomReadingSource checks coverage at construction; legacy() covers all five sensors.
        ReadingSource source = RandomReadingSource.legacy();
        for (SensorType sensor : SensorType.values()) assertFalse(source.read("CH1", sensor).isEmpty(), sensor.name());
    }
}
