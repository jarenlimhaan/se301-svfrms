package org.example.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.example.alert.Alert;
import org.example.alert.AlertLedger;
import org.example.resource.UsageTotals;

/**
 * Facade for end-of-run reporting: archiving alerts, printing the daily report and saving the archive
 * file. Callers make three calls instead of coordinating alerts, usage totals, summaries and reports.
 */
public final class ReportService {
    private final AlertLedger alerts;
    private final UsageTotals meter;
    private final ChamberSummaries summaries;
    private final Report dailyReport;
    private final Report archiveReport;
    private final Consumer<String> console;
    private final List<String> activity = Collections.synchronizedList(new ArrayList<>());

    public ReportService(AlertLedger alerts, UsageTotals meter, ChamberSummaries summaries, Report dailyReport,
            Report archiveReport, Consumer<String> console) {
        this.alerts = alerts;
        this.meter = meter;
        this.summaries = summaries;
        this.dailyReport = dailyReport;
        this.archiveReport = archiveReport;
        this.console = console;
    }

    public void archiveAlerts() {
        int moved = alerts.archiveActive().size();
        if (moved > 0) {
            activity.add("Archived " + moved + " alerts");
            console.accept("Archiving alerts... " + moved + " moved to archive.");
        } else {
            activity.add("No alerts to archive");
            console.accept("Archiving alerts... no active alerts to archive.");
        }
    }

    public void generateDailyReport() {
        dailyReport.render(snapshot()).forEach(console);
        activity.add("Daily Report");
    }

    /**
     * Writes the archive report to {@code file}, creating its directory if needed.
     *
     * @throws IOException if the report could not be written; the caller decides how to fail
     */
    public void saveArchiveReport(Path file) throws IOException {
        Path dir = file.getParent(); // kept relative so error messages name the path as legacy did
        if (dir != null) Files.createDirectories(dir);
        Files.write(file, archiveReport.render(snapshot()), StandardCharsets.UTF_8);
        console.accept("Archive report saved to: " + file.toAbsolutePath());
    }

    private FacilitySnapshot snapshot() {
        List<String> activityCopy;
        synchronized (activity) {
            activityCopy = List.copyOf(activity);
        }
        return new FacilitySnapshot(Instant.now(), alerts.generatedCount(), alerts.activeCount(), meter.water(),
                meter.energy(), activityCopy, summaries.lines(), alerts.archived().stream().map(Alert::message).toList());
    }
}
