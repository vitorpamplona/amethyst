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

# In a sibling clone of quic-interop-runner, merge our entry into
# implementations.json (snippet checked in at quic/interop/quic-interop-runner-snippet.json):
jq -s '.[0] * .[1]' implementations.json \
   ../amethyst/quic/interop/quic-interop-runner-snippet.json \
   > implementations.json.new && mv implementations.json.new implementations.json

python run.py -d -i amethyst -s aioquic -t handshake,chacha20 --log-dir ./logs
```

Inspect `./logs/<run>/client_qlog/*.qlog` in qvis when something breaks.

## Phase ladder (excerpt — full plan in conversation)

| Phase | Goal | Tests | Exit criterion |
|---|---|---|---|
| 0 | Minimum harness | `handshake` | one test reproducible end-to-end ✅ |
| 1 | Triangulate handshake bugs | + `versionnegotiation`, `chacha20` | green vs aioquic + quiche + picoquic |
| 2 | Streams + loss + multiplexing | + `transfer`, `multiplexing`, `*loss`, `http3` | `transfer` / `multiplexing` / `http3` ✅ landed; loss tests pending |
| 3 | Edge cases | `retry`, `resumption`, `zerortt`, `keyupdate`, `rebinding-*`, `blackhole`, `amplificationlimit` | every test green or unsupported-127 with a written reason |
| 4 | CI gate | nightly Phases 1–2; PR-blocking subset on every push | qlogs uploaded as artifacts on red |

## Phase 2 — landed 2026-05-06

- Minimal `Http3GetClient` (in `:quic-interop`, NOT `:quic` — interop-test
  surface, not a production HTTP/3 client). Opens the three required
  client uni streams (control + QPACK encoder + QPACK decoder), sends
  empty SETTINGS, then per request opens a bidi stream, encodes a HEADERS
  frame with the four pseudo-headers using the existing literal-only
  `QpackEncoder`, FINs, and reassembles HEADERS+DATA frames from the
  response. Out-of-scope: GOAWAY, PUSH_PROMISE, dynamic QPACK table,
  trailers, priority.
- `transfer` + `http3` testcases: GET each URL in `REQUESTS` sequentially,
  write each body to `$DOWNLOADS/<basename>`. Status != 200 fails.
- `multiplexing` testcase: same as `transfer` but issues each GET in a
  parallel `coroutineScope { async { … } }` so the request streams
  genuinely overlap on the wire (what tshark verifies).

## Phase 1a — landed 2026-05-06

- `SSLKEYLOGFILE` writer in `InteropClient` (NSS Key Log Format) so Wireshark
  decrypts the sim's pcap captures. Backed by:
  - `TlsClient.clientRandom` (new public read-only property, captured in
    `start()` before sending ClientHello).
  - `QuicConnection.extraSecretsListener` (new optional constructor param,
    chained after the connection's own key-installation listener; no-op
    default, so production callers are unaffected).
- `chacha20` testcase: forces ChaCha20-Poly1305 only via new
  `QuicConnection.cipherSuites` knob (threaded through `TlsClient` →
  `buildQuicClientHello` → `TlsClientHello`).

## Phase 1b — open

- `QLOGDIR`: `:quic` has no qlog observer infrastructure yet. Wiring needs
  hooks at packet send/recv, frame dispatch, recovery, and TLS state
  transitions, plus the qlog JSON-NDJSON schema. Sized as its own design
  doc before implementation.
- `versionnegotiation`: `QuicConnectionWriter` hard-codes `QuicVersion.V1`
  in two call sites; threading a configurable initial-version through and
  wiring the response-handling path is non-trivial. Defer.
- Server role: we are client-first. Reassess after Phase 3.
- WebTransport is **not** part of the standard interop matrix; it needs a
  separate harness against `moq-rs` / chrome-headless.
