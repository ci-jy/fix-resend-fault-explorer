# FIX Session Recovery Checker

Stock QuickFIX/J 2.3.2 silently loses orders in 1,084 of the 11,386 disconnect schedules I ran it through. With a four-line change to `Session.sendResendRequest`, the patched build loses or duplicates 0 messages across all 10,906 of its schedules. The tool behind those numbers runs two real QuickFIX/J sessions in one JVM, cuts the connection at every outbound send (and every pair of sends), and checks what each application actually received against a FIX gap-fill reference model. I wrote it to pin down the lost-ResendRequest bug ([quickfix-j/quickfixj#1114](https://github.com/quickfix-j/quickfixj/issues/1114)) and to show the fix holds across the whole space, not just in one hand-written test.

![Losing schedules and duplicate ResendRequests per build](docs/results.png)

## The bug and the fix

`sendResendRequest` ignores what `sendRaw` returns. If the connection drops just before the write, the request never goes out, but the range is still recorded as requested. After the next Logon the engine sees the gap again, logs "Already sent ResendRequest ... Not sending another", and never asks for those messages. The session stays logged on. The orders are just gone.

I expected the losses to come from ResendRequest cuts only. They don't. A cut at the Logon response loses messages through the same path, because `nextLogon` carries on into `doTargetTooHigh` and sends the ResendRequest on a session that has already disconnected. Of the 1,084 losing schedules, 1,068 end with a cut before a ResendRequest or a Logon response.

```java
if (!sendRaw(resendRequest, 0)) {
    getLog().onErrorEvent("Failed to send ResendRequest ...; resend range not recorded");
    return;
}
```

That is the core of [`patches/quickfixj-1114.patch`](patches/quickfixj-1114.patch), which applies with `patch -p1` to the 2.3.2 sources and adds a JUnit 4 regression test that fails on stock and passes patched. The workaround suggested in the issue, `SendRedundantResendRequests=Y`, also stops the losses, but in the congested-burst runs it sent 35,120 duplicate ResendRequests. The fix sent none.

The full results table and the breakdown by faulted message are in [docs/results.md](docs/results.md).

## Running it

```bash
mvn -B verify
bash scripts/explore.sh --max-app-messages 4 --disconnects 2 --check
```

The first command runs the 35 tests: the #1114 reproducer, sweeps against the stock and patched builds, and real-TCP confirmations. The second runs the full sweep and writes `results/schedules.csv` and `results/summary.md`.

You can also replay a single schedule against stock QuickFIX/J:

```bash
java -cp "harness/target/classes:$(mvn -q -pl harness dependency:build-classpath -Dmdep.outputFile=/dev/stdout)" fixresend.Debug 2 N MEMORY 15:BEFORE
```

That is 2 orders per side, an in-memory store, and the connection cut just before send #15 (the acceptor's ResendRequest). Somewhere in the engine log you get `Already sent ResendRequest FROM: 7 TO: 7. Not sending another.` and the run ends with `LOST@A(I-2 seq 7)`.

Script options, `Debug` arguments, the CSV columns and how to point it at another QuickFIX/J version are in [docs/usage.md](docs/usage.md).

## How the explorer works

Everything runs on a simulated clock installed as QuickFIX/J's `SystemTimeSource`, so heartbeats, TestRequests, logon timeouts and reconnect delays all happen single-threaded and every run is deterministic. That was the hard part, and it is what makes the rest possible. Each schedule is a reproducible test case.

The script itself is a normal day for a session: Logon, orders both ways, a stall that triggers a TestRequest, a Logout, orders sent while logged out, then re-Logon with gap recovery. A fault shim sits in place of the `Responder`, numbers every outbound send from both sides, and cuts the connection at the scheduled ones. A BEFORE cut processes the drop first so `send` returns false (the #1114 race). An AFTER cut delivers the frame and then drops.

The explorer tries every single cut, then every second cut after it. Since runs are deterministic, a second cut is only tried at sends that actually happen in the first cut's run, which keeps the space finite. After each run settles, the reference model checks that every order arrived exactly once, in order, that gap fills only skipped admin messages, and that both sides ended up in sync.

To check the in-JVM transport isn't hiding anything, 60 sampled schedules are replayed over loopback TCP through a small NIO proxy that understands FIX framing and can cut before, after or in the middle of a frame. They give the same outcome as the in-JVM runs.

## What it doesn't cover yet

- 12 patched schedules lose nothing but never resynchronise. The smallest is `14:BEFORE 15:AFTER`: a stale Logon gets re-enqueued after the disconnect and processed on the new connection, so the acceptor waits for a gap that was already filled. It looks like the same family as #1243. A guard in `nextLogon` for a session without a responder is what I would try next.
- The fix only covers a write that fails synchronously. A write MINA accepts and then loses in the socket buffer is left to the next Logon's sequence check.
- File-store runs reuse one session object across reconnects, so they don't test a process restart that reloads the store. A "restart" fault would be the natural next step.
- Only FIX.4.4, one session pair and at most two disconnects per run. The TCP mode uses the harness's own sockets, not QuickFIX/J's `SocketInitiator`/`SocketAcceptor`.

Project period: 2026-08-08 to 2026-09-09.
