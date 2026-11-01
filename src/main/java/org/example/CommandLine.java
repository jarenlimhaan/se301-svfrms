package org.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.example.alert.AlertFormats;
import org.example.crop.CropCatalog;
import org.example.crop.CropProfile;
import org.example.crop.CropRegistry;
import org.example.crop.CropSpecParser;

/**
 * Parsed command-line arguments: which crop each chamber grows, and which crops to add to or remove
 * from the built-in catalog for this run. Crops are managed here, at application level, through
 * {@link CropRegistry}; no monitoring, alerting or control code is involved.
 *
 * @param chambers    chamber name to crop name, in order; the legacy four chambers if none were given
 * @param addedCrops  crops defined with {@code --add-crop} (replacing any same-named crop)
 * @param removedCrops crop names given with {@code --remove-crop}
 * @param alertFormat the console alert format name ({@code --alert-format}); see {@link AlertFormats}
 * @param help        whether {@code --help} was given
 */
record CommandLine(Map<String, String> chambers, List<CropProfile> addedCrops, List<String> removedCrops,
                   String alertFormat, boolean help) {

    static final String USAGE = """
            Usage: java -jar run.jar [options] [CHAMBER=Crop ...]
              CHAMBER=Crop             assign a crop to a chamber, e.g. CH5=Kale (default: CH1..CH4 legacy crops)
              --add-crop=SPEC          add or replace a crop, e.g. --add-crop="Okra:temperature=24..30,soil=50..70"
              --remove-crop=Name       remove a crop from the catalog for this run
              --alert-format=FORMAT    console alert format, one of %s (default: %s)
              --help                   show this message
            SPEC is Name:sensor=min..max,... with sensors temperature, humidity, soil, light, nutrient.
            Exit status: 0 success, 1 run failed, 2 archive report not saved, 3 invalid arguments."""
            .formatted(AlertFormats.names(), AlertFormats.DEFAULT);

    CommandLine {
        chambers = Collections.unmodifiableMap(new LinkedHashMap<>(chambers.isEmpty() ? CropCatalog.legacyChambers() : chambers));
        addedCrops = List.copyOf(addedCrops);
        removedCrops = List.copyOf(removedCrops);
    }

    /** @throws IllegalArgumentException if an argument is malformed */
    static CommandLine parse(String... args) {
        Map<String, String> chambers = new LinkedHashMap<>();
        List<CropProfile> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        boolean help = false;
        String alertFormat = AlertFormats.DEFAULT;
        for (String arg : args) {
            if (arg.equals("--help") || arg.equals("-h")) {
                help = true;
            } else if (arg.startsWith("--add-crop=")) {
                added.add(CropSpecParser.parse(value(arg)));
            } else if (arg.startsWith("--remove-crop=")) {
                removed.add(value(arg));
            } else if (arg.startsWith("--alert-format=")) {
                alertFormat = AlertFormats.requireKnown(value(arg));
            } else if (arg.startsWith("--")) {
                throw new IllegalArgumentException("Unknown option: " + arg);
            } else {
                String[] parts = arg.split("=", 2);
                if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                    throw new IllegalArgumentException("Expected CHAMBER=Crop, got: " + arg);
                }
                chambers.put(parts[0].trim(), parts[1].trim());
            }
        }
        return new CommandLine(chambers, added, removed, alertFormat, help);
    }

    /** The built-in catalog with this run's additions applied, then its removals. */
    CropRegistry cropRegistry() {
        CropRegistry registry = CropCatalog.defaultRegistry();
        addedCrops.forEach(registry::register);
        for (String name : removedCrops) {
            if (registry.find(name).isEmpty()) {
                throw new IllegalArgumentException("--remove-crop: unknown crop '" + name + "'; known: " + registry.names());
            }
            registry.remove(name);
        }
        return registry;
    }

    private static String value(String option) {
        String value = option.substring(option.indexOf('=') + 1).trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Missing value: " + option);
        return value;
    }
}
