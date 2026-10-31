package org.example.report;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.example.resource.Resource;

/**
 * An immutable point-in-time copy of everything a report needs, so reports never touch live state.
 * Deeply immutable: {@link Instant} is immutable and every list is copied into an unmodifiable list.
 */
public record FacilitySnapshot(Instant timestamp, int generatedAlerts, int activeAlerts,
                               Map<Resource, Integer> resourceUsage, List<String> activity, List<String> chamberSummaries,
                               List<String> archivedAlerts) {

    public FacilitySnapshot {
        Objects.requireNonNull(timestamp, "timestamp");
        resourceUsage = Map.copyOf(resourceUsage);
        activity = List.copyOf(activity);
        chamberSummaries = List.copyOf(chamberSummaries);
        archivedAlerts = List.copyOf(archivedAlerts);
    }

    /** Compatibility constructor for the legacy report's water and energy totals. */
    public FacilitySnapshot(Instant timestamp, int generatedAlerts, int activeAlerts, int waterUsage, int energyUsage,
                            List<String> activity, List<String> chamberSummaries, List<String> archivedAlerts) {
        this(timestamp, generatedAlerts, activeAlerts,
                Map.of(Resource.WATER, waterUsage, Resource.ENERGY, energyUsage),
                activity, chamberSummaries, archivedAlerts);
    }

    public int resourceUsage(Resource resource) { return resourceUsage.getOrDefault(resource, 0); }

    public int waterUsage() { return resourceUsage(Resource.WATER); }

    public int energyUsage() { return resourceUsage(Resource.ENERGY); }
}
