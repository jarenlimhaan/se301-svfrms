package org.example.alert;

import java.util.List;

/** Read and archive access to the alert history. The role reporting code depends on. */
public interface AlertLedger {
    /** Atomically moves all active alerts to the archive and returns them. */
    List<Alert> archiveActive();

    int generatedCount();

    int activeCount();

    List<Alert> archived();
}
