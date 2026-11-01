package org.example.alert;

import java.util.List;

/** Read-only view of the alert counters and archive. The role report snapshots depend on. */
public interface AlertHistory {
    int generatedCount();

    int activeCount();

    List<Alert> archived();
}
