# `:quic` interop harness

Scripts and test entry points for driving the pure-Kotlin QUIC client against
real reference servers.

## Quickstart — picoquic

```bash
# Terminal 1: run the picoquic reference server in Docker.
quic/scripts/run-picoquic.sh -d

# Terminal 2: drive our client at it.
./gradlew :quic:jvmTestClasses
java -cp "$(./gradlew -q :quic:printTestRuntimeClasspath)" \
  com.vitorpamplona.quic.interop.InteropRunnerKt 127.0.0.1 4433
```

Expected output:

```
== :quic interop runner ==
target:  127.0.0.1:4433
timeout: 10s

✓ HANDSHAKE COMPLETE
  status:               CONNECTED
  negotiated ALPN:      h3
  peer transport params: max_data=…, max_streams_bidi=…, …
```

## What this proves

- TCP-equivalent UDP connection setup
- QUIC v1 Initial / Handshake / 1-RTT packet flow with RFC-correct PADDING
- TLS 1.3 over QUIC with the SHA-256 cipher suites
- ALPN `h3` negotiation
- QUIC transport parameters round-trip
- Header protection (AES-ECB)
- AEAD payload protection (AES-128-GCM)

It does NOT yet prove WebTransport / MoQ — picoquic doesn't speak WT.

## Live nests interop

For a full WebTransport + MoQ test against the actual Nostr nests server, the
target is `nostrnests.com:443`:

```bash
java -cp "..." com.vitorpamplona.quic.interop.InteropRunnerKt nostrnests.com 443
```

This goes against the real CA-signed cert, so revert to the default
`JdkCertificateValidator` (the InteropRunner currently uses
`PermissiveCertificateValidator` for self-signed dev servers — change the
constant before pointing at production).

## Other reference servers worth trying

| Server | Image | Notes |
|---|---|---|
| picoquic | `privateoctopus/picoquic` | Most permissive; clear qlog traces |
| quic-go | `martenseemann/quic-go-interop` | Stable, widest scenario coverage |
| aioquic | `aiortc/aioquic` | Easy to debug, Python reference |
| quiche | `cloudflare/quiche` | Production-grade, strict |
| nests-rs | (local cargo build) | The actual MoQ relay; needs WebTransport |

The IETF's [`quic-interop-runner`](https://github.com/quic-interop/quic-interop-runner)
exposes all of these via a single Docker matrix. Wrapping our client in its
container contract (`TESTCASE` env, `REQUESTS=` URL list, `/certs` mount) would
let us join the public matrix at https://interop.seemann.io.
