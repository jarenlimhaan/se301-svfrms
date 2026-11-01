package org.example.report;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.example.alert.Alert;
import org.example.alert.AlertHistory;
import org.example.resource.UsageTotals;

/** Owns report activity and creates immutable snapshots of completed facility work. */
public final class FacilitySnapshotFactory {
    private final AlertHistory alerts;
    private final UsageTotals meter;
    private final ChamberSummaries summaries;
    private final List<String> activity = Collections.synchronizedList(new ArrayList<>());

    public FacilitySnapshotFactory(AlertHistory alerts, UsageTotals meter, ChamberSummaries summaries) {
        this.alerts = alerts;
        this.meter = meter;
        this.summaries = summaries;
    }

    public void recordActivity(String description) {
        activity.add(description);
    }

    public FacilitySnapshot create() {
        List<String> activityCopy;
        synchronized (activity) {
            activityCopy = List.copyOf(activity);
        }
        return new FacilitySnapshot(Instant.now(), alerts.generatedCount(), alerts.activeCount(), meter.totals(),
                activityCopy, summaries.lines(), alerts.archived().stream().map(Alert::message).toList());
    }
}
