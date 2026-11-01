package org.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.example.alert.Alert;
import org.example.alert.AlertService;
import org.example.alert.Severity;
import org.example.report.ArchiveReport;
import org.example.report.ChamberSummaryTracker;
import org.example.report.FacilitySnapshotFactory;
import org.example.report.ReportService;
import org.example.report.Utf8ReportWriter;
import org.example.resource.ResourceMeter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Finding 9: a report that cannot be saved is an error, not a printed message. */
class ReportPersistenceTest {
    @TempDir
    Path dir;

    private ReportService service() {
        AlertService alerts = Chambers.alerts(List.of());
        alerts.raise(new Alert("CH1", "Tomato", "CH1 Tomato High Temp", Severity.WARNING));
        FacilitySnapshotFactory snapshots = new FacilitySnapshotFactory(
                alerts, new ResourceMeter(), new ChamberSummaryTracker());
        ReportService reports = new ReportService(alerts, snapshots::create, snapshots::recordActivity,
                new Utf8ReportWriter(), System.out::println);
        reports.archiveAlerts();
        return reports;
    }

    /** A path whose parent "directory" is actually a file, so it can never be written. */
    private Path unwritable() throws IOException {
        Path blocker = Files.writeString(dir.resolve("not-a-directory"), "x");
        return blocker.resolve("daily_report.txt");
    }

    @Test
    void reportIsWrittenWithTheLegacyLayout() throws Exception {
        Path file = dir.resolve("archive").resolve("daily_report.txt");
        service().save(new ArchiveReport(), file);
        List<String> lines = Files.readAllLines(file);
        assertEquals("===== DAILY REPORT =====", lines.get(0));
        assertTrue(lines.get(1).startsWith("Timestamp: "));
        assertTrue(lines.contains("--- archived alerts ---") && lines.contains("CH1 Tomato High Temp"), lines.toString());
    }

    @Test
    void unwritableDestinationThrows() throws Exception {
        Path file = unwritable();
        assertThrows(IOException.class, () -> service().save(new ArchiveReport(), file));
    }

    @Test
    void applicationExitsWithStatus2WhenTheArchiveCannotBeSaved() throws Exception {
        assertEquals(SVFRMS.EXIT_ARCHIVE_NOT_SAVED, SVFRMS.run(new String[] {"CH1=Tomato"}, unwritable()));
    }

    @Test
    void applicationExitsWithStatus0WhenTheArchiveIsSaved() throws Exception {
        Path file = dir.resolve("daily_report.txt");
        assertEquals(SVFRMS.EXIT_OK, SVFRMS.run(new String[] {"CH1=Tomato"}, file));
        assertTrue(Files.size(file) > 0);
    }
}
