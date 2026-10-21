# A `quartz-crypto` module that Quartz and quic-kmp share

Status: **superseded by a separate repository** (2026-10-20). In review the module became its own
repo, [vitorpamplona/crypto-kmp](https://github.com/vitorpamplona/crypto-kmp), and the primitives
were renamed to `com.vitorpamplona.crypto.*` (`sha2`, `digest`, `mac`, `kdf`, `aes`, `chacha`,
`curve25519`, `random`), with every import in quartz, the apps and quic-kmp rewritten instead of
keeping the old package names. That reverses D1 and D3 below. Phases 1 and 3 are done that way:
crypto-kmp 1.0.0 holds the code, quartz takes it as `api(libs.crypto)`, and quic-kmp 1.0.1 depends
on it instead of quartz. `Hex` and `Log` stayed in quartz; NIP-44's `fastExpand`/`MessageKey`
stayed in quartz as `nip44Encryption/crypto/Nip44MessageKey.kt`. Phase 2 (a public API shaped for
quic's hot path) and Phase 4 remain open, now in crypto-kmp. The text below is the original plan.

Follows [vitorpamplona/amethyst#4586](https://github.com/vitorpamplona/amethyst/pull/4586), which
replaced Quartz's third-party native crypto with in-repo code.

## Why

quic-kmp ([vitorpamplona/quic-kmp](https://github.com/vitorpamplona/quic-kmp)) declares
`api(libs.quartz)` to reach 11 symbols:

| Symbol | Where it lives in Quartz |
| --- | --- |
| `X25519`, `X25519KeyPair` | `mls.crypto` |
| `ChaCha20Core`, `ChaCha20Poly1305`, `Hkdf` | `nip44Encryption.crypto` |
| `AESGCM`, `MacInstance`, `sha256`, `RandomInstance` | `utils.ciphers`, `utils.mac`, `utils.sha256`, `utils` |
| `Log`, `TimeUtils` | `utils` (not crypto) |

`Log` and `TimeUtils` are habits from the days quic lived in Amethyst, not needs: 4 debug log
calls and 2 default clocks. quic-kmp drops both on its own (see Phase 3), so only the 9 crypto
symbols are left for this module to serve.

Three problems follow:

1. **Everything else in Quartz comes with it.** Every quic user gets Quartz's other `api`
   dependencies on their classpath: `androidx.sqlite`, `negentropy-kmp`, `secp256k1-kmp` with its
   JNI builds, and Jackson on JVM/Android. A WebTransport client has no use for any of them.
2. **Generic primitives sit under Nostr names.** ChaCha20 lives in `nip44Encryption`, X25519 in
   `mls`. quic imports from a NIP-44 package to run TLS.
3. **The best code is unreachable.** #4586 left Quartz with its own SHA-1/SHA-256/SHA-512
   (with state copy), HMAC with cached key states, and constant-time AES, AES-GCM and AES-CBC.
   All of it is `internal`. quic's native placeholders (`quic/src/nativeCheck`) need exactly
   these: a TLS transcript hash that can be snapshotted (`ShaDigest.copyFrom`) and a one-block
   AES for header protection. That is the groundwork for audio rooms on iOS
   (`commons/plans/2026-10-09-ios-app.md`, B11).

## What moves

60 main-source files: 34 in `commonMain`, 12 in `jvmAndroid`, 5 in `appleMain`, 4 in
`nativeMain`, 5 in `linuxMain`. Their tests move with them.

| Group | Files (packages unchanged) |
| --- | --- |
| SHA-1/2 | `utils/sha2/` (`ShaDigest`, `Sha256Compression`), `utils/sha256/` (`sha256`, `sha256Into`, the JVM `Sha256Pool`/`Sha256Hasher`/`CountingInputStream`, Apple and Linux actuals), `nip13Pow/miner/Sha256Midstate` |
| Other digests | `utils/diggest/` (`DigestInstance` + actuals, `StreamingDigest`), `nip03Timestamp/ots/crypto/` (`RIPEMD160Digest`, `KeccakDigest`, `GeneralDigest` and its BouncyCastle-style interfaces, `Pack`) |
| MAC and KDF | `utils/mac/` (`MacInstance` + actuals, `Hmac`), `nip44Encryption/crypto/Hkdf` |
| Ciphers | `utils/ciphers/` (`AESCBC`, `AESGCM`, `NostrCipher` + actuals), `utils/ciphers/aes/`, `nip44Encryption/crypto/` (`ChaCha20`, `ChaCha20Core`, `Poly1305`, `ChaCha20Poly1305`, `XChaCha20Poly1305`) |
| Curves | `mls/crypto/` `X25519`, `Curve25519Field`, `Ed25519` + actuals |
| Support | `utils/` `RandomInstance`, `SecureRandom` + actuals, `ConstantTime`, `Hex` |

The support files move because the crypto files need them: `Hkdf` compares MACs with
`equalsConstantTime`, anything random goes through `RandomInstance`, and hex is the byte codec
error messages print with. None of them imports anything else from Quartz.

**No logging in the module.** Six moving files log today:
- `decryptOrNull` warns on every failed decryption: `AESCBC` (JVM/Android, Apple, Linux) and
  `AESGCM` (JVM/Android, native).
- The JVM `Sha256Pool` warns when it runs low.

Phase 1 removes these six calls instead of moving `Log`, and the module ends up depending on
nothing but the Kotlin stdlib:
- A library primitive can't tell an expected `null` from an alarming one. Commons'
  `EncryptedBlobInterceptor` returns `null` on purpose for some blobs and retries with a UTF-8
  nonce, so that warning is noise.
- The other two callers, Concord's `ImagePointer` and `CyberspaceBagEvent`, can log their own
  `null` where it means something.
- The pool warning has nothing to act on: `take()` blocks until a digest comes back.

**What stays in Quartz:**
- **secp256k1/Schnorr.** It is Nostr's curve and carries the JNI builds; moving it would bring
  those back to quic.
- **`Hpke` and `MlsCryptoProvider`.** They depend on the MLS TLS codec (`mls.codec`).
- **The protocol compositions:** NIP-04, NIP-44 v2, NIP-49, gift wraps, Cashu BDHKE, the
  OpenTimestamps ops.
- **`Log`, `LogLevel`, `LogSink`** (414 files outside Quartz import `Log`) and **`TimeUtils`**.
  Neither is crypto, and after Phase 3 quic uses neither.

**Boundary check (run on 2026-10-20):** the moving files import exactly one thing from outside the set,
`nip01Core.core.toHexKey`, used in `Hkdf` (an error message) and `AESGCM.jvmAndroid`. Both
become `Hex.encode`, which moves. One `internal` symbol crosses the line: `Sha256Compression`,
used by `Sha256Midstate`. Moving the miner's midstate too keeps it `internal`. It's plain
SHA-256 resumed from a saved state, used by `PoWMiner`, `PoWHashMode` and commons'
`PoWEstimator`, all through its public API.

## Decisions

**D1. A module in this repo, not a new repo.**
- Quartz writes most of this code, and a primitive and its first use should land in one commit.
- It ships on Quartz's release train at the same version, so there is no third version to keep
  aligned.
- It fits the existing rule: a module for code with no Nostr in it (`nestsClient`, `marmotQuic`).

**D2. Name.** Gradle `:quartzCrypto` (the repo's camelCase module style), published as
`com.vitorpamplona.quartz:quartz-crypto` at `libs.versions.app`, beside `quartz`.

**D3. Keep every package name.** This is the `commonsUI` precedent: "the split is a module
boundary, not a package rename".
- Outside Quartz, 37 files import `utils.sha256`, 33 `RandomInstance` and 22 `utils.ciphers`.
  None of them changes.
- Quartz declares `api(project(":quartzCrypto"))`, so a consumer that depends on `quartz`
  compiles unchanged.
- The cost is a package split across two jars. Kotlin, Android and R8 don't care. JPMS would,
  but Quartz ships no `module-info`.
- Moving the misplaced packages to `com.vitorpamplona.quartz.crypto` is a separate, optional
  step. Typealiases can bridge classes but not top-level functions such as `sha256`, so it would
  be a breaking change for Quartz users.

**D4. Public API, platform-best by default.** Phase 2 promotes what quic needs. On JVM and
Android, quic gets its speed from JCA (cached `Cipher`, range `doFinal`), and a pure-Kotlin
public class would be a step backwards there. So each new public type is an `expect` with JCA
actuals on `jvmAndroid` and the Kotlin code on native, the shape quic's own
`PlatformCrypto.kt` already has:
- **Digest with snapshot:** an incremental SHA-256/SHA-384/SHA-512 with `copy()`. JVM uses
  `MessageDigest.clone()`; native uses `ShaDigest.copyFrom`. SHA-384 is new: TLS 1.3's
  `TLS_AES_256_GCM_SHA384` needs it, and quic defines but rejects that suite today.
- **One-block AES** encrypt, for QUIC header protection. JVM: `AES/ECB/NoPadding`. Native:
  `AesBitsliced`.
- **AES-GCM and ChaCha20-Poly1305 bound to a key**, with range input and in-place output
  (`sealInto`/`openInto`) like quic's `Aead`. JVM: cached JCA `Cipher`. Native: `AesGcmKey`
  and the existing ChaCha20-Poly1305.
- `X25519`, `Hkdf`, `ChaCha20Core` and `RandomInstance` are already public and stay as they are.

`AesBitsliced`, `Ghash` and `Hmac` stay `internal`: they are implementation, not API.

**D5. No binary-compatibility tooling yet.** Quartz has never promised a stable API, and its
version tracks the app. Phase 2's public types are the first promise worth guarding, so add
Kotlin's binary-compatibility-validator to `:quartzCrypto` alone when they land. It is a new
dependency, so check its license against its actual `LICENSE` file before wiring it in, as
`AGENTS.md` requires.

## Phases

### Phase 1: create the module and move the files (no API change)

1. Create `quartzCrypto/build.gradle.kts`.
   - Copy `quartz/build.gradle.kts` for the targets: JVM, Android, `iosArm64`,
     `iosSimulatorArm64`, `macosArm64`, `linuxX64`.
   - Copy the source-set tree: `jvmAndroid`; `nativeMain` above `appleMain` (iOS and macOS) and
     `linuxMain`; the test sets to match.
   - Copy the Android namespace, publishing and signing blocks.
   - Its dependencies are the Kotlin stdlib alone. Check that nothing else is needed.
2. Add `include(":quartzCrypto")` to `settings.gradle.kts`, and
   `api(project(":quartzCrypto"))` to Quartz's `commonMain`.
3. `git mv` the files listed above, keeping history, then their tests. The tests that move:
   - `utils/sha2`, `utils/mac`, `utils/ciphers/aes`, `utils/sha256`;
   - `nativeTest` `DigestInstanceNativeTest`;
   - `jvmTest` `ShaDigestJcaTest` and `AesJcaTest`;
   - `Sha256MidstateTest`;
   - the NIP-44 primitive tests (`ChaCha20CoreTest`, `ChaCha20Poly1305Test`, `XChaCha20Test`,
     `HkdfText`);
   - `jvmAndroidTest` `Ed25519Test`.

   Tests of compositions (NIP-04/44 envelopes, MLS) stay in Quartz.
4. Replace the two `toHexKey()` calls with `Hex.encode`, and remove the six log calls (see "No
   logging in the module"). The only behaviour change: failed decryptions in `decryptOrNull`
   no longer log a warning.
5. Build wiring:
   - apply `gradle/kmp-purity.gradle.kts`;
   - add `:quartzCrypto` to the `lint` job's purity and metadata compiles;
   - add its `jvmTest` to the JVM test jobs, to `test-quartz-linux-native` and to
     `test-quartz-ios`;
   - add it to `.git-hooks/pre-push` (keep the hook and `build.yml` in sync, as the hook says);
   - add it to the root `test` alias.
6. R8: `quartz/consumer-rules.pro` names none of the moved classes today. Recheck it and
   `amethyst/proguard-rules.pro` at move time, and give the module its own consumer rules if
   one turns out to be needed.
7. Docs:
   - `quartz/CLIENT.md` and `quartz/agent-skill/quartz/`: name the new artifact;
   - `AGENTS.md`: module list, architecture tree, the "sharing philosophy" line for crypto;
   - the `kotlin-multiplatform` skill, if it lists modules.

**Done when:**
- every module builds with no source change outside Quartz;
- the moved tests pass in `:quartzCrypto` on JVM, Linux native and iOS;
- `publishToMavenLocal` produces both artifacts;
- Quartz's POM lists `quartz-crypto` as a compile dependency.

### Phase 2: the public API quic needs

Add D4's types to `:quartzCrypto`, each with:
- JCA and Kotlin implementations;
- the existing OpenSSL and NIST vectors run through every platform;
- a JVM test against JCA;
- a Linux native run.

Then add the binary-compatibility validator (D5). Quartz's own callers don't change.

### Phase 3: quic-kmp 2.0.0 on `quartz-crypto`

1. **Done ahead of this plan,** on quic-kmp's `claude/compassionate-wozniak-743sh0` branch
   (`43a5cb5`, not yet merged or released): quic stops using Quartz's `TimeUtils` and `Log`.
   - The two default clocks read `kotlin.time.Clock.System`, which is stable since Kotlin 2.3.
   - The four debug logs go through quic's own `QuicLog` hook, which logs nothing until a sink
     is set.
   - Amethyst must point `QuicLog.sink` at Quartz's `Log` when it bumps `quicKmp`. Otherwise
     quic's debug lines disappear from its logs.
2. Then:
   - depend on `com.vitorpamplona.quartz:quartz-crypto`, replacing `quartz`;
   - move `crypto/PlatformCrypto.kt` and the AEADs onto Phase 2's types where they are at
     least as fast. Keep quic's `Aead` interface, which carries the RFC 9001 limits.
3. Version 2.0.0. Two caveats:
   - **`X25519KeyPair` is no change.** It's in quic's public API (`TlsClient`), but it keeps its
     fully qualified name.
   - **Dropping Quartz is a breaking change anyway.** Anyone relying on quic to bring in Quartz
     must now add it themselves. Amethyst's `nestsClient` and `marmotQuic` already depend on
     Quartz directly.
4. Release order:
   1. `quartz` and `quartz-crypto` publish together.
   2. quic-kmp 2.0.0 publishes, pinned to that version (it pins Quartz `1.19.0` today).
   3. Amethyst bumps `quicKmp`.
5. With `-PquicKmpDir=../quic-kmp`, the included quic build must resolve `quartz-crypto` to this
   checkout's `:quartzCrypto`, the way it resolves `quartz` to `:quartz` today. Check that the
   coordinates match, so Gradle's composite build swaps it in without an explicit substitute.

### Phase 4 (own plan): quic on native

- Real native actuals:
  - the transcript hash on Phase 2's digest snapshot;
  - header protection on the one-block AES;
  - the AEADs on the bound-key types;
  - a UDP socket on POSIX or Network.framework.
- Then an iOS target for quic and `nestsClient`.

This is item B11 of the iOS plan. It needs its own plan for the socket and audio work.

## Open questions for review

Settled in review: the module stays crypto-only (`Log` and `TimeUtils` remain in Quartz, and
quic stops using them), which also settles the name: `quartzCrypto` / `quartz-crypto`.

1. **secp256k1.** Keeping it in Quartz (recommended) means `quartz-crypto` never needs JNI. Is
   there a consumer that wants Schnorr without the rest of Quartz?
2. **Package rename.** Leave the misplaced packages (`nip44Encryption.crypto`, `mls.crypto`)
   as they are for good, or plan the breaking rename to `com.vitorpamplona.quartz.crypto` for a
   later major release?
