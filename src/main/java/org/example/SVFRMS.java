package org.example;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.example.alert.AlertListener;
import org.example.alert.AlertService;
import org.example.alert.ConsoleAlertPrinter;
import org.example.alert.SeverityAlertPrinter;
import org.example.crop.CropRegistry;
import org.example.device.ConsoleControlPrinter;
import org.example.device.ControlDispatcher;
import org.example.device.DispatchStats;
import org.example.device.StaleRequestPolicy;
import org.example.monitoring.ResponsePolicy;
import org.example.monitoring.SensorProcessor;
import org.example.report.ArchiveReport;
import org.example.report.ChamberSummaryTracker;
import org.example.report.DailyReport;
import org.example.report.ReportService;
import org.example.resource.ResourceMeter;
import org.example.sensor.ChamberRegistry;
import org.example.sensor.SensorLog;
import org.example.simulation.ChamberSimulation;
import org.example.simulation.RandomReadingSource;
import org.example.simulation.ReadingSource;
import org.example.simulation.SimulationFailedException;
import org.example.simulation.SimulationRunner;

/**
 * Entry point and composition root: builds the components, wires them together, and runs the
 * chamber simulations. See {@link CommandLine#USAGE}; e.g. {@code java -jar run.jar CH5=Kale}, or
 * {@code java -jar run.jar --add-crop="Okra:temperature=24..30" CH5=Okra} for a crop defined at run time.
 * With no arguments the legacy four chambers run.
 *
 * <p>Exit status: 0 success, 1 the run failed (a chamber or the control worker), 2 the archive
 * report could not be saved, 3 invalid arguments. A failed run writes no report.
 */
public final class SVFRMS {
    static final Path REPORT_FILE = Path.of("archive", "daily_report.txt");
    static final int EXIT_OK = 0;
    static final int EXIT_RUN_FAILED = 1;
    static final int EXIT_ARCHIVE_NOT_SAVED = 2;
    static final int EXIT_BAD_ARGUMENTS = 3;

    private static final int CONTROL_QUEUE_CAPACITY = 5;
    private static final int CYCLES_PER_CHAMBER = 20;
    private static final Duration CONTROL_SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);
    /** Legacy behaviour: a queued device runs even if a newer reading arrived. SKIP revalidates instead. */
    private static final StaleRequestPolicy STALE_REQUESTS = StaleRequestPolicy.EXECUTE;

    private SVFRMS() {}

    public static void main(String[] args) throws InterruptedException {
        int status = run(args, REPORT_FILE);
        if (status != EXIT_OK) System.exit(status);
    }

    /** Runs the whole facility once and returns the exit status (never calls {@code System.exit}). */
    static int run(String[] args, Path reportFile) throws InterruptedException {
        return run(args, reportFile, RandomReadingSource.legacy());
    }

    /** As {@link #run(String[], Path)}, with the sensor feed supplied (tests use this to inject failures). */
    static int run(String[] args, Path reportFile, ReadingSource readings) throws InterruptedException {
        CommandLine cli;
        CropRegistry crops;
        try {
            cli = CommandLine.parse(args);
            crops = cli.cropRegistry();
        } catch (IllegalArgumentException | IOException e) {
            System.err.println("ERROR: " + e.getMessage());
            System.err.println(CommandLine.USAGE);
            return EXIT_BAD_ARGUMENTS;
        }
        if (cli.help()) {
            System.out.println(CommandLine.USAGE);
            return EXIT_OK;
        }
        cli.chambers().values().stream().filter(c -> crops.find(c).isEmpty()).distinct().forEach(c ->
                System.out.println("WARNING: no crop profile for '" + c + "'; only facility-wide checks apply. Known: " + crops.names()));

        ResourceMeter meter = new ResourceMeter();
        ChamberSummaryTracker summaries = new ChamberSummaryTracker();
        AlertListener alertPrinter = cli.severityAlerts() ? new SeverityAlertPrinter() : new ConsoleAlertPrinter();
        AlertService alerts = new AlertService(List.of(summaries, alertPrinter));
        ChamberRegistry chamberStates = new ChamberRegistry();
        ReportService reports = new ReportService(alerts, meter, summaries, new DailyReport(), new ArchiveReport());

        ControlDispatcher dispatcher = new ControlDispatcher(chamberStates, CONTROL_QUEUE_CAPACITY,
                List.of(summaries, new ConsoleControlPrinter()), CONTROL_SHUTDOWN_TIMEOUT, STALE_REQUESTS);
        try (dispatcher) {
            dispatcher.start();
            SensorProcessor processor = new SensorProcessor(crops, chamberStates, new SensorLog(), alerts, meter,
                    ResponsePolicy.standard(), dispatcher);
            List<ChamberSimulation> simulations = new ArrayList<>();
            cli.chambers().forEach((chamber, crop) -> simulations.add(
                    new ChamberSimulation(chamber, crop, CYCLES_PER_CHAMBER, processor, readings, meter)));
            // Waits for every chamber; throws (and no report is written) unless all completed every cycle.
            SimulationRunner.runAll(simulations);
        } catch (SimulationFailedException | IllegalStateException e) {
            // IllegalStateException: the control worker failed or lost requests (see ControlDispatcher.close).
            System.err.println("ERROR: run failed, no report written: " + e.getMessage());
            for (Throwable cause : e.getSuppressed()) System.err.println("  caused by: " + cause);
            return EXIT_RUN_FAILED;
        } // drains pending device requests, then stops the control thread
        reportProblems(dispatcher.stats(), alerts.listenerFailures());

        // Run in a fixed order: the legacy code raced these two threads, so the report's numbers varied run to run.
        reports.archiveAlerts();
        reports.generateDailyReport();
        System.out.println("System finished sensor processing.");
        try {
            reports.saveArchiveReport(reportFile);
        } catch (IOException e) {
            System.out.println("Could not save archive report: " + e.getMessage()); // legacy text
            System.err.println("  cause: " + e); // the exception type says why (e.g. FileAlreadyExistsException)
            return EXIT_ARCHIVE_NOT_SAVED;
        }
        return EXIT_OK;
    }

    /** Tells the operator (on stderr) about failures that were isolated during the run. Silent when all went well. */
    private static void reportProblems(DispatchStats stats, int alertListenerFailures) {
        if (stats.hasProblems() || stats.skipped() > 0 || alertListenerFailures > 0) {
            System.err.println("WARNING: device requests: " + stats.executed() + " executed, " + stats.failed()
                    + " failed, " + stats.skipped() + " skipped as stale; listener failures: "
                    + (stats.listenerFailures() + alertListenerFailures) + " (details above)");
        }
    }
}
