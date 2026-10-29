package org.example;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.example.alert.AlertService;
import org.example.alert.ConsoleAlertPrinter;
import org.example.crop.CropCatalog;
import org.example.crop.CropRegistry;
import org.example.device.ConsoleControlPrinter;
import org.example.device.ControlDispatcher;
import org.example.monitoring.ResponsePolicy;
import org.example.monitoring.SensorProcessor;
import org.example.report.ChamberSummaryTracker;
import org.example.report.ReportService;
import org.example.resource.ResourceMeter;
import org.example.sensor.ChamberRegistry;
import org.example.sensor.SensorLog;
import org.example.simulation.ChamberSimulation;

/**
 * Entry point and composition root: builds the components, wires them together, and runs the
 * chamber simulations. Usage: {@code java -jar run.jar [CHAMBER=Crop ...]}, e.g. {@code CH5=Kale}.
 * With no arguments the legacy four chambers run.
 */
public final class SVFRMS {
    static final Path REPORT_FILE = Path.of("archive", "daily_report.txt");
    private static final int CONTROL_QUEUE_CAPACITY = 5;
    private static final int CYCLES_PER_CHAMBER = 20;

    private SVFRMS() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> chambers = args.length == 0 ? defaultChambers() : parseChambers(args);
        CropRegistry crops = CropCatalog.defaultRegistry();
        chambers.values().stream().filter(c -> crops.find(c).isEmpty()).distinct().forEach(c ->
                System.out.println("WARNING: no crop profile for '" + c + "'; only facility-wide checks apply. Known: " + crops.names()));

        ResourceMeter meter = new ResourceMeter();
        ChamberSummaryTracker summaries = new ChamberSummaryTracker();
        AlertService alerts = new AlertService(List.of(summaries, new ConsoleAlertPrinter()));
        ChamberRegistry chamberStates = new ChamberRegistry();
        ReportService reports = new ReportService(alerts, meter, summaries);

        try (ControlDispatcher dispatcher = new ControlDispatcher(
                chamberStates, CONTROL_QUEUE_CAPACITY, List.of(summaries, new ConsoleControlPrinter()))) {
            dispatcher.start();
            SensorProcessor processor = new SensorProcessor(crops, chamberStates, new SensorLog(), alerts, meter,
                    ResponsePolicy.standard(), dispatcher);
            try (ExecutorService pool = Executors.newFixedThreadPool(chambers.size())) {
                chambers.forEach((chamber, crop) ->
                        pool.execute(new ChamberSimulation(chamber, crop, CYCLES_PER_CHAMBER, processor, meter)));
            } // waits for every chamber to finish
        } // drains pending device requests, then stops the control thread

        // Run in a fixed order: the legacy code raced these two threads, so the report's numbers varied run to run.
        reports.archiveAlerts();
        reports.generateDailyReport();
        System.out.println("System finished sensor processing.");
        reports.saveArchiveReport(REPORT_FILE);
    }

    private static Map<String, String> defaultChambers() {
        Map<String, String> chambers = new LinkedHashMap<>();
        chambers.put("CH1", "Tomato");
        chambers.put("CH2", "Strawberry");
        chambers.put("CH3", "Lettuce");
        chambers.put("CH4", "Chili");
        return chambers;
    }

    static Map<String, String> parseChambers(String[] args) {
        Map<String, String> chambers = new LinkedHashMap<>();
        for (String arg : args) {
            String[] parts = arg.split("=", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new IllegalArgumentException("Expected CHAMBER=Crop, got: " + arg);
            }
            chambers.put(parts[0].trim(), parts[1].trim());
        }
        return chambers;
    }
}
