package org.example.alert;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * The console alert formats, by name. Adding a format is one entry in this table: name validation,
 * the command-line usage text and wiring all read this table, so none of them change.
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
     * Returns {@code name} if it is a known format; the only place format names are validated.
     *
     * @throws IllegalArgumentException if no such format exists
     */
    public static String requireKnown(String name) {
        if (!FORMATS.containsKey(name)) {
            throw new IllegalArgumentException("unknown alert format '" + name + "'; known: " + names());
        }
        return name;
    }

    /**
     * A new printer for the named format.
     *
     * @throws IllegalArgumentException if no such format exists
     */
    public static AlertListener printer(String name) {
        return FORMATS.get(requireKnown(name)).get();
    }
}
