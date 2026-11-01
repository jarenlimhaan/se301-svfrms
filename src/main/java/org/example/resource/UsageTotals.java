package org.example.resource;

import java.util.Map;

/** Read access to total resource usage. The role reporting code depends on. */
public interface UsageTotals {
    /** Immutable total for every resource known by this implementation. */
    Map<Resource, Integer> totals();

    /** Total usage recorded for {@code resource}. */
    default int total(Resource resource) { return totals().getOrDefault(resource, 0); }

    /** Total water used, in litres. */
    default int water() { return total(Resource.WATER); }

    /** Total energy used, in kWh. */
    default int energy() { return total(Resource.ENERGY); }
}
