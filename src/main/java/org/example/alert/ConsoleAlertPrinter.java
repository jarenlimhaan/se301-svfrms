package org.example.alert;

/** Prints alerts in the legacy console format. */
public final class ConsoleAlertPrinter implements AlertListener {
    @Override
    public void onAlert(Alert alert) {
        System.out.println("[" + alert.chamber() + "][" + alert.crop() + "][ALERT] " + alert.message());
    }
}
