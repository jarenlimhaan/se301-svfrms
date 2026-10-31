package org.example.report;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * An immutable point-in-time copy of everything a report needs, so reports never touch live state.
 * Deeply immutable: {@link Instant} is immutable and every list is copied into an unmodifiable list.
 */
public record FacilitySnapshot(Instant timestamp, int generatedAlerts, int activeAlerts, int waterUsage,
                               int energyUsage, List<String> activity, List<String> chamberSummaries,
                               List<String> archivedAlerts) {

    public FacilitySnapshot {
        Objects.requireNonNull(timestamp, "timestamp");
        activity = List.copyOf(activity);
        chamberSummaries = List.copyOf(chamberSummaries);
        archivedAlerts = List.copyOf(archivedAlerts);
    }
}
