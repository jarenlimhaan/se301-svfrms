package org.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.example.alert.Alert;
import org.example.alert.AlertService;
import org.example.crop.CropCatalog;
import org.example.crop.CropProfile;
import org.example.crop.CropRegistry;
import org.example.crop.Range;
import org.example.monitoring.Response;
import org.example.monitoring.ResponsePolicy;
import org.example.monitoring.SensorProcessor;
import org.example.resource.ResourceMeter;
import org.example.sensor.Breach;
import org.example.sensor.ChamberRegistry;
import org.example.sensor.Direction;
import org.example.sensor.SensorType;
import org.junit.jupiter.api.Test;

/** Finding 1: every breach of a configured range gets a response unless the crop explicitly ignores it. */
class ResponseCoverageTest {
    private final CropRegistry crops = CropCatalog.defaultRegistry();
    private final AlertService alerts = new AlertService(List.of());
    private final List<String> requested = new ArrayList<>();
    private final SensorProcessor processor = new SensorProcessor(crops, new ChamberRegistry(), alerts, new ResourceMeter(), ResponsePolicy.standard(), (chamber, device) -> requested.add(device.name()));

    private List<String> alertMessages() {
        alerts.archiveActive();
        return alerts.archived().stream().map(Alert::message).toList();
    }

    @Test
    void standardPolicyCoversEverySensorAndDirection() {
        ResponsePolicy policy = ResponsePolicy.standard();
        for (SensorType sensor : SensorType.values()) {
            for (Direction direction : Direction.values()) {
                Response response = policy.responseTo(new Breach(sensor, direction));
                assertTrue(response != null && !response.label().isBlank(), sensor + " " + direction);
            }
        }
    }

    @Test
    void incompletePolicyIsRejectedAtConstruction() {
        Map<Breach, Response> partial = Map.of(
                new Breach(SensorType.TEMPERATURE, Direction.HIGH), new Response("High Temp", org.example.alert.Severity.CRITICAL, true, null));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new ResponsePolicy(partial));
        assertTrue(e.getMessage().contains("SOIL_MOISTURE"), e.getMessage());
    }

    @Test
    void kaleHighSoilMoistureNowRaisesAnAlert() throws Exception {
        processor.process("CH5", "Kale", SensorType.SOIL_MOISTURE, 90);
        assertEquals(List.of("CH5 Kale High Soil Moisture"), alertMessages());
        assertTrue(requested.isEmpty(), "no device lowers soil moisture, so the breach is alert-only");
    }

    @Test
    void tomatoHighSoilMoistureStaysIgnoredForLegacyParity() throws Exception {
        assertTrue(crops.find("Tomato").orElseThrow().ignores(new Breach(SensorType.SOIL_MOISTURE, Direction.HIGH)));
        processor.process("CH1", "Tomato", SensorType.SOIL_MOISTURE, 90);
        assertEquals(List.of(), alertMessages());
    }

    @Test
    void customLightMaximumGetsHighLightAlert() throws Exception {
        crops.register(CropProfile.builder("Microgreens").range(SensorType.LIGHT, new Range(500, 800)).build());
        processor.process("CH7", "Microgreens", SensorType.LIGHT, 950);
        assertEquals(List.of("CH7 High Light Level"), alertMessages());
    }

    /** Every finite bound of every catalog crop either alerts or is an explicitly declared legacy exception. */
    @Test
    void everyConfiguredBoundOfEveryCropIsHandled() throws Exception {
        for (CropProfile crop : CropCatalog.defaults()) {
            for (SensorType sensor : SensorType.values()) {
                Range range = crop.range(sensor).orElse(null);
                if (range == null) continue;
                check(crop, sensor, Direction.HIGH, range.max() + 1, Double.isFinite(range.max()));
                check(crop, sensor, Direction.LOW, range.min() - 1, Double.isFinite(range.min()));
            }
        }
    }

    private void check(CropProfile crop, SensorType sensor, Direction direction, double value, boolean finite)
            throws InterruptedException {
        if (!finite) return;
        alerts.archiveActive();
        int before = alerts.generatedCount();
        processor.process("CHX", crop.name(), sensor, value);
        int raised = alerts.generatedCount() - before;
        boolean ignored = crop.ignores(new Breach(sensor, direction));
        assertEquals(ignored ? 0 : 1, raised, crop.name() + " " + sensor + " " + direction + " at " + value);
    }
}
