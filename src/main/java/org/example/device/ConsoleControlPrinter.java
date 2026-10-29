package org.example.device;

/** Prints device activations to the console in green. */
public final class ConsoleControlPrinter implements DeviceListener {
    private static final String GREEN = "\u001B[32m";
    private static final String RESET = "\u001B[0m";

    @Override
    public void onActivated(String chamber, Device device, String effect) {
        print(chamber, device.name() + " Activated");
        print(chamber, effect);
    }

    private static void print(String chamber, String message) {
        System.out.println("\t" + GREEN + "[" + chamber + "][CONTROL] " + message + RESET);
    }
}
