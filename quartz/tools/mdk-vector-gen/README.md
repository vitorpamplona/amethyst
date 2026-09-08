# mdk-vector-gen

Rust helpers that emit MLS and Marmot interop test vectors from the same
OpenMLS backend MDK builds against.

**The dependency pin is the point of this tool.** `Cargo.toml` tracks
`erskingardner/openmls` at the exact rev MDK's root `Cargo.toml` names, built
with the `extensions-draft` feature. Stock crates.io `openmls` does not carry
`app_data_dictionary` / `app_components` / `app_data_update`, and the current
Marmot profile is defined entirely in those terms — so vectors generated from
the published crate cannot exercise it. When MDK bumps its OpenMLS rev, bump it
here in the same change.

## Binaries

### `marmot-profile-gen` → `marmot-current-profile.json`

The current-profile Marmot vector: a group built the way MDK's `cgka-engine`
builds one.

- `RequiredCapabilities` = extension `0x0006` (`app_data_dictionary`) +
  proposal `0x0008` (`app_data_update`).
- GroupContext `app_data_dictionary` carrying the required-component list
  (`0x0001`) plus `marmot.group.profile.v1` (`0x8001`),
  `marmot.group.admin-policy.v1` (`0x8003`),
  `marmot.transport.nostr.routing.v1` (`0x8004`) and
  `marmot.group.lifecycle.v1` (`0x800c`). Component `0x8009` is *required* but
  leaf-only, so it deliberately has no GroupContext data.
- Every member LeafNode dictionary carrying the supported-component list, an
  empty `safe_aad` list, and the 104-byte
  `marmot.member.account-identity-proof.v2` component.
- The KeyPackage-level dictionary carrying the empty-data
  `last_resort_key_package` component (`0x0004`) — last resort is not an MLS
  extension type in this profile.
- Handshake messages as `PublicMessage`, matching Marmot's pinned wire format;
  the Add commit is emitted so the peeler has a real one to authenticate.
- Exporter KATs for both `MLS-Exporter("marmot", "group-event", 32)` and the
  conformance commitment from `foundation/conformance.md`.

The identity-proof encoder is hand-rolled from the spec text rather than pulled
from MDK, and `assert_spec_proof_vector()` checks it against the fixed vector
published in `app-components/account-identity-proof-v2.md` before anything else
runs. If the generator starts up at all, the canonical kind-450 event
serialization, its id, the BIP-340 signature and the 104-byte component layout
all match the spec byte-for-byte.

```
cd quartz/tools/mdk-vector-gen
cargo run --release --bin marmot-profile-gen \
  > ../../src/commonTest/resources/mls/marmot-current-profile.json
```

### `mdk-vector-gen` → `mdk-welcome.json`

The MLS-core vector, unchanged in intent: it proves Amethyst can parse and
decrypt a Welcome plus application messages authored by the Rust side. It
builds a plain OpenMLS group with no Marmot profile state, which is exactly
what makes it a clean test of the key schedule alone.

- `joiner.init_priv` / `encryption_priv` / `signature_priv` / `signature_pub` —
  the private key material the joiner needs to drive `MlsGroup.processWelcome`.
- `joiner.key_package` (MlsMessage-wrapped) and `key_package_raw`.
- `welcome` (MlsMessage-wrapped) — Alice's Welcome for Bob.
- `committer.signer_pub` — Alice's Ed25519 signature public key.
- `exporter.{label,context,length,secret}` — `MLS-Exporter("marmot",
  "group-event", 32)` derived from Bob's post-join state. A match here means
  Amethyst's whole post-join key schedule agrees with OpenMLS byte-for-byte.
- `app_messages_alice_to_bob[]` — Alice-sent PrivateMessage bytes plus the
  expected plaintext.

```
cargo run --release --bin mdk-vector-gen \
  > ../../src/commonTest/resources/mls/mdk-welcome.json
```

### `emit-joiner-kp`, `verify-amethyst`

Debug helpers for driving one side of a join by hand.

## Regenerating

Both generators use fresh randomness each run, so the committed vectors change
on regeneration — that is fine, because the Kotlin tests assert round-trip
correctness against whatever is in the JSON rather than against fixed bytes.
Commit the regenerated file if you change a generator.
