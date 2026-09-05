# Results

From `bash scripts/explore.sh --max-app-messages 4 --disconnects 2 --check`: QuickFIX/J 2.3.2, FIX.4.4, memory and file stores, 1 to 4 orders per side plus a 5-order congested burst. The same numbers are in [results/summary.md](../results/summary.md), and every schedule is in `results/schedules.csv`.

| Build | Schedules | Losing messages | Duplicating | Not recovered | Lost messages | ResendRequests sent | Duplicate ResendRequests |
|---|---:|---:|---:|---:|---:|---:|---:|
| stock 2.3.2 | 11,386 | 1,084 | 0 | 1,170 | 1,808 | 42,100 | 0 |
| stock + SendRedundantResendRequests=Y | 16,594 | 0 | 0 | 0 | 0 | 98,554 | 35,120 |
| patched (#1114 fix) | 10,906 | 0 | 0 | 12 | 0 | 42,578 | 0 |

Schedule counts differ by build because each build sends a different number of messages, so it has a different number of disconnect points.

In stock, 1,068 of the 1,084 losing schedules end with a BEFORE cut at a ResendRequest or a Logon response. `results/summary.md` lists the most common combinations of faulted messages.

## Shortest reproducers

- `15:BEFORE`, 2 orders per side: single cut at the acceptor's ResendRequest. Loses order `I-2`.
- `14:BEFORE`, 2 orders per side: single cut at the acceptor's Logon response. Loses order `I-2`.
- `14:BEFORE 15:AFTER` on the patched build: no loss, but the acceptor ends up `NOT_RECOVERED`.

## Violation kinds

- `LOST`: an order the other side sent was never delivered to the application.
- `DUPLICATED`: an order was delivered more than once.
- `OUT_OF_ORDER`: orders were delivered out of MsgSeqNum order.
- `APP_GAP_FILLED`: a SequenceReset-GapFill skipped an application message.
- `NOT_RECOVERED`: after settling, the sessions are not both logged on and expecting each other's next sequence number.
