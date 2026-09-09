# Marmot conformance fixtures (copied from MDK)

These files are **copied verbatim** from the reference implementation. They are
not ours to edit: their whole value is that another implementation wrote them,
so a local "fix" to make a test pass would delete the only thing they prove.

| File | Upstream path |
|---|---|
| `imeta-v1.json`, `imeta-v2.json` | `fixtures/encrypted-media/` |
| `nostr-routing-v1-*.v1.json` | `crates/cgka-conformance-simulator/vectors/byte-fixtures/` |

Upstream is the MDK checkout the interop harness already vendors at
`cli/tests/marmot/state/mdk`. To refresh:

```bash
MDK=cli/tests/marmot/state/mdk
cp $MDK/fixtures/encrypted-media/imeta-v{1,2}.json \
   quartz/src/commonTest/resources/marmot/conformance/
cp $MDK/crates/cgka-conformance-simulator/vectors/byte-fixtures/nostr-routing-v1-*.v1.json \
   quartz/src/commonTest/resources/marmot/conformance/
```

A refresh that makes a test fail is a signal, not a chore: either the wire
format moved and we have not, or upstream tightened a rule we were lenient
about.

## What is NOT here yet

`crates/cgka-conformance-simulator/vectors/manifest.v1.json` lists 41 artifacts,
31 marked `"status": "portable"` — built for exactly this purpose. The 19
scenario vectors (`three-client-message-exchange`, `publish-fail`,
`convergence-*-selected`, `restart-delivery-faults`, `late-welcome-backfill`, …)
need a runner that can drive our client through a scripted trace and project
its state per `marmot/foundation/conformance.md` ("Canonical snapshot"). That
is a separate piece of work, and it is where the convergence and crash/restart
coverage lives.

`tests/agent_text_stream_vectors.rs` pins the key context encoding, the HKDF
record-key derivation, the record AAD, the transcript hash and the broker
control envelope as literal bytes. It is Rust source rather than a data file,
so adopting it means transcribing the expected values.
