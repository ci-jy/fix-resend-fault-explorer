# Disconnect-point exploration summary

QuickFIX/J 2.3.2, FIX.4.4, every single and double disconnect point (cut before or after each outbound send, both sides), message stores: [MEMORY, FILE], up to 4 application messages per side plus a 5-message congested burst.

| Build | Schedules explored | Losing messages | Duplicating messages | Not recovered | Lost messages (total) | ResendRequests sent | Duplicate ResendRequests | Duplicate ResendRequests (burst runs) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| stock 2.3.2 | 11386 | 1084 | 0 | 1170 | 1808 | 42100 | 0 | 0 / 3802 runs |
| stock + SendRedundantResendRequests=Y | 16594 | 0 | 0 | 0 | 0 | 98554 | 35120 | 35120 / 9382 runs |
| patched (#1114 fix) | 10906 | 0 | 0 | 12 | 0 | 42578 | 0 | 0 / 3694 runs |

A schedule is a list of `send#:MODE` disconnect points; send numbers count outbound sends across both sessions. BEFORE = the drop is processed before the write (Responder.send returns false), AFTER = the write is delivered, then the connection drops.

## Minimal reproducing schedules

Shortest schedule (fewest disconnects, then fewest application messages) per build and violation kind.

| Build | Violation | Store | m | Schedule | Faulted sends | Violations |
|---|---|---|---:|---|---|---|
| stock 2.3.2 | LOST | FILE | 2 | `14:BEFORE` | #14 A Logon(8) BEFORE | LOST@A(I-2 seq 7); NOT_RECOVERED@A(loggedOn=true expects 7  peer next 18) |
| stock 2.3.2 | NOT_RECOVERED | FILE | 2 | `14:BEFORE` | #14 A Logon(8) BEFORE | LOST@A(I-2 seq 7); NOT_RECOVERED@A(loggedOn=true expects 7  peer next 18) |
| patched (#1114 fix) | NOT_RECOVERED | FILE | 2 | `14:BEFORE 15:AFTER` | #14 A Logon(8) BEFORE; #15 I Logon(9) AFTER | NOT_RECOVERED@A(loggedOn=true expects 9  peer next 19) |

## Losing schedules by faulted message (stock)

| Faulted message(s) | Losing schedules |
|---|---:|
| A NewOrderSingle BEFORE; A Logon BEFORE | 42 |
| A NewOrderSingle BEFORE; A ResendRequest BEFORE | 42 |
| A NewOrderSingle AFTER; A Logon BEFORE | 32 |
| A NewOrderSingle AFTER; A ResendRequest BEFORE | 32 |
| A NewOrderSingle BEFORE; I ResendRequest BEFORE | 32 |
| A NewOrderSingle AFTER; I ResendRequest BEFORE | 24 |
| I Heartbeat AFTER; A Logon BEFORE | 24 |
| I Heartbeat AFTER; A ResendRequest BEFORE | 24 |
| I Heartbeat AFTER; I ResendRequest BEFORE | 24 |
| I Heartbeat BEFORE; A Logon BEFORE | 24 |
| I Heartbeat BEFORE; A ResendRequest BEFORE | 24 |
| I Heartbeat BEFORE; I ResendRequest BEFORE | 24 |
| I NewOrderSingle AFTER; I ResendRequest BEFORE | 22 |
| I NewOrderSingle BEFORE; A Logon BEFORE | 22 |
| I NewOrderSingle BEFORE; A ResendRequest BEFORE | 22 |

## Checks

- Stock build reproduces #1114 with a single disconnect at a ResendRequest: yes
- Patched build loses or duplicates no application message: yes
- Workaround sends more duplicate ResendRequests than the patch: yes
