# AGENTS.md

Instructions for AI coding agents working in this repo for **SE301 Group 6**
(Yuen Wai Yee, Jaren Lim Haan, Liew Tian Le).

## What this project is

A refactoring of the legacy SVFRMS (Singapore Vertical Farming Research Management System),
a single 393-line Java class (`legacy/SVFRMS.java`), into a multi-package, thread-safe design.
**The rule is: improve the design, never change behaviour.** Thresholds, alert texts, resource
costs, device effects, console output formats and the `archive/daily_report.txt` layout must stay
identical to the legacy version.

The full brief is `SE301-Project-Specification.pdf`. Its key points:
- §4.1 No single-file solution. §4.2 SOLID. §4.3 Design patterns, with a justification for each.
- §4.4 Fix race conditions, synchronisation issues and deadlocks.
- §4.5 Add or remove crops with minimal code change (Kale, Bell Pepper, Bok Choy and Eggplant are done).
- §5 Submit one zip with `src/`, `run.jar` and `README.pdf` (3–6 pages plus a cover page).
- Deadline **1 Nov 2026, 23:59** (no extensions; more than 1 hour late scores 0). Presentation **2 Nov 2026**.

## Layout

| Path | What |
|---|---|
| `src/main/java/org/example/` | Refactored code. `SVFRMS.run` (+ `CommandLine`) is the composition root and the only place that wires concrete classes. |
| `.../crop` | `CropProfile` (Builder), `Range`, `CropRegistry`, `CropCatalog` (to add a crop, add one entry here), `CropSpecParser` (crops from the command line) |
| `.../sensor` | `SensorType` (every per-sensor loop iterates it), `ChamberState` (atomic `adjust`), `ChamberRegistry` (`ChamberStates` roles: `record`, `control`) |
| `.../monitoring` | `SensorProcessor` (the single breach rule, Sensor Failure), `ResponsePolicy` (must cover every sensor and direction; severities) |
| `.../device` | `ControlDispatcher` (worker lifecycle), `ClosableBoundedQueue` (Lock/Condition, close/abort), `RequestExecutor`, `DeviceRequest` (Command), `Devices` |
| `.../alert`, `.../report`, `.../resource`, `.../simulation`, `.../support` | Alerts (Observer, severity), reports (Facade, Strategy), water/energy meter, chamber tasks (`ReadingSource` strategy), `Listeners` (fault-isolated notification) |
| `src/test/java/org/example/` | 68 JUnit tests in 14 classes: parity, every crop bound, validation, concurrency (`DispatcherConcurrencyTest`, `ClosableBoundedQueueTest`), shutdown, run-time crops, severity, exit codes, and `ArchitectureTest` (DIP check) |
| `tools/compare_legacy.sh` | Parity check: runs legacy and `run.jar` N times, masks numbers, diffs distinct output lines, counts legacy crashes. |
| `tools/build_readme_pdf.py` | Regenerates `README.pdf` (cover + content, Mermaid as vector) from `README.md`. Needs Python `markdown`, `playwright` and `mmdc`. |
| `SE301-Project-Specification.pdf` | The assignment brief: requirements, deliverables and rubric. |
| `legacy/SVFRMS.java` | The original code. **Read-only reference**: use it to check behaviour parity. |
| `legacy/LegacyHangDemo.java` | Reproduces legacy hang C7 without editing the legacy code: `javac -d target/legacy legacy/SVFRMS.java legacy/LegacyHangDemo.java`, then `java -cp target/legacy org.example.LegacyHangDemo`. Not part of the Maven build. |
| `README.md` → `README.pdf` | Technical report. Edit the `.md` file, then regenerate the PDF and check it is 3–6 pages plus the cover. |
| `run.jar` | Submission jar (copy of `target/run.jar`). |

## Build, test, run

Requires JDK 25 (`pom.xml` uses `release 25`). On JDK 21, add `-Dmaven.compiler.release=21`.

```bash
mvn clean package                  # compile, run tests, build target/run.jar
cp target/run.jar run.jar          # refresh the submission jar
java -jar run.jar                  # legacy 4 chambers (CH1 Tomato, CH2 Strawberry, CH3 Lettuce, CH4 Chili)
java -jar run.jar CH5=Kale         # new-crop demo
java -jar run.jar --add-crop="Okra:temperature=24..30" CH5=Okra   # crop defined at run time
java -jar run.jar --alert-format=severity                       # alerts with severity
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
6. **Never commit** `target/`, `archive/`, `submission/` or zips.
7. **Honor code.** Do not discuss or exchange solutions with other teams.

## Status (31 Oct 2026)

- ✅ Review findings 1–9 fixed; strict rubric pass done (DIP/ISP role interfaces, LSP analysis, Lock/Condition queue, severity, Sensor Failure incl. unresponsive sensors, run-time crops, exit codes)
- ✅ Verified against legacy: same output line formats over 25 runs each; 150-run defect comparison is in README §2.3
- ✅ `README.md` rewritten and `README.pdf` regenerated (cover + 5 pages)
- ✅ `mvn clean package` on JDK 25 (68/68 tests pass); `run.jar` rebuilt from it (Java 25 bytecode) (31 Oct)
- ⬜ **Presentation slides** for 2 Nov: 5 minutes plus a 2-minute demo covering legacy issues, SOLID and the refactor, a new-crop demo,
  the concurrency fixes, and evidence of maintainability and reliability
- ⬜ Commit, then one member submits the zip on eLearn before the deadline

## Building the submission zip

Use forward-slash paths (PowerShell 5.1 `Compress-Archive` writes backslashes, which break on macOS and Linux):

```bash
C:/Windows/System32/tar.exe -a -c -f SVFRMS-submission.zip src legacy run.jar README.pdf pom.xml   # Windows
zip -r SVFRMS-submission.zip src legacy run.jar README.pdf pom.xml                                # macOS/Linux
```

`legacy/` is included because README §2.3 cites `legacy/LegacyHangDemo.java`. Then unzip a fresh copy and run `java -jar run.jar` from it before uploading.
