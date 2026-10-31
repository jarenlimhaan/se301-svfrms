package org.example.monitoring;

import java.util.Optional;
import org.example.alert.Alert;
import org.example.alert.AlertSink;
import org.example.alert.Severity;
import org.example.crop.CropLookup;
import org.example.crop.CropProfile;
import org.example.device.DeviceController;
import org.example.resource.UsageRecorder;
import org.example.sensor.Breach;
import org.example.sensor.ChamberStates;
import org.example.sensor.Direction;
import org.example.sensor.Reading;
import org.example.sensor.ReadingLog;
import org.example.sensor.SensorType;

/**
 * Handles one sensor reading: records it, compares it with the crop's range, and on a breach
 * raises an alert, records resource usage and requests the corrective device. A reading the sensor
 * cannot physically produce (NaN, infinite, or outside {@link SensorType#isValid}), or no reading
 * at all, raises a CRITICAL Sensor Failure alert instead and is never stored or acted on.
 *
 * <p>Every collaborator is an interface (DIP); {@link ResponsePolicy} is an immutable value object.
 */
public final class SensorProcessor implements ReadingProcessor {
    private final CropLookup crops;
    private final ChamberStates chambers;
    private final ReadingLog log;
    private final AlertSink alerts;
    private final UsageRecorder usage;
    private final ResponsePolicy policy;
    private final DeviceController controller;

    public SensorProcessor(CropLookup crops, ChamberStates chambers, ReadingLog log, AlertSink alerts,
                           UsageRecorder usage, ResponsePolicy policy, DeviceController controller) {
        this.crops = crops;
        this.chambers = chambers;
        this.log = log;
        this.alerts = alerts;
        this.usage = usage;
        this.policy = policy;
        this.controller = controller;
    }

    @Override
    public void process(String chamber, String crop, SensorType sensor, double value) throws InterruptedException {
        log.add("Processing " + crop + " " + sensor.label() + " = " + value);
        if (!sensor.isValid(value)) {
            sensorFailure(chamber, crop, "invalid " + sensor.label() + " reading " + value);
            return;
        }
        Reading reading = chambers.state(chamber).set(sensor, value);

        // Unknown crops still get the facility-wide light and nutrient checks, as in the legacy system.
        CropProfile profile = crops.find(crop).orElseGet(() -> CropProfile.builder(crop).build());
        Optional<Direction> direction = profile.range(sensor).flatMap(r -> r.breach(value));
        if (direction.isEmpty()) return;

        Breach breach = new Breach(sensor, direction.get());
        if (profile.ignores(breach)) return; // explicit legacy-compatibility exception, declared on the crop

        Response response = policy.responseTo(breach); // never null: the policy is complete
        alerts.raise(new Alert(chamber, crop, response.alertMessage(chamber, crop), response.severity()));
        profile.cost(breach).ifPresent(usage::record);
        if (response.device() != null) {
            controller.request(chamber, response.device(), reading);
        }
    }

    @Override
    public void reportMissing(String chamber, String crop, SensorType sensor) {
        log.add("No reading from " + crop + " " + sensor.label());
        sensorFailure(chamber, crop, "no " + sensor.label() + " reading");
    }

    private void sensorFailure(String chamber, String crop, String detail) {
        alerts.raise(new Alert(chamber, crop, chamber + " Sensor Failure: " + detail, Severity.CRITICAL));
    }
}
