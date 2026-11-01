package org.example.resource;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Facility-wide water (L) and energy (kWh) counters, safe to update from any thread. Usage only ever grows. */
public final class ResourceMeter implements UsageRecorder, UsageTotals {
    private final Map<Resource, AtomicInteger> totals = new EnumMap<>(Resource.class);

    public ResourceMeter() {
        for (Resource resource : Resource.values()) totals.put(resource, new AtomicInteger());
    }

    @Override
    public void record(Usage usage) {
        totals.get(usage.resource()).addAndGet(usage.amount());
    }

    @Override
    public Map<Resource, Integer> totals() {
        Map<Resource, Integer> snapshot = new EnumMap<>(Resource.class);
        totals.forEach((resource, amount) -> snapshot.put(resource, amount.get()));
        return Map.copyOf(snapshot);
    }
}
