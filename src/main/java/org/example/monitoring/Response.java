package org.example.monitoring;

import java.util.Objects;
import java.util.Optional;
import org.example.alert.Severity;
import org.example.device.Device;

/**
 * What the facility does about one kind of breach: the alert label and severity, and the corrective device.
 *
 * @param namesCrop whether the alert text names the crop (crop-specific thresholds) or not (facility-wide ones)
 * @param device    device to activate; empty if the breach only raises an alert
 */
public record Response(String label, Severity severity, boolean namesCrop, Optional<Device> device) {

    public Response {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(device, "device");
    }

    /** A response that activates {@code device}, or only raises the alert if it is {@code null}. */
    public Response(String label, Severity severity, boolean namesCrop, Device device) {
        this(label, severity, namesCrop, Optional.ofNullable(device));
    }

    public String alertMessage(String chamber, String crop) {
        return namesCrop ? chamber + " " + crop + " " + label : chamber + " " + label;
    }
}
