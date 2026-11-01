package org.example.resource;

import java.util.Map;

/** Read access to total resource usage. The role reporting code depends on. */
public interface UsageTotals {
    /** Immutable total for every resource known by this implementation. */
    Map<Resource, Integer> totals();
}
