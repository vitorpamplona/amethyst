# quic plans

_Audited 2026-06-30. 4 plans: 3 shipped (archived), 0 in-progress, 0 queued, 1 abandoned._

## Abandoned
| Plan | Summary |
| ---- | ------- |
| [2026-05-05-congestion-control.md](2026-05-05-congestion-control.md) | NewReno congestion control parked indefinitely — no `CongestionController` in code; the real concern was solved by the smaller `SendBuffer.bestEffort` fix instead. |

## Archived (shipped)
| Plan | Summary |
| ---- | ------- |
| [archive/2026-04-26-quic-stack-status.md](archive/2026-04-26-quic-stack-status.md) | Living status doc for the shipped pure-Kotlin QUIC v1 + HTTP/3 + WebTransport stack; records all resolved RFC-audit gaps. |
| [archive/2026-05-04-control-frame-retransmit.md](archive/2026-05-04-control-frame-retransmit.md) | RFC 9002 per-frame retransmit (RecoveryToken/SentPacket/loss-detection/PTO) plus STREAM/CRYPTO/RESET_STREAM follow-ups and SendBuffer retain-until-ACK. |
| [archive/2026-05-08-lock-split-design.md](archive/2026-05-08-lock-split-design.md) | Phase-1 split of the single `QuicConnection.lock` into `streamsLock`/`lifecycleLock`/per-level locks; phase 2 writer split deferred. |
