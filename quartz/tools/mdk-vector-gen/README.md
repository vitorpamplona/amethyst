# mdk-vector-gen

Rust helper that emits MLS interop test vectors from the same openmls 0.8
backend MDK/whitenoise uses. Produces `quartz/src/commonTest/resources/mls/mdk-welcome.json`,
which [`MdkWelcomeInteropTest`] consumes to prove Amethyst can parse and
decrypt a Welcome + application messages authored by the Rust side.

## What the vector contains

- `joiner.init_priv` / `encryption_priv` / `signature_priv` / `signature_pub` —
  all the private key material the joiner needs to drive
  `MlsGroup.processWelcome`.
- `joiner.key_package` (MlsMessage-wrapped) and `key_package_raw`.
- `welcome` (MlsMessage-wrapped) — Alice's Welcome for Bob.
- `committer.signer_pub` — Alice's Ed25519 signature public key.
- `exporter.{label,context,length,secret}` — `MLS-Exporter("marmot",
  "group-event", 32)` derived from Bob's post-join state. This is the
  exporter Marmot uses to derive the outer ChaCha20 key for kind:445
  events, so a match here means Amethyst's whole post-join key schedule
  agrees with openmls byte-for-byte.
- `app_messages_alice_to_bob[]` — Alice-sent PrivateMessage bytes plus
  the expected plaintext. (Amethyst decrypt currently skips
  `PrivateMessageContent` framing; the matching test is `@Ignore`d
  until that is fixed.)

## Regenerating

```
cd quartz/tools/mdk-vector-gen
cargo run --release > ../../src/commonTest/resources/mls/mdk-welcome.json
```

The generator uses fresh randomness each run, so the committed vector
changes on regeneration — that is fine because the Kotlin test only
asserts round-trip correctness against whatever is in the JSON. Commit
the regenerated file if you change the generator.
