# libschnorr256k1

A fast, from-scratch BIP-340 Schnorr signature library on the secp256k1 curve.

Built for [Nostr](https://nostr.com) event verification where every microsecond
counts: batch verification amortizes the cost of verifying many signatures from
the same pubkey, a fast-verify mode skips the y-parity check for x-only keys,
and hardware-accelerated SHA-256 uses native instructions on ARM64 and x86_64.

## Features

- **BIP-340 Schnorr signatures** — sign, verify, and batch-verify
- **Variable-length messages** — not limited to 32-byte hashes
- **Same-pubkey batch verification** — randomized linear combination
- **Fast verify** — skip y-parity check (safe for Nostr x-only pubkeys)
- **X-only ECDH** — shared secret for NIP-44 encryption
- **Key operations** — create, compress, tweak-add, tweak-mul
- **SHA-256** — with BIP-340 tagged hashes, HW-accelerated on ARM64/x86_64
- **Zero dependencies** — pure C11, no allocator, no libc beyond `<string.h>`
- **Platform-tuned** — ARM64 crypto extensions, x86_64 BMI2/SHA, LTO

## Quick Start

```c
#include "schnorr256k1.h"

/* Call once at startup */
secp256k1c_init();

/* Sign */
uint8_t sig[64];
secp256k1c_schnorr_sign(sig, msg, msg_len, secret_key, aux_rand);

/* Verify */
int ok = secp256k1c_schnorr_verify(sig, msg, msg_len, x_only_pubkey);

/* Batch verify (same pubkey, N signatures) */
int all_ok = secp256k1c_schnorr_verify_batch(
    pubkey, sigs, msgs, msg_lens, count
);

/* ECDH shared secret */
uint8_t shared[32];
secp256k1c_ecdh_xonly(shared, their_xonly_pub, my_secret_key);
```

## API

All public functions are declared in [`include/schnorr256k1.h`](include/schnorr256k1.h).

| Function | Description |
|----------|-------------|
| `secp256k1c_init()` | Initialize precomputed tables (call once, thread-safe) |
| `secp256k1c_schnorr_sign()` | BIP-340 Schnorr sign (any message length) |
| `secp256k1c_schnorr_sign_xonly()` | Sign with cached x-only pubkey (fast path) |
| `secp256k1c_schnorr_verify()` | Full BIP-340 verification |
| `secp256k1c_schnorr_verify_fast()` | Fast verify (skip y-parity, safe for Nostr) |
| `secp256k1c_schnorr_verify_batch()` | Batch verify N sigs from the same pubkey |
| `secp256k1c_pubkey_create()` | Derive uncompressed public key from secret key |
| `secp256k1c_pubkey_compress()` | Compress 65-byte pubkey to 33 bytes |
| `secp256k1c_seckey_verify()` | Check if a 32-byte buffer is a valid secret key |
| `secp256k1c_privkey_tweak_add()` | BIP-32 key derivation: `(key + tweak) mod n` |
| `secp256k1c_pubkey_tweak_mul()` | EC point multiplication: `pubkey * scalar` |
| `secp256k1c_ecdh_xonly()` | X-only ECDH shared secret |
| `secp256k1c_sha256()` | One-shot SHA-256 |
| `secp256k1c_tagged_hash()` | BIP-340 tagged hash |

All functions operate on byte arrays — no opaque context objects.

## Build

```bash
mkdir build && cd build
cmake ..
make
```

CMake options:

| Option | Default | Description |
|--------|---------|-------------|
| `BUILD_TESTS` | `ON` | Build the test suite |
| `BUILD_BENCH` | `ON` | Build the benchmark |

Platform optimizations are detected automatically:

| Platform | Flags |
|----------|-------|
| ARM64 | `-march=armv8-a+crypto` (NEON + SHA) |
| x86_64 | `-march=x86-64-v2 -mbmi2 -msha -msse4.1` |
| Other | Portable `-O3` |

LTO is enabled when the toolchain supports it.

### Android (NDK)

```bash
./scripts/build_android.sh [OUTPUT_DIR]
```

Builds ARM64 and x86_64 static libraries using the Android NDK.

### iOS / macOS

Use CMake with the appropriate toolchain file:

```bash
cmake -DCMAKE_TOOLCHAIN_FILE=ios.toolchain.cmake -DPLATFORM=OS64 ..
```

## Test

```bash
cd build
./test/schnorr256k1_tests
```

Or via CTest:

```bash
cd build
ctest
```

The test suite includes 103 tests across 6 files:

| File | Tests | Coverage |
|------|-------|----------|
| `test_schnorr.c` | 21 | Full BIP-340 test vectors (0-18), sign/verify round-trip |
| `test_secp256k1.c` | 23 | Key ops, ECDH symmetry, batch verify, tagged hash |
| `test_field.c` | 23 | Field arithmetic mod p, carry-fold regression |
| `test_point.c` | 13 | EC point doubling, addition, scalar multiplication |
| `test_key_codec.c` | 13 | Key parsing, serialization, liftX |
| `test_sha256.c` | 8 | SHA-256 KATs, tagged hash consistency |

All BIP-340 test vectors come from the
[official BIP-340 spec](https://github.com/bitcoin/bips/blob/master/bip-0340/test-vectors.csv).

## Benchmark

```bash
cd build
./bench/secp256k1_bench
```

Sample output (x86_64):

```
Operation                                 µs/op
─────────                                 ─────
pubkeyCreate                               13.0
signSchnorr                                26.1
signSchnorrXOnly (cached pubkey)           13.3
verifySchnorr                              35.1
verifySchnorrFast                          31.9
verifyBatch(4)                             46.7 per batch
verifyBatch(16)                            82.4 per batch
ecdhXOnly                                  26.3
```

## Project Structure

```
libschnorr256k1/
├── include/
│   └── schnorr256k1.h         # Public API
├── src/
│   ├── secp256k1_internal.h   # Internal types, platform detection
│   ├── field.c / field.h      # Field arithmetic (4x64-bit limbs, mod p)
│   ├── field_asm.h            # ARM64 assembly for field multiply
│   ├── scalar.c / scalar.h    # Scalar arithmetic (mod n, GLV)
│   ├── point.c / point.h      # EC point ops, comb/GLV multiplication
│   ├── schnorr.c              # BIP-340, key ops, ECDH, SHA wrappers
│   ├── sha256.c / sha256.h    # SHA-256 + tagged hashes
│   └── sha256_hw.h            # HW-accelerated SHA-256
├── test/                      # 103 tests
├── bench/                     # Standalone benchmark
├── scripts/                   # Android NDK build script
├── CMakeLists.txt
└── LICENSE                    # MIT
```

## Design

This is **not** a fork of Bitcoin's `libsecp256k1`. It is a from-scratch
implementation written in C11 with these design choices:

- **4x64-bit limbs** for field elements (fewer multiplies than 5x52)
- **Comb method** for generator multiplication (~43 point lookups)
- **GLV endomorphism + wNAF** for arbitrary point multiplication
- **Strauss/Shamir + GLV** for dual scalar multiplication (verification)
- **Montgomery's trick** for batch affine conversion
- **Lazy reduction** — field operations defer normalization
- **Precomputed tables** initialized once at startup

## License

MIT. See [LICENSE](LICENSE).
