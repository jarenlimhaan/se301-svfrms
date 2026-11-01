package org.example.alert;

import java.util.List;

/** Moves active alerts to the archive. The role the end-of-run archiving step depends on. */
public interface AlertArchive {
    /** Atomically moves all active alerts to the archive and returns them. */
    List<Alert> archiveActive();
}
