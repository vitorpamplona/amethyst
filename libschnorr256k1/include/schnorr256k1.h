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

/*
 * libschnorr256k1: High-performance secp256k1 for Nostr.
 *
 * A purpose-built secp256k1 implementation optimized for Nostr/BIP-340
 * workflows: Schnorr signing/verification, batch verification for
 * same-pubkey events, x-only ECDH for NIP-44, and hardware-accelerated
 * SHA-256 on ARM64 and x86_64.
 */
#ifndef SCHNORR256K1_H
#define SCHNORR256K1_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ==================== Initialization ==================== */

/* Initialize the library (precompute tables). Thread-safe, idempotent. */
void secp256k1c_init(void);

/* ==================== Key Operations ==================== */

/* Create an uncompressed public key (65 bytes: 04 || x || y) from a 32-byte secret key.
 * Returns 1 on success, 0 if the secret key is invalid. */
int secp256k1c_pubkey_create(uint8_t *pub65, const uint8_t *seckey32);

/* Compress a 65-byte uncompressed public key to 33 bytes (02/03 || x).
 * Returns 1 on success, 0 on failure. */
int secp256k1c_pubkey_compress(uint8_t *pub33, const uint8_t *pub65);

/* Verify that a 32-byte buffer is a valid secret key (0 < key < n).
 * Returns 1 if valid, 0 if not. */
int secp256k1c_seckey_verify(const uint8_t *seckey32);

/* ==================== BIP-340 Schnorr Signatures ==================== */

/* Sign a message using BIP-340 Schnorr.
 * msg can be any length (BIP-340 specifies 32 bytes for standard use).
 * auxrand32 may be NULL for deterministic signing.
 * Returns 1 on success, 0 on failure. */
int secp256k1c_schnorr_sign(
    uint8_t *sig64,
    const uint8_t *msg,
    size_t msg_len,
    const uint8_t *seckey32,
    const uint8_t *auxrand32
);

/* Sign with a pre-computed x-only pubkey (fast path, avoids re-deriving pubkey).
 * Returns 1 on success, 0 on failure. */
int secp256k1c_schnorr_sign_xonly(
    uint8_t *sig64,
    const uint8_t *msg,
    size_t msg_len,
    const uint8_t *seckey32,
    const uint8_t *xonly_pub32,
    const uint8_t *auxrand32
);

/* Full BIP-340 verification (with y-parity check).
 * Returns 1 if the signature is valid, 0 otherwise. */
int secp256k1c_schnorr_verify(
    const uint8_t *sig64,
    const uint8_t *msg,
    size_t msg_len,
    const uint8_t *pub32
);

/* Fast verification (skip y-parity check, safe for Nostr where pubkeys
 * are always x-only). Returns 1 if valid, 0 otherwise. */
int secp256k1c_schnorr_verify_fast(
    const uint8_t *sig64,
    const uint8_t *msg,
    size_t msg_len,
    const uint8_t *pub32
);

/* Batch verification of N signatures from the SAME pubkey.
 * More efficient than N individual verifications.
 * Returns 1 if ALL signatures are valid, 0 if any is invalid. */
int secp256k1c_schnorr_verify_batch(
    const uint8_t *pub32,
    const uint8_t *const *sigs64,
    const uint8_t *const *msgs,
    const size_t *msg_lens,
    size_t count
);

/* ==================== Key Derivation ==================== */

/* BIP-32 private key tweak: result = (seckey + tweak) mod n.
 * Returns 1 on success, 0 if the result would be invalid. */
int secp256k1c_privkey_tweak_add(uint8_t *result32, const uint8_t *seckey32, const uint8_t *tweak32);

/* ==================== ECDH ==================== */

/* Elliptic curve point multiplication: result = pubkey * tweak.
 * pubkey can be 33 bytes (compressed) or 65 bytes (uncompressed).
 * Returns the result in the same format as the input pubkey. */
int secp256k1c_pubkey_tweak_mul(uint8_t *result, size_t result_len,
                                 const uint8_t *pubkey, size_t pubkey_len,
                                 const uint8_t *tweak32);

/* X-only ECDH: compute shared secret as the x-coordinate of xonly_pub * scalar.
 * Returns 1 on success, 0 on failure. */
int secp256k1c_ecdh_xonly(uint8_t *result32, const uint8_t *xonly_pub32, const uint8_t *scalar32);

/* ==================== SHA-256 ==================== */

/* One-shot SHA-256 hash. */
void secp256k1c_sha256(uint8_t *out32, const uint8_t *data, size_t len);

/* BIP-340 tagged hash: SHA256(SHA256(tag) || SHA256(tag) || msg). */
void secp256k1c_tagged_hash(uint8_t *out32, const char *tag,
                             const uint8_t *msg, size_t msg_len);

#ifdef __cplusplus
}
#endif

#endif /* SCHNORR256K1_H */
