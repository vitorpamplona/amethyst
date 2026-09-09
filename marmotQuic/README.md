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
- A **direct sender** opens a client-initiated *unidirectional* stream and
  writes record frames with **no** control envelope — the dialed endpoint is
  already the one receiver, so there is no room to name.

A broker rejects the wrong pairing. Every role frames the same way:
`uint32 frame_len || bytes` — on the broker path the control envelope first and
then each `AgentTextStreamRecordV1`, on the direct path records from the first
byte.

Note the direct path's connection direction: the **receiver** listens and the
**sender** dials, inverted from the broker path where both ends dial the
broker. Only the sender half is here; `:quic` is a client stack with no server
role, so this module cannot expose a direct-path endpoint of its own.

The codecs — control envelope, frame reader/writer with both caps, `quic://`
candidate parsing — live in `quartz` next to the rest of agent-text-stream,
because they are pure bytes and belong with the feature. This module is only
the connection.

## The broker sees nothing

Records are encrypted under a key derived from the group's MLS exporter. A
broker holds no key and learns only the routing pair
`(stream_id, start_event_id)` plus ciphertext. It is an untrusted forwarder,
and a candidate that points somewhere hostile still cannot forge a record.

## TLS trust

Preview endpoints and brokers are commonly self-signed, and the binding says so:
a client MAY pin the endpoint certificate by exact DER or SHA-256 fingerprint
instead of chaining to a CA. `PinnedCertificateValidator` (in `:quic`) is that
pin. It replaces the chain and the hostname check and nothing else — the peer
still has to sign the TLS transcript with the pinned certificate's private key,
so copying a public certificate off the wire buys an attacker nothing.

`amy marmot stream send|watch` takes `--pin-sha256 HEX[,HEX…]`; the reference
broker prints its own `server_cert_sha256_fingerprint` in its startup JSON.

## Interop tests

`MarmotQuicBrokerInteropTest` drives our publisher and subscriber through MDK's
own reference broker, and `MarmotQuicDirectInteropTest` drives our direct
sender against MDK's direct receiver (`wn stream receive`). Both are the only
way to know the binding is right: an ALPN string, a stream direction, a missing
control envelope and a frame prefix are all things an implementation will
happily agree with itself about.

Start the broker from an MDK checkout:

```bash
cargo build --release --bin marmot-quic-broker --bin wn
./target/release/marmot-quic-broker --bind 127.0.0.1:4450 --json
```

then:

```bash
./gradlew :marmotQuic:jvmTest \
  -DmarmotQuicBroker=127.0.0.1:4450 \
  -DmarmotQuicBrokerPin=<server_cert_sha256_fingerprint from that JSON> \
  -DmarmotWn=/path/to/mdk/target/release/wn
```

Each property gates its own cases and they skip visibly without it, so an
ordinary `./gradlew test` never needs the reference implementation on the
machine. `-DmarmotWn` needs no running process: the test spawns
`wn stream receive` itself on a free port.

## Using it

`amy marmot stream` drives the whole feature; the harness's tests 18 and 19
run it in both directions against MDK.

```bash
amy marmot stream start GID --broker quic://127.0.0.1:4450
amy marmot stream send  GID --stream-id … --start-event-id … --broker … "hello"
amy marmot stream send  GID --stream-id … --start-event-id … --direct quic://host:port "hello"
amy marmot stream watch GID --stream-id …
amy marmot stream finish GID --stream-id … --transcript-hash … --chunk-count N "hello"
```

## Advertised, but not started

The implementation is complete and tested, and nothing in the app starts it.

Those are two separate things, and conflating them broke interop once. Our
KeyPackage **does** advertise component `0x8006` and the `0xF2D1` receive role,
because the reference client installs `agent-text-stream.quic.v1` with
`required_member_roles = receive` into the required set of **every group it
creates**, and refuses an invitee whose leaf omits either. Dropping the
advertisement on the grounds that "nothing publishes previews" made an Amethyst
user un-addable to any group a White Noise user started — the traffic claim was
right, the capability claim was not. A capability says "this client can handle
it", never "this group uses it".

What we do NOT advertise is `send` (`0xF2D2`) and `fanout` (`0xF2D4`): we can be
shown a preview, we do not originate one. A group that requires either refuses
us, and `CurrentProfileWelcomeTest` asserts that refusal so widening the default
stays a deliberate decision.

What is not started: the Android chat screen builds no watcher and dials no
broker a kind:1200 advertises. The codecs, this module, the CLI
(`amy marmot stream …`) and the interop tests all still work and still run.
Turning the live path on is re-adding the watcher to `MarmotGroupChatView`.

## Not done

- The Android GUI renders previews but does not originate a stream — that is
  an agent's job, and no agent runs in the app yet. Only `amy` publishes one.
- The desktop app has no Marmot chat screen at all, so there is nothing to
  render a preview into. The watcher it would use already lives in `commons`.
- The direct path's **receiving** half. `:quic` has no server role, so this
  module can dial a direct receiver but cannot be one. v1 also defines no
  start-payload candidate format for the direct path, so a sender only reaches
  a receiver whose endpoint it already knows out of band.
