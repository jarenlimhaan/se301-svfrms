# AGENTS.md

Instructions for AI coding agents working in this repo for **SE301 Group 6**
(Yuen Wai Yee, Jaren Lim Haan, Liew Tian Le).

## What this project is

A refactoring of the legacy SVFRMS (Singapore Vertical Farming Research Management System),
a single 393-line Java class (`legacy/SVFRMS.java`), into a multi-package, thread-safe design.
**The rule is: improve the design, never change behaviour.** Thresholds, alert texts, resource
costs, device effects, console output formats and the `archive/daily_report.txt` layout must stay
identical to the legacy version.

The spec PDF is not in the repo (it is classified Restricted). Get it from eLearn. Its key points:
- §4.1 No single-file solution. §4.2 SOLID. §4.3 Design patterns, with a justification for each.
- §4.4 Fix race conditions, synchronisation issues and deadlocks.
- §4.5 Add or remove crops with minimal code change (Kale, Bell Pepper, Bok Choy and Eggplant are done).
- §5 Submit one zip with `src/`, `run.jar` and `README.pdf` (3–6 pages plus a cover page).
- Deadline **1 Nov 2026, 23:59** (no extensions; more than 1 hour late scores 0). Presentation **2 Nov 2026**.

## Layout

| Path | What |
|---|---|
| `src/main/java/org/example/` | Refactored code. `SVFRMS.java` is the composition root and the only place that wires concrete classes. |
| `.../crop` | `CropProfile` (Builder), `Range`, `CropRegistry`, `CropCatalog` (to add a crop, add one entry here) |
| `.../sensor` | `SensorType`, `ChamberState`, `ChamberRegistry`, `SensorLog` |
| `.../monitoring` | `SensorProcessor` (the single breach rule), `ResponsePolicy` |
| `.../device` | `Device` strategies, `ControlDispatcher` (bounded queue + worker + poison pill) |
| `.../alert`, `.../report`, `.../resource`, `.../simulation` | Alerts (Observer), reports, water/energy meter, chamber threads |
| `src/test/java/org/example/SVFRMSTest.java` | 6 JUnit tests: behaviour parity, new crops, concurrency |
| `legacy/SVFRMS.java` | The original code. **Read-only reference**: use it to check behaviour parity. |
| `README.md` → `README.pdf` | Technical report. Edit the `.md` file, then regenerate the PDF. |
| `run.jar` | Submission jar (copy of `target/run.jar`). |

## Build, test, run

Requires JDK 25 (`pom.xml` uses `release 25`). On JDK 21, add `-Dmaven.compiler.release=21`.

```bash
mvn clean package                  # compile, run tests, build target/run.jar
cp target/run.jar run.jar          # refresh the submission jar
java -jar run.jar                  # legacy 4 chambers (CH1 Tomato, CH2 Strawberry, CH3 Lettuce, CH4 Chili)
java -jar run.jar CH5=Kale         # new-crop demo
```

Always run `mvn clean package` and confirm **all tests pass** before committing.

## Rules for agents

1. **Do not change behaviour.** If you touch `monitoring`, `device`, `crop` or `report`, compare
   the output with the legacy version: run both several times, replace numbers with `N`, and diff
   the sets of distinct output lines. The only accepted difference is that the legacy version
   crashes (`NullPointerException` from its HashMap race).
2. **Keep it thread-safe.** Never hold one lock while acquiring another. Call listeners outside
   locks. Use `ConcurrentHashMap`, atomics, or the owning class's lock. Never swallow
   `InterruptedException`; restore the interrupt flag instead.
3. **No new runtime dependencies.** `run.jar` must run with plain `java -jar`.
4. **Match the existing style.** Constructor injection, small interfaces, records for values, and
   short Javadoc on public types. To add a crop, edit only `CropCatalog`.
5. **Keep the report in sync.** If code changes affect anything described in `README.md`, update
   it and regenerate `README.pdf` (3–6 pages excluding the cover; diagrams must render as images).
6. **Never commit** `target/`, `archive/`, `submission/`, zips or the spec PDF.
7. **Honor code.** This repo is private. Never share code or the report outside Group 6.

## Status (29 Oct 2026)

- ✅ Refactor done and verified against the legacy version (same output formats; 0 errors in 70 runs vs. 11 crashes in 30 legacy runs)
- ✅ `README.pdf` is final, with the Group 6 cover
- ✅ The submission zip was built and tested (it is not in the repo; rebuild it from `src/`, `run.jar`, `README.pdf` and `pom.xml`)
- ⬜ Optional: rebuild `run.jar` on JDK 25 so it matches `pom.xml` (the current jar is Java 21 bytecode and runs fine on 25)
- ⬜ **Presentation slides** for 2 Nov: 5 minutes plus a 2-minute demo covering legacy issues, SOLID and the refactor, a new-crop demo,
  the concurrency fixes, and evidence of maintainability and reliability
- ⬜ One member submits the zip on eLearn before the deadline

## Building the submission zip

Use forward-slash paths (PowerShell 5.1 `Compress-Archive` writes backslashes, which break on macOS and Linux):

```bash
C:/Windows/System32/tar.exe -a -c -f SVFRMS-submission.zip src run.jar README.pdf pom.xml   # Windows
zip -r SVFRMS-submission.zip src run.jar README.pdf pom.xml                                # macOS/Linux
```

Then unzip a fresh copy and run `java -jar run.jar` from it before uploading.
