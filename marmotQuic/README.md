# marmotQuic

Marmot's raw QUIC transport binding for agent text stream previews
(`transports/quic.md`), on top of the repo's own pure-Kotlin `:quic` stack.

## Why this is not `nestsClient`'s WebTransport

Both features move bytes over `:quic`, but they enter it at different layers.

`nestsClient` speaks **WebTransport**: HTTP/3, an Extended CONNECT handshake, a
`:protocol` pseudo-header, QPACK, SETTINGS negotiation. Its
`WebTransportSession` abstraction starts *above* all of that.

Marmot's binding is **raw QUIC**. It negotiates its own ALPN —
`marmot.quic_broker.v1` for the broker path, `marmot.quic_stream.v1` for the
direct one — and writes frames straight onto QUIC streams. There is no HTTP/3
in it at all, so `WebTransportSession` is the wrong shape.

What both share is everything below that line, which is the hard part and is
already built: the QUIC connection, TLS 1.3, ALPN negotiation, stream
multiplexing, loss recovery and the UDP socket.

## Shape

- A **publisher** opens a client-initiated *unidirectional* stream, writes a
  `publish` control envelope, then record frames.
- A **subscriber** opens a client-initiated *bidirectional* stream, writes a
  `subscribe` control envelope, and reads the fan-out on the return direction.

A broker rejects the wrong pairing. Both roles frame everything the same way:
`uint32 frame_len || bytes`, the control envelope first and then each
`AgentTextStreamRecordV1`.

The codecs — control envelope, frame reader/writer with both caps, `quic://`
candidate parsing — live in `quartz` next to the rest of agent-text-stream,
because they are pure bytes and belong with the feature. This module is only
the connection.

## The broker sees nothing

Records are encrypted under a key derived from the group's MLS exporter. A
broker holds no key and learns only the routing pair
`(stream_id, start_event_id)` plus ciphertext. It is an untrusted forwarder,
and a candidate that points somewhere hostile still cannot forge a record.

## Interop tests

`MarmotQuicBrokerInteropTest` drives our publisher and subscriber through
MDK's own reference broker. Start it from an MDK checkout:

```bash
cargo build --release --bin marmot-quic-broker
./target/release/marmot-quic-broker --bind 127.0.0.1:4450 --json
```

then:

```bash
./gradlew :marmotQuic:jvmTest -DmarmotQuicBroker=127.0.0.1:4450
```

Without the property the cases skip visibly, so an ordinary `./gradlew test`
never needs a broker on the machine.

## Using it

`amy marmot stream` drives the whole feature; the harness's tests 18 and 19
run it in both directions against MDK.

```bash
amy marmot stream start GID --broker quic://127.0.0.1:4450
amy marmot stream send  GID --stream-id … --start-event-id … --broker … "hello"
amy marmot stream watch GID --stream-id …
amy marmot stream finish GID --stream-id … --transcript-hash … --chunk-count N "hello"
```

## Not done

- The Android GUI renders previews but does not originate a stream — that is
  an agent's job, and no agent runs in the app yet. Only `amy` publishes one.
- The desktop app has no Marmot chat screen at all, so there is nothing to
  render a preview into. The watcher it would use already lives in `commons`.
- The direct path (`marmot.quic_stream.v1`) is unimplemented. v1 defines no
  start-payload candidate format for it, so it is only reachable with an
  endpoint known out of band.
