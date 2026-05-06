# quic-interop-runner endpoint — Phase 0 scaffolding

Date: 2026-05-06

## Why

We want the [`quic-interop-runner`](https://github.com/quic-interop/quic-interop-runner)
matrix as a **bug-finding harness**, not a vanity scoreboard. Each peer impl
(quiche, aioquic, picoquic, ngtcp2, msquic, mvfst, lsquic, neqo, kwik) enforces
different parts of RFC 9000/9001/9002/9114 strictly, so failures triangulate
to specific bugs in `:quic`. The runner's ns-3 sim also exposes loss /
reorder / migration scenarios that are awkward to reproduce in unit tests.

## What's in Phase 0

- `:quic-interop` Gradle module (JVM-only application, registered at
  `quic/interop/` via `settings.gradle`).
- `InteropClient.kt` reads the runner's env-var contract (`ROLE`, `TESTCASE`,
  `REQUESTS`, …) and dispatches by testcase. Phase 0 implements only
  `handshake`; everything else returns `127` (runner-skip).
- `Dockerfile` based on `martenseemann/quic-network-simulator-endpoint` +
  OpenJDK 21 runtime, copies the `installDist` output.
- `run_endpoint.sh` sources the base image's `/setup.sh` then execs our JVM
  binary.
- `Makefile` wrappers: `make build`, `make smoke`, `make clean`.

## Local iteration loop

```
# In our repo:
make -C quic/interop build

# In a sibling clone of quic-interop-runner, add to implementations.json:
"amethyst": {
    "image": "amethyst-quic-interop:latest",
    "url":   "https://github.com/vitorpamplona/amethyst",
    "role":  "client"
}

python run.py -d -i amethyst -s aioquic -t handshake --log-dir ./logs
```

Inspect `./logs/<run>/client_qlog/*.qlog` in qvis when something breaks.

## Phase ladder (excerpt — full plan in conversation)

| Phase | Goal | Tests | Exit criterion |
|---|---|---|---|
| 0 | Minimum harness | `handshake` | one test reproducible end-to-end ✅ |
| 1 | Triangulate handshake bugs | + `versionnegotiation`, `chacha20` | green vs aioquic + quiche + picoquic |
| 2 | Streams + loss + multiplexing | + `transfer`, `multiplexing`, `*loss`, `http3` | green vs aioquic + quiche; soak 500/500 |
| 3 | Edge cases | `retry`, `resumption`, `zerortt`, `keyupdate`, `rebinding-*`, `blackhole`, `amplificationlimit` | every test green or unsupported-127 with a written reason |
| 4 | CI gate | nightly Phases 1–2; PR-blocking subset on every push | qlogs uploaded as artifacts on red |

## Known follow-ups (NOT in Phase 0)

- `SSLKEYLOGFILE` and `QLOGDIR` are not yet wired through to `:quic` — the
  runner will set them but our endpoint ignores them. Phase 1 must surface
  TLS keys (so Wireshark can decrypt the sim's pcap captures) and qlog
  output (so qvis is useful).
- Server role: we are client-first. Reassess after Phase 3.
- WebTransport is **not** part of the standard interop matrix; it needs a
  separate harness against `moq-rs` / chrome-headless.
