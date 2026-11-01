package org.example.report;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.example.alert.AlertArchive;

/**
 * Facade for end-of-run reporting: archiving alerts, printing the daily report and saving the archive
 * file. Callers make three calls instead of coordinating alerts, usage totals, summaries and reports.
 */
public final class ReportService {
    private final AlertArchive alerts;
    private final Supplier<FacilitySnapshot> snapshots;
    private final Consumer<String> activity;
    private final ReportWriter writer;
    private final Consumer<String> console;

    public ReportService(AlertArchive alerts, Supplier<FacilitySnapshot> snapshots, Consumer<String> activity,
                         ReportWriter writer, Consumer<String> console) {
        this.alerts = alerts;
        this.snapshots = snapshots;
        this.activity = activity;
        this.writer = writer;
        this.console = console;
    }

    public void archiveAlerts() {
        int moved = alerts.archiveActive().size();
        if (moved > 0) {
            activity.accept("Archived " + moved + " alerts");
            console.accept("Archiving alerts... " + moved + " moved to archive.");
        } else {
            activity.accept("No alerts to archive");
            console.accept("Archiving alerts... no active alerts to archive.");
        }
    }

    public void print(Report report, String activity) {
        report.render(snapshots.get()).forEach(console);
        this.activity.accept(activity);
    }

    /**
     * Writes the archive report to {@code file}, creating its directory if needed.
     *
     * @throws IOException if the report could not be written; the caller decides how to fail
     */
    public void save(Report report, Path file) throws IOException {
        writer.write(file, report.render(snapshots.get()));
    }
}
