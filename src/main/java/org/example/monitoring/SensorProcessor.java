package org.example.monitoring;

import java.util.Optional;
import org.example.alert.Alert;
import org.example.alert.AlertService;
import org.example.crop.CropProfile;
import org.example.crop.CropRegistry;
import org.example.device.DeviceController;
import org.example.resource.ResourceMeter;
import org.example.sensor.Breach;
import org.example.sensor.ChamberRegistry;
import org.example.sensor.Direction;
import org.example.sensor.SensorLog;
import org.example.sensor.SensorType;

/**
 * Handles one sensor reading: records it, compares it with the crop's range, and on a breach
 * raises an alert, records resource usage and requests the corrective device.
 */
public final class SensorProcessor {
    private final CropRegistry crops;
    private final ChamberRegistry chambers;
    private final SensorLog log;
    private final AlertService alerts;
    private final ResourceMeter meter;
    private final ResponsePolicy policy;
    private final DeviceController controller;

    public SensorProcessor(CropRegistry crops, ChamberRegistry chambers, SensorLog log, AlertService alerts,
                           ResourceMeter meter, ResponsePolicy policy, DeviceController controller) {
        this.crops = crops;
        this.chambers = chambers;
        this.log = log;
        this.alerts = alerts;
        this.meter = meter;
        this.policy = policy;
        this.controller = controller;
    }

    public void process(String chamber, String crop, SensorType sensor, double value) throws InterruptedException {
        log.add("Processing " + crop + " " + sensor.label() + " = " + value);
        chambers.state(chamber).set(sensor, value);

        // Unknown crops still get the facility-wide light and nutrient checks, as in the legacy system.
        CropProfile profile = crops.find(crop).orElseGet(() -> CropProfile.builder(crop).build());
        Optional<Direction> direction = profile.range(sensor).flatMap(r -> r.breach(value));
        if (direction.isEmpty()) return;

        Breach breach = new Breach(sensor, direction.get());
        Optional<Response> response = policy.responseTo(breach);
        if (response.isEmpty()) return;

        alerts.raise(new Alert(chamber, crop, response.get().alertMessage(chamber, crop)));
        profile.cost(breach).ifPresent(meter::record);
        if (response.get().device() != null) {
            controller.request(chamber, response.get().device());
        }
    }
}
