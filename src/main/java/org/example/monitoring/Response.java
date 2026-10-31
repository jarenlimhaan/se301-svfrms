package org.example.monitoring;

import java.util.Objects;
import org.example.alert.Severity;
import org.example.device.Device;

/**
 * What the facility does about one kind of breach: the alert label and severity, and the corrective device.
 *
 * @param namesCrop whether the alert text names the crop (crop-specific thresholds) or not (facility-wide ones)
 * @param device    device to activate, or {@code null} if the breach only raises an alert
 */
public record Response(String label, Severity severity, boolean namesCrop, Device device) {

    public Response {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(severity, "severity");
    }

    public String alertMessage(String chamber, String crop) {
        return namesCrop ? chamber + " " + crop + " " + label : chamber + " " + label;
    }
}
