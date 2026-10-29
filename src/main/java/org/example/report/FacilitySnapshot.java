package org.example.report;

import java.util.Date;
import java.util.List;

/** An immutable point-in-time copy of everything a report needs, so reports never touch live state. */
public record FacilitySnapshot(Date timestamp, int generatedAlerts, int activeAlerts, int waterUsage,
                               int energyUsage, List<String> activity, List<String> chamberSummaries,
                               List<String> archivedAlerts) {}
