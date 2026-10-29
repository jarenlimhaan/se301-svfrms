package org.example.report;

import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.example.alert.Alert;
import org.example.alert.AlertListener;
import org.example.device.Device;
import org.example.device.DeviceListener;

/** Keeps a per-chamber tally of alerts and mitigation actions by listening to both. */
public final class ChamberSummaryTracker implements AlertListener, DeviceListener {

    private static final class Summary {
        private int alerts;
        private int actions;
        private String lastAction = "No action";
        private String lastAlert = "None";

        synchronized void alert(String message) {
            alerts++;
            lastAlert = message;
        }

        synchronized void action(String message) {
            actions++;
            lastAction = message;
        }

        synchronized String describe() {
            return "Alerts=" + alerts + ", Actions=" + actions + ", Last Action=" + lastAction + ", Last Alert=" + lastAlert;
        }
    }

    private final ConcurrentMap<String, Summary> summaries = new ConcurrentHashMap<>();

    @Override
    public void onAlert(Alert alert) {
        summary(alert.chamber()).alert(alert.message());
    }

    @Override
    public void onActivated(String chamber, Device device, String effect) {
        summary(chamber).action(device.name() + " Activated");
    }

    /** One line per chamber, sorted by chamber name. */
    public List<String> lines() {
        return new TreeMap<>(summaries).entrySet().stream()
                .map(e -> e.getKey() + " -> " + e.getValue().describe())
                .toList();
    }

    private Summary summary(String chamber) {
        return summaries.computeIfAbsent(chamber, c -> new Summary());
    }
}
