package org.example.resource;

import java.util.Objects;

/** An amount of a resource consumed, e.g. 10 kWh of energy. The amount cannot be negative. */
public record Usage(Resource resource, int amount) {

    public Usage {
        Objects.requireNonNull(resource, "resource");
        if (amount < 0) throw new IllegalArgumentException("usage cannot be negative: " + amount);
    }
}
