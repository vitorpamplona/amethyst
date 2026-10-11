# Crypto and encryption in Quartz

## Signing and ids (`nip01Core/crypto/`)

| File | Holds |
|---|---|
| `EventHasher.kt` | `hashId(pubKey, createdAt, kind, tags, content)`, `hashIdCheck(id, …)` — canonical NIP-01 serialization + SHA-256 |
| `EventHasherSerializer.kt` | the exact byte layout the hash needs |
| `Nip01Crypto.kt` | `object Nip01Crypto`: `privKeyCreate()`, `pubKeyCreate(privKey)`, `sign(...)`, `signWithPubKey(...)`, `verify(...)` |
| `KeyPair.kt` | private/public key holder; `toString()` redacts the private key |
| `EventAssembler.kt` | `EventAssembler.hashAndSign(...)` — id + Schnorr signature into a typed event |
| `EventExt.kt` | `Event.generateId()`, `verifyId()`, `verifySignature()`, `verify()`, `checkSignature()` |

App code signs through a `NostrSigner` (`signer.sign(template)`), never `Nip01Crypto`
directly, so NIP-46 and NIP-55 signers keep working — see the `auth-signers` skill.

`utils/Secp256k1Instance.kt` is a plain common `object` over secp256k1-kmp
(`signSchnorr`, `verifySchnorr`, `compressedPubKeyFor`, tweaks); the platform binding
comes from the `secp256k1-kmp-jni-android` / `-jni-jvm` dependency.

## NIP-44 (`nip44Encryption/`)

`Nip44.kt` is the facade: `encrypt(msg, privateKey, pubKey): Nip44v2.EncryptedInfo`
(always the current version; call `.encodePayload()` for the base64 content) and
`decrypt(payload, privateKey, pubKey)` (any version, base64 or the legacy JSON form).
`Nip44v1.kt`/`Nip44v2.kt` are the versions; `crypto/` holds the ChaCha20/HKDF/HMAC
primitives; `SharedKeyCache` (one private instance per `Nip44v1`/`Nip44v2`/`Nip04`)
caches ECDH secrets, so don't compute conversation keys yourself.

As with signing, app code goes through `signer.nip44Encrypt(plaintext, toPublicKey)` /
`signer.nip44Decrypt(ciphertext, fromPublicKey)`, or `signer.decrypt(...)` which picks
NIP-04 vs NIP-44 from the payload. NIP-04 (`nip04Dm/crypto/Nip04.kt`) is legacy: decrypt
old DMs, don't encrypt new content with it.

## Gotchas

- Private keys stay `ByteArray`; never log keys, shared secrets or plaintext.
- NIP-44 pads plaintext — assert round-trips, not ciphertext lengths.
- Hashing your own JSON instead of `EventHasher` produces ids relays reject.

Tests: `quartz/src/commonTest/kotlin/com/vitorpamplona/quartz/nip44Encryption/`
(`Nip44v2Test`, padding, `SharedKeyCacheTest`).
