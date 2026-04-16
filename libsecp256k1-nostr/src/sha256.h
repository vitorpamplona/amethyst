/*
 * Copyright (c) 2025 Vitor Pamplona
 * Minimal SHA-256 for BIP-340 tagged hashes.
 */
#ifndef SECP256K1_SHA256_H
#define SECP256K1_SHA256_H

#include <stdint.h>
#include <stddef.h>

typedef struct {
    uint32_t state[8];
    uint8_t  buf[64];
    uint64_t total;
} secp256k1_sha256;

void secp256k1_sha256_init(secp256k1_sha256 *ctx);
void secp256k1_sha256_update(secp256k1_sha256 *ctx, const uint8_t *data, size_t len);
void secp256k1_sha256_finalize(secp256k1_sha256 *ctx, uint8_t *out32);

/* One-shot convenience */
void secp256k1_sha256_hash(uint8_t *out32, const uint8_t *data, size_t len);

/* BIP-340 tagged hash: SHA256(SHA256(tag) || SHA256(tag) || msg) */
void secp256k1_tagged_hash(uint8_t *out32, const char *tag,
                           const uint8_t *msg, size_t msg_len);

/* Tagged hash with pre-computed prefix (64 bytes = SHA256(tag) || SHA256(tag)) */
void secp256k1_tagged_hash_precomputed(uint8_t *out32,
                                        const uint8_t *prefix64,
                                        const uint8_t *msg, size_t msg_len);

#endif /* SECP256K1_SHA256_H */
