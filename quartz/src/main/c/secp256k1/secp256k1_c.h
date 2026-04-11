/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
#ifndef SECP256K1_C_H
#define SECP256K1_C_H

#include <stdint.h>
#include <stddef.h>
#include <string.h>

/*
 * Custom secp256k1 implementation for Amethyst/Nostr.
 *
 * This mirrors the Kotlin pure implementation structure but uses
 * C's 5x52-bit limbs (matching libsecp256k1) with platform-specific
 * 128-bit integer support for maximum performance on ARM64 and x86_64.
 *
 * Key differences from the Kotlin version:
 *   - 5x52-bit limbs with 12-bit headroom (lazy reduction)
 *   - Native __int128 for 64x64->128 multiply (single MULQ/MUL instruction)
 *   - Platform-specific ASM for field multiply on ARM64 (UMULH, UMULL)
 *   - Precomputed tables at compile time (no lazy init overhead)
 *   - Batch verification with randomized linear combination
 */

/* ==================== Platform Detection ==================== */

#if defined(__SIZEOF_INT128__)
    #define HAVE_INT128 1
    typedef unsigned __int128 uint128_t;
#else
    #define HAVE_INT128 0
#endif

#if defined(__aarch64__) || defined(_M_ARM64)
    #define SECP_ARM64 1
#else
    #define SECP_ARM64 0
#endif

#if defined(__x86_64__) || defined(_M_X64)
    #define SECP_X86_64 1
#else
    #define SECP_X86_64 0
#endif

/* ==================== Field Element (5x52-bit limbs) ==================== */

/*
 * Field element modulo p = 2^256 - 2^32 - 977.
 * 5 limbs of 52 bits each, with 12 bits of headroom per limb.
 * This allows 3-8 chained additions without reduction (lazy reduction).
 *
 * Magnitude tracking: after N additions without reduction, each limb
 * can be up to N * 2^52. We reduce when magnitude exceeds safe limits.
 */
typedef struct {
    uint64_t d[5];
} secp256k1_fe;

/* Wide result of field multiplication (used internally) */
typedef struct {
    uint64_t d[10];
} secp256k1_fe_wide;

/* ==================== Scalar (mod n) ==================== */

typedef struct {
    uint64_t d[4]; /* 4x64-bit limbs, little-endian */
} secp256k1_scalar;

/* ==================== Points ==================== */

/* Jacobian point: affine (X/Z^2, Y/Z^3) */
typedef struct {
    secp256k1_fe x;
    secp256k1_fe y;
    secp256k1_fe z;
    int infinity;
} secp256k1_gej;

/* Affine point */
typedef struct {
    secp256k1_fe x;
    secp256k1_fe y;
} secp256k1_ge;

/* ==================== Public API ==================== */

/* Initialize the library (precompute tables). Thread-safe, idempotent. */
void secp256k1c_init(void);

/* Key operations */
int secp256k1c_pubkey_create(uint8_t *pub65, const uint8_t *seckey32);
int secp256k1c_pubkey_compress(uint8_t *pub33, const uint8_t *pub65);
int secp256k1c_seckey_verify(const uint8_t *seckey32);

/* BIP-340 Schnorr signatures */
int secp256k1c_schnorr_sign(
    uint8_t *sig64,
    const uint8_t *msg,
    size_t msg_len,
    const uint8_t *seckey32,
    const uint8_t *auxrand32  /* NULL for deterministic */
);

/* Sign with pre-computed x-only pubkey (fast path) */
int secp256k1c_schnorr_sign_xonly(
    uint8_t *sig64,
    const uint8_t *msg,
    size_t msg_len,
    const uint8_t *seckey32,
    const uint8_t *xonly_pub32,
    const uint8_t *auxrand32
);

/* Full BIP-340 verification (with y-parity check) */
int secp256k1c_schnorr_verify(
    const uint8_t *sig64,
    const uint8_t *msg,
    size_t msg_len,
    const uint8_t *pub32
);

/* Fast verification (skip y-parity check, safe for Nostr) */
int secp256k1c_schnorr_verify_fast(
    const uint8_t *sig64,
    const uint8_t *msg,
    size_t msg_len,
    const uint8_t *pub32
);

/* Batch verification of N signatures from the SAME pubkey */
int secp256k1c_schnorr_verify_batch(
    const uint8_t *pub32,
    const uint8_t *const *sigs64,
    const uint8_t *const *msgs,
    const size_t *msg_lens,
    size_t count
);

/* BIP-32 key derivation */
int secp256k1c_privkey_tweak_add(uint8_t *result32, const uint8_t *seckey32, const uint8_t *tweak32);

/* ECDH */
int secp256k1c_pubkey_tweak_mul(uint8_t *result, size_t result_len,
                                 const uint8_t *pubkey, size_t pubkey_len,
                                 const uint8_t *tweak32);

int secp256k1c_ecdh_xonly(uint8_t *result32, const uint8_t *xonly_pub32, const uint8_t *scalar32);

#endif /* SECP256K1_C_H */
