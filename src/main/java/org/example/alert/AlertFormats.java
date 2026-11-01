package org.example.alert;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * The console alert formats, by name. Adding a format is one entry in this table; command-line
 * parsing and wiring look formats up here and never change.
 */
public final class AlertFormats {
    /** The format used when none is chosen: the legacy {@code [CH1][Tomato][ALERT] ...} lines. */
    public static final String DEFAULT = "legacy";

    private static final Map<String, Supplier<AlertListener>> FORMATS = Map.of(
            "legacy", ConsoleAlertPrinter::new,
            "severity", SeverityAlertPrinter::new);

    private AlertFormats() {}

    /** The known format names, sorted. */
    public static Set<String> names() {
        return new TreeSet<>(FORMATS.keySet());
    }

    /**
     * A new printer for the named format.
     *
     * @throws IllegalArgumentException if no such format exists
     */
    public static AlertListener printer(String name) {
        Supplier<AlertListener> format = FORMATS.get(name);
        if (format == null) throw new IllegalArgumentException("unknown alert format '" + name + "'; known: " + names());
        return format.get();
    }
}
