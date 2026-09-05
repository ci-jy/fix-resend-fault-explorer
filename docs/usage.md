# Usage

## Sweep script

```
scripts/explore.sh [--max-app-messages N] [--disconnects 1|2] [--stores MEMORY,FILE] [--check]
```

- `--max-app-messages N`: sweep 1..N orders per side (default 4). A 5-order burst run is always added.
- `--disconnects 1|2`: single cuts only, or single and double (default 2).
- `--stores`: comma-separated message stores (default `MEMORY,FILE`).
- `--check`: also verify that stock reproduces #1114 with a single cut, that the patched build loses or duplicates nothing, and that the workaround sends more duplicate ResendRequests than the patch.

The script builds the project if needed, runs the stock, workaround and patched builds in turn, and writes `results/schedules.csv` and `results/summary.md`.

## Replaying one schedule

```
fixresend.Debug <orders> <Y|N> <MEMORY|FILE> [burst=N] <send#:BEFORE|AFTER ...>
```

`Y|N` turns `SendRedundantResendRequests` on or off. Send numbers count outbound sends across both sessions. It prints the engine log, every outbound send, the faults, what each side received, the final session state and the violations.

It runs against whichever `quickfix.Session` is first on the classpath. Put `run-patched/target/classes` in front to replay against the patched build.

## schedules.csv columns

`build, store, redundant_resends, app_messages, burst, disconnects, schedule, faulted_sends, total_sends, lost, duplicated, out_of_order, app_gap_filled, not_recovered, resend_requests, duplicate_resend_requests, violations`

## Other QuickFIX/J versions and settings

- Version: set `quickfixj.version` in the root `pom.xml`. For a patched build, replace `run-patched/src/main/java/quickfix/Session.java` with your version's `Session.java` plus the fix. `scripts/make-patch.sh` regenerates the patch.
- Settings: session settings (HeartBtInt, ReconnectInterval, LogonTimeout, `SendRedundantResendRequests`, store type) are set in `Scenario.createSessions()`. To sweep another one, such as `ResetOnLogon`, add a field to `Config` and set it there.
