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
- `Makefile` wrappers: `make build`, `make clean`. (A `make smoke`
  target previously stood up picoquic + our endpoint on a private Docker
  bridge to bisect runner failures from impl failures; dropped once
  the runner reliably exercised both paths.)

## Local iteration loop

The fast path: `quic/interop/run-matrix.sh` clones the runner alongside
this repo, sets up a venv, merges our `implementations_quic.json` snippet,
builds the endpoint image, and invokes `run.py`. All steps are
idempotent so repeated invocations just iterate.

```
# Single test against the most permissive peer:
quic/interop/run-matrix.sh -s aioquic -t handshake

# A focused triangulation:
quic/interop/run-matrix.sh -s aioquic   -t handshake,chacha20
quic/interop/run-matrix.sh -s quic-go   -t handshake,chacha20
quic/interop/run-matrix.sh -s picoquic  -t handshake,chacha20

# Tight inner loop — skip the image rebuild between test selections:
SKIP_BUILD=1 quic/interop/run-matrix.sh -s aioquic -t transfer
```

Manual flow (if `run-matrix.sh` doesn't fit):

```
make -C quic/interop build
# Then in a sibling clone of quic-interop-runner, merge our snippet:
jq -s '.[0] * .[1]' implementations_quic.json \
   ../amethyst/quic/interop/quic-interop-runner-snippet.json \
   > implementations_quic.json.new && mv implementations_quic.json.new implementations_quic.json
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
- Aliased sim-driven testcases — these reuse the same client code paths;
  the runner injects the network condition via the ns-3 sim. Failures
  here are exactly the bug-finding signal we want, since they exercise
  loss recovery / RTT estimator / congestion behaviour against real peers:
  - `transferloss` → transfer (random packet loss)
  - `transfercorruption` → transfer (random bit-flip; AEAD AUTH FAIL → drop + retransmit)
  - `longrtt` → transfer (emulated high-latency link)
  - `goodput` → transfer (throughput floor)
  - `crosstraffic` → transfer (competing UDP flows on the same link)
  - `handshakeloss` → handshake (loss during handshake — tests CRYPTO retransmit)

## Phase 3 — landed 2026-05-07 (post-quic-go interop)

- ALPN per testcase (`:quic-interop`'s `Alpn` enum + per-test switch in
  `InteropClient.main`). quic-go enforces strictly with TLS
  `no_application_protocol` (CRYPTO_ERROR 0x178); aioquic / picoquic accept
  either. Convention: `h3` for `http3`/`multiplexing`, `hq-interop`
  everywhere else.
- `HqInteropGetClient` (HTTP/0.9 over QUIC) — open bidi, send `GET /path\r\n`,
  FIN, read body until server FINs. No framing, no QPACK. ~30 lines.
- Multi-stream FIN delivery fix in `QuicConnection.closeAllSignals()`: pre-fix
  iterated only the connection-wide signal channels, leaving every per-stream
  `incomingChannel` open after teardown — coroutines suspended on
  `stream.incoming.collect { ... }` hung forever. Fix iterates `streamsList`
  on close. Three regression tests in `MultiStreamFinDeliveryTest`.
- `retry` + `ipv6` testcases enabled in dispatch. `retry` rides on agent 3's
  RFC 9000 §17.2.5 + RFC 9001 §5.8 implementation. `ipv6` should "just work"
  via JDK's `DatagramChannel` v6 support; runner-validated when run.

## Validated against (as of 2026-05-07 evening)

Latest run results before pushing the multi-ALPN fix `acfe815e1`:

| Peer | handshake | chacha20 | transfer | http3 | multiplexing |
|---|---|---|---|---|---|
| aioquic   | ✓ | ✓ | ✓ | ✓ | ✕ (channel-saturation, agent C investigating) |
| picoquic  | ✕ (alpn=hq-interop unsupported, fixed in `acfe815e1`) | ✕ | ✕ | ✓ | ✕ |
| quic-go   | (untested post-fix) | | | | |

After `acfe815e1`'s multi-ALPN offer, predictions:
- picoquic returns to 4/4 (server picks `h3`, we run Http3GetClient).
- quic-go: handshake / chacha20 / transfer / http3 should green (server picks `hq-interop` for non-h3 tests).
- aioquic: still 4/5; multiplexing held back by channel-saturation bug.

## Phase 4 — landed 2026-05-07 (overnight agents)

All three agents merged onto the branch with three rounds of fixup
(each agent's worktree was based on `main`, not the branch HEAD, so
they clobbered each other's changes during merge):

- **Agent A — VN-handling defense in `:quic`**: configurable
  `initialVersion` on `QuicConnection`, `applyVersionNegotiation`
  state machine, `vnConsumed` latch, downgrade defenses. *NOTE*: the
  runner does not have a `versionnegotiation` testcase (it has `v2`,
  which tests QUIC v2 — we're v1-only). Code stays as defensive
  support for any server that throws a VN at us, but unused by the
  matrix.
- **Agent B — qlog observer**: `QlogObserver` interface in `:quic`
  with NoOp default + 12 event hooks (packet-sent/received/dropped,
  key-updated, conn-started/closed, loss-detected, PTO-fired,
  transport-params, ALPN, version). `QlogWriter` (Jackson-backed
  JSON-NDJSON) in `:quic-interop`. `InteropClient` reads `$QLOGDIR`
  the runner already sets and writes `client.sqlog` per testcase.
  Drag straight into qvis.quictools.info to see the trace.
- **Agent C — peer-uni-stream drainer**: `drainPeerInitiatedUniStreamsIntoBlackHole`
  helper on `QuicConnection`, wired into `Http3GetClient.init(scope)`.
  Fixes the multiplexing channel-saturation symptom (server's uni
  streams accumulated bytes in 64-chunk per-stream channels until
  parser tore down with INTERNAL_ERROR ~4.5s into a multi-stream run).

## Open issues for tomorrow

- **`retry` test fails** against both aioquic and picoquic. `applyRetry`
  + token threading + ClientHello caching all look correct on inspection.
  Need qlog output (now available) to diagnose. Run `retry` against
  picoquic, drag `client.sqlog` into qvis, look for: `packet_received`
  with type=retry, `packet_sent` with non-empty token, server's
  reaction.
- **`v2` testcase** — server demands QUIC v2; we're v1-only, so this
  correctly fails. Real fix is implementing v2 (RFC 9369) which is
  out of scope for now.
- **Run `retry` + `multiplexing` against quic-go**, picoquic, aioquic
  with the new fixes. Predictions:
  - aioquic / picoquic: multiplexing should now green; retry
    diagnostic surfaces as qlog.
  - quic-go: previously 0/7 (probably tested mid-flight); should
    now be much closer to picoquic/aioquic results.

## Concurrency

`run-matrix.sh` is NOT safe to run in parallel. The runner's
docker-compose.yml hardcodes `container_name: sim/server/client` —
Docker enforces those globally. Use a sequential loop:

```
for peer in aioquic picoquic quic-go; do
    quic/interop/run-matrix.sh -s $peer -t handshake,chacha20,...
done
```

## Explicitly unsupported testcases (return 127, runner skips)

| Testcase | Reason |
|---|---|
| `versionnegotiation` | `QuicConnectionWriter` hard-codes `QuicVersion.V1`; needs a configurable initial version + VN-response retry path. **In progress (background agent)**. |
| `resumption` | session ticket parsing + persistence not yet implemented |
| `zerortt` | depends on `resumption` + early-data path |
| `keyupdate` | KEY_PHASE bit handling not yet implemented in 1-RTT |
| `rebinding-port`, `rebinding-addr` | client-side connection migration (re-bind UDP socket, NEW_CONNECTION_ID rotation) not implemented |
| `amplificationlimit` | server-side test, N/A for client role |
| `blackhole` | inverse test (verifies we *fail* on dead network in bounded time); needs special handling |
| `ipv6` | `UdpSocket` IPv6 path not exercised; risky to claim without testing |

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
