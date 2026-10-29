package org.example.monitoring;

import org.example.device.Device;

/**
 * What the facility does about one kind of breach: the alert label and the corrective device.
 *
 * @param namesCrop whether the alert text names the crop (crop-specific thresholds) or not (facility-wide ones)
 * @param device    device to activate, or {@code null} if the breach only raises an alert
 */
public record Response(String label, boolean namesCrop, Device device) {

    public String alertMessage(String chamber, String crop) {
        return namesCrop ? chamber + " " + crop + " " + label : chamber + " " + label;
    }
}
