package org.example.report;

import java.util.List;

/** The short daily summary printed to the console. */
public final class DailyReport implements Report {
    @Override
    public List<String> render(FacilitySnapshot s) {
        return List.of(
                "===== DAILY REPORT =====",
                "Generated Alerts: " + s.generatedAlerts(),
                "Active Alerts: " + s.activeAlerts(),
                "Water Usage: " + s.waterUsage() + " L",
                "Energy Usage: " + s.energyUsage() + " kWh");
    }
}
