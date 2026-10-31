package org.example.alert;

/**
 * Prints alerts with their severity, in the spec's format, e.g. {@code CRITICAL: CH3 Lettuce High Temp}.
 * Selected with {@code --alert-format=severity}; the default console format is unchanged.
 */
public final class SeverityAlertPrinter implements AlertListener {
    @Override
    public void onAlert(Alert alert) {
        System.out.println(alert.severity() + ": " + alert.message());
    }
}
