package org.example;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.HashMap;

/**
 * Reproduces legacy issue C7 deterministically, without changing legacy/SVFRMS.java.
 *
 * <p>In the legacy system the control thread can die from any RuntimeException, for example the
 * NullPointerException caused by the HashMap race (C2). That race is rare, so this demo forces the
 * same failure: because {@code chamberStates} is a public, non-final static field (D6), it swaps in
 * a map that throws when the control thread reads it. The control thread then dies, the 5-slot
 * queue fills, and every chamber thread waits in {@code addControlRequest} forever.
 *
 * <p>Run from the project root:
 * <pre>
 * javac -d target/legacy legacy/SVFRMS.java legacy/LegacyHangDemo.java
 * java -cp target/legacy org.example.LegacyHangDemo
 * </pre>
 */
public final class LegacyHangDemo {
    private LegacyHangDemo() {}

    public static void main(String[] args) throws Exception {
        SVFRMS.chamberStates = new HashMap<>() {
            @Override
            public SVFRMS.ChamberState get(Object chamber) {
                if (Thread.currentThread() instanceof SVFRMS.ControlThread) {
                    throw new NullPointerException("simulated HashMap race (C2) in the control thread");
                }
                return super.get(chamber);
            }
        };
        // Create the four chambers up front so the real C2 race cannot also strike; only the forced failure happens.
        for (String chamber : new String[] {"CH1", "CH2", "CH3", "CH4"}) {
            SVFRMS.chamberStates.put(chamber, new SVFRMS.ChamberState());
        }
        Thread legacyMain = new Thread(() -> {
            try {
                SVFRMS.main(new String[0]);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, "legacy-main");
        legacyMain.setDaemon(true);
        legacyMain.start();
        legacyMain.join(10_000);

        System.out.flush();
        System.out.println();
        if (!legacyMain.isAlive()) {
            System.out.println("RESULT: legacy main finished; hang NOT reproduced");
            System.exit(1);
        }
        System.out.println("RESULT: legacy main still blocked after 10 s -> C7 hang reproduced");
        for (ThreadInfo info : ManagementFactory.getThreadMXBean().dumpAllThreads(false, false)) {
            StackTraceElement[] stack = info.getStackTrace();
            for (StackTraceElement frame : stack) {
                if (frame.getMethodName().equals("addControlRequest") || frame.getMethodName().equals("main")
                        && frame.getClassName().equals("org.example.SVFRMS")) {
                    System.out.println("  " + info.getThreadName() + " is " + info.getThreadState()
                            + " in SVFRMS." + frame.getMethodName() + " (line " + frame.getLineNumber() + ")");
                    break;
                }
            }
        }
        System.exit(0);
    }
}
