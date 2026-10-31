package org.example.report;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** The full report saved to the archive file: totals, activity, per-chamber summary and archived alerts. */
public final class ArchiveReport implements Report {
    @Override
    public List<String> render(FacilitySnapshot s) {
        List<String> lines = new ArrayList<>();
        lines.add("===== DAILY REPORT =====");
        // Date.toString gives the same timestamp format as the legacy report.
        lines.add("Timestamp: " + Date.from(s.timestamp()));
        lines.add("Generated Alerts: " + s.generatedAlerts());
        lines.add("Active Alerts: " + s.activeAlerts());
        lines.add("Water Usage: " + s.waterUsage() + " L");
        lines.add("Energy Usage: " + s.energyUsage() + " kWh");
        lines.add("Archived alerts: " + s.archivedAlerts().size());
        lines.add("---");
        lines.addAll(s.activity());
        if (!s.chamberSummaries().isEmpty()) {
            lines.add("--- chamber wise summary ---");
            lines.addAll(s.chamberSummaries());
        }
        if (!s.archivedAlerts().isEmpty()) {
            lines.add("--- archived alerts ---");
            lines.addAll(s.archivedAlerts());
        }
        return lines;
    }
}
