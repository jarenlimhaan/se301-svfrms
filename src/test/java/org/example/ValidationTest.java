package org.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.example.alert.Alert;
import org.example.alert.AlertService;
import org.example.crop.CropCatalog;
import org.example.crop.CropProfile;
import org.example.crop.Range;
import org.example.monitoring.ResponsePolicy;
import org.example.monitoring.SensorProcessor;
import org.example.resource.Resource;
import org.example.resource.ResourceMeter;
import org.example.resource.Usage;
import org.example.sensor.ChamberRegistry;
import org.example.sensor.ChamberState;
import org.example.sensor.SensorType;
import org.junit.jupiter.api.Test;

/** Finding 5: invalid configuration is rejected; invalid readings raise Sensor Failure alerts. */
class ValidationTest {
    private final ChamberRegistry chambers = new ChamberRegistry();
    private final AlertService alerts = new AlertService(List.of());
    private final ResourceMeter meter = new ResourceMeter();
    private final List<String> requested = new ArrayList<>();
    private final SensorProcessor processor = new SensorProcessor(CropCatalog.defaultRegistry(), chambers, alerts, meter, ResponsePolicy.standard(), (chamber, device) -> requested.add(device.name()));

    private List<String> alertMessages() {
        alerts.archiveActive();
        return alerts.archived().stream().map(Alert::message).toList();
    }

    @Test
    void rangeRejectsNanAndWrongWayInfinity() {
        assertThrows(IllegalArgumentException.class, () -> new Range(Double.NaN, 10));
        assertThrows(IllegalArgumentException.class, () -> new Range(0, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new Range(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new Range(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new Range(30, 20));
        assertThrows(IllegalArgumentException.class, () -> new Range(0, 10).breach(Double.NaN));
        assertEquals(Double.POSITIVE_INFINITY, Range.atLeast(500).max());
    }

    @Test
    void cropRangesMustBeMeasurable() {
        assertThrows(IllegalArgumentException.class, () -> CropProfile.builder("X").humidity(-10, 80));
        assertThrows(IllegalArgumentException.class, () -> CropProfile.builder("X").soilMoisture(50, 120));
        assertThrows(IllegalArgumentException.class, () -> CropProfile.builder("X").temperature(-100, 20));
        CropProfile.builder("X").humidity(0, 100).temperature(-40, 125).build(); // edges are fine
        CropCatalog.defaults(); // every catalog crop passes validation
    }

    @Test
    void negativeResourceAmountsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Usage(Resource.WATER, -1));
        assertThrows(NullPointerException.class, () -> new Usage(null, 1));
        assertThrows(IllegalArgumentException.class, () -> CropProfile.builder("X").cost(SensorType.TEMPERATURE,
                org.example.sensor.Direction.HIGH, Resource.ENERGY, -10));
        assertThrows(IllegalArgumentException.class, () -> meter.record(new Usage(Resource.WATER, -2)));
        assertThrows(IllegalArgumentException.class, () -> meter.record(new Usage(Resource.ENERGY, -1)));
        assertEquals(0, meter.water());
    }

    @Test
    void chamberStateRejectsInvalidValues() {
        ChamberState state = new ChamberState();
        assertThrows(IllegalArgumentException.class, () -> state.set(SensorType.TEMPERATURE, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> state.set(SensorType.HUMIDITY, 101));
        assertThrows(IllegalArgumentException.class, () -> state.adjust(SensorType.LIGHT, Double.POSITIVE_INFINITY, 1000));
        assertEquals(24.0, state.get(SensorType.TEMPERATURE));
    }

    @Test
    void invalidReadingsRaiseSensorFailureAndChangeNothing() throws Exception {
        processor.process("CH1", "Tomato", SensorType.TEMPERATURE, Double.NaN);
        processor.process("CH1", "Tomato", SensorType.HUMIDITY, 150);
        processor.process("CH1", "Tomato", SensorType.LIGHT, -5);
        processor.process("CH1", "Tomato", SensorType.SOIL_MOISTURE, Double.POSITIVE_INFINITY);
        assertEquals(List.of(
                "CH1 Sensor Failure: invalid Temperature reading NaN",
                "CH1 Sensor Failure: invalid Humidity reading 150.0",
                "CH1 Sensor Failure: invalid Light reading -5.0",
                "CH1 Sensor Failure: invalid Soil reading Infinity"), alertMessages());
        ChamberState state = chambers.state("CH1");
        for (SensorType sensor : SensorType.values()) assertEquals(sensor.initialValue(), state.get(sensor));
        assertTrue(requested.isEmpty(), "an invalid reading never triggers a device");
        assertEquals(0, meter.water() + meter.energy(), "and never costs resources");
    }

    @Test
    void everySimulatedReadingIsValidSoLegacyBehaviourIsUnchanged() {
        // ChamberSimulation draws from these ranges (see its call()); all lie inside the sensor limits.
        assertTrue(SensorType.TEMPERATURE.isValid(10) && SensorType.TEMPERATURE.isValid(39));
        assertTrue(SensorType.HUMIDITY.isValid(40) && SensorType.HUMIDITY.isValid(99));
        assertTrue(SensorType.SOIL_MOISTURE.isValid(20) && SensorType.SOIL_MOISTURE.isValid(79));
        assertTrue(SensorType.LIGHT.isValid(300) && SensorType.LIGHT.isValid(1199));
        assertTrue(SensorType.NUTRIENT.isValid(20) && SensorType.NUTRIENT.isValid(99));
    }
}
