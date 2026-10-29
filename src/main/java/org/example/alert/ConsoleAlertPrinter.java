package org.example.alert;

public final class ConsoleAlertPrinter implements AlertListener {
    @Override
    public void onAlert(Alert alert) {
        System.out.println("[" + alert.chamber() + "][" + alert.crop() + "][ALERT] " + alert.message());
    }
}
