# Crypto & Encryption in Quartz

Event signing, hashing, and NIP-44 payload encryption.

## Layout

### Core crypto (`quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/crypto/`)

- `EventHasher.kt` — canonical JSON serialization + SHA-256 → event id. NIP-01 §1.
- `EventHasherSerializer.kt` — Jackson serializer that emits the exact byte layout NIP-01 hashing requires.
- `KeyPair.kt` — holder for `privateKey: ByteArray` + derived `pubKey: ByteArray`. Generates fresh key pairs via `secureRandom`.
- `Nip01Crypto.kt` — one-stop helper: sign an event, verify a signature, derive pubkey from seckey.
- `EventAssembler.kt` — takes an unsigned template + signer and produces a fully populated `Event`.
- `EventExt.kt` — `Event.verify()` / `Event.hasValidSignature()` extensions.

### secp256k1 abstraction (`quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/utils/`)

- `Secp256k1Instance.kt` — `expect object` with `signSchnorr`, `verifySchnorr`, `pubKey(seckey)`, `sharedSecret`.
- `Secp256k1InstanceC.kt` — C-based actual using secp256k1 JNI (Android/JVM).
- `Secp256k1InstanceKotlin.kt` — pure-Kotlin actual (iOS via native, etc.).
- Android actual: `secp256k1-kmp-jni-android` (0.23.0). JVM actual: `secp256k1-kmp-jni-jvm`.

### NIP-44 encryption (`quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip44Encryption/`)

- `Nip44.kt` — dispatcher that handles both v1 (ChaCha20 w/o Poly1305, legacy) and v2 (XChaCha20-Poly1305).
- `Nip44v2.kt` — current spec: HKDF key derivation → XChaCha20-Poly1305 → padded plaintext → Base64.
- `Nip44v1.kt` — legacy path (decrypt-only for backward compat; do not encrypt with v1).
- `crypto/` — `ChaCha20Poly1305`, `HKDF`, `Hmac`, etc. (pure Kotlin, MPP-friendly).
- `SharedKeyCache.kt` — in-process LRU for ECDH shared secrets. Critical for performance in chat/list screens that decrypt many messages with the same counterparty.
- `EncryptedInfoString.kt` — versioned payload envelope that the parser reads to pick v1 vs v2.

## Typical Flows

### Sign an event

```kotlin
// Direct (when you have the privkey in memory)
val signed = Nip01Crypto.sign(unsignedEvent, keyPair.privateKey)

// Via signer (preferred — honors external/remote signers)
val signer: NostrSigner = ...     // NostrSignerInternal, Nip46RemoteSigner, NostrSignerExternal
signer.sign(template) { signed -> /* emit signed event */ }
```

Use `NostrSigner` whenever the key might not live in the current process (NIP-46 bunker, NIP-55 Android external signer). See the `auth-signers` skill.

### Verify an event

```kotlin
event.verify()               // throws on failure
event.hasValidSignature()    // returns Boolean
```

Both recompute `sha256(canonicalJson(event))` and call Schnorr `verifySchnorr(sig, hash, pubKey)`.

### NIP-44 encrypt / decrypt

```kotlin
// Always compute shared secret through the cache — direct ECDH is expensive
val sharedSecret = SharedKeyCache.getOrComputeShared(mySeckey, theirPubkey)

val cipherText = Nip44.encrypt(plaintext, sharedSecret)   // v2 by default
val plain      = Nip44.decrypt(cipherText, sharedSecret)  // dispatches on version byte
```

Callers rarely touch `Nip44v2` directly; go through `Nip44`.

## Gotchas

- **Never log private keys, shared secrets, or raw plaintext.** `KeyPair.privateKey` is a `ByteArray` on purpose so it doesn't get interned as a String.
- **Don't recompute ECDH per message.** `SharedKeyCache` exists because the same counterparty appears in many messages; bypassing the cache produces noticeable UI lag.
- **`EventHasher` ordering is canonical.** Serialize tags / content exactly as `EventHasherSerializer` emits, or ids won't match relays.
- **secp256k1 JNI is platform-specific**: if you add crypto that must run in `commonTest`, wrap it in `expect/actual` or you'll get `UnsatisfiedLinkError` in JVM unit tests.
- **NIP-44 pads messages**. Don't assert exact ciphertext length; assert decrypt round-trips.

## Tests

- `quartz/src/commonTest/kotlin/com/vitorpamplona/quartz/nip01Core/crypto/` — sign/verify/hash round-trips.
- `quartz/src/commonTest/kotlin/com/vitorpamplona/quartz/nip44Encryption/` — NIP-44 vectors (encryption parity with reference vectors).
- JNI crypto is exercised in `androidUnitTest` / JVM integration tests.
