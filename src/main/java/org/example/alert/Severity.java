package org.example.alert;

/** How urgently an alert needs attention (spec §3.4). */
public enum Severity {
    /** Informational; no immediate action needed. */
    INFO,
    /** A condition that may need attention to prevent problems. */
    WARNING,
    /** A severe condition needing immediate action to protect crops or the system. */
    CRITICAL
}
