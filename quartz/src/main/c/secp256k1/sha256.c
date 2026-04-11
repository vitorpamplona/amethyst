/*
 * Copyright (c) 2025 Vitor Pamplona
 * Minimal SHA-256 for BIP-340. No external dependencies.
 */
#include "sha256.h"
#include "sha256_hw.h"
#include <string.h>

static const uint32_t K[64] = {
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
    0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
    0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
    0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
    0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
    0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
    0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
    0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
    0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
};

#define ROR32(x, n) (((x) >> (n)) | ((x) << (32 - (n))))
#define CH(x, y, z)  (((x) & (y)) ^ (~(x) & (z)))
#define MAJ(x, y, z) (((x) & (y)) ^ ((x) & (z)) ^ ((y) & (z)))
#define SIG0(x) (ROR32(x, 2) ^ ROR32(x, 13) ^ ROR32(x, 22))
#define SIG1(x) (ROR32(x, 6) ^ ROR32(x, 11) ^ ROR32(x, 25))
#define sig0(x) (ROR32(x, 7) ^ ROR32(x, 18) ^ ((x) >> 3))
#define sig1(x) (ROR32(x, 17) ^ ROR32(x, 19) ^ ((x) >> 10))

static inline uint32_t be32(const uint8_t *p) {
    return ((uint32_t)p[0] << 24) | ((uint32_t)p[1] << 16) |
           ((uint32_t)p[2] << 8) | (uint32_t)p[3];
}

static inline void be32_put(uint8_t *p, uint32_t v) {
    p[0] = (uint8_t)(v >> 24);
    p[1] = (uint8_t)(v >> 16);
    p[2] = (uint8_t)(v >> 8);
    p[3] = (uint8_t)v;
}

#if SHA256_HW_AVAILABLE
/* Use hardware-accelerated transform (SHA-NI on x86_64, CE on ARM64) */
static void sha256_transform(uint32_t state[8], const uint8_t block[64]) {
    sha256_transform_hw(state, block);
}
#else
/* Software fallback */
static void sha256_transform_sw(uint32_t state[8], const uint8_t block[64]) {
    uint32_t W[64];
    uint32_t a, b, c, d, e, f, g, h;
    int i;

    for (i = 0; i < 16; i++)
        W[i] = be32(block + 4 * i);
    for (i = 16; i < 64; i++)
        W[i] = sig1(W[i-2]) + W[i-7] + sig0(W[i-15]) + W[i-16];

    a = state[0]; b = state[1]; c = state[2]; d = state[3];
    e = state[4]; f = state[5]; g = state[6]; h = state[7];

    for (i = 0; i < 64; i++) {
        uint32_t t1 = h + SIG1(e) + CH(e, f, g) + K[i] + W[i];
        uint32_t t2 = SIG0(a) + MAJ(a, b, c);
        h = g; g = f; f = e; e = d + t1;
        d = c; c = b; b = a; a = t1 + t2;
    }

    state[0] += a; state[1] += b; state[2] += c; state[3] += d;
    state[4] += e; state[5] += f; state[6] += g; state[7] += h;
}
static void sha256_transform(uint32_t state[8], const uint8_t block[64]) {
    sha256_transform_sw(state, block);
}
#endif /* SHA256_HW_AVAILABLE */

void secp256k1_sha256_init(secp256k1_sha256 *ctx) {
    ctx->state[0] = 0x6a09e667; ctx->state[1] = 0xbb67ae85;
    ctx->state[2] = 0x3c6ef372; ctx->state[3] = 0xa54ff53a;
    ctx->state[4] = 0x510e527f; ctx->state[5] = 0x9b05688c;
    ctx->state[6] = 0x1f83d9ab; ctx->state[7] = 0x5be0cd19;
    ctx->total = 0;
}

void secp256k1_sha256_update(secp256k1_sha256 *ctx, const uint8_t *data, size_t len) {
    size_t fill = (size_t)(ctx->total & 63);
    ctx->total += len;

    if (fill && fill + len >= 64) {
        size_t copy = 64 - fill;
        memcpy(ctx->buf + fill, data, copy);
        sha256_transform(ctx->state, ctx->buf);
        data += copy;
        len -= copy;
        fill = 0;
    }

    while (len >= 64) {
        sha256_transform(ctx->state, data);
        data += 64;
        len -= 64;
    }

    if (len > 0) {
        memcpy(ctx->buf + fill, data, len);
    }
}

void secp256k1_sha256_finalize(secp256k1_sha256 *ctx, uint8_t *out32) {
    uint64_t bits = ctx->total * 8;
    size_t fill = (size_t)(ctx->total & 63);
    uint8_t pad = (fill < 56) ? (uint8_t)(56 - fill) : (uint8_t)(120 - fill);
    uint8_t tmp[72]; /* max padding */
    int i;

    memset(tmp, 0, sizeof(tmp));
    tmp[0] = 0x80;
    secp256k1_sha256_update(ctx, tmp, pad);

    /* Append length in big-endian */
    be32_put(tmp, (uint32_t)(bits >> 32));
    be32_put(tmp + 4, (uint32_t)bits);
    secp256k1_sha256_update(ctx, tmp, 8);

    for (i = 0; i < 8; i++)
        be32_put(out32 + 4 * i, ctx->state[i]);
}

void secp256k1_sha256_hash(uint8_t *out32, const uint8_t *data, size_t len) {
    secp256k1_sha256 ctx;
    secp256k1_sha256_init(&ctx);
    secp256k1_sha256_update(&ctx, data, len);
    secp256k1_sha256_finalize(&ctx, out32);
}

void secp256k1_tagged_hash(uint8_t *out32, const char *tag,
                           const uint8_t *msg, size_t msg_len) {
    uint8_t tag_hash[32];
    secp256k1_sha256 ctx;

    secp256k1_sha256_hash(tag_hash, (const uint8_t *)tag, strlen(tag));

    secp256k1_sha256_init(&ctx);
    secp256k1_sha256_update(&ctx, tag_hash, 32);
    secp256k1_sha256_update(&ctx, tag_hash, 32);
    secp256k1_sha256_update(&ctx, msg, msg_len);
    secp256k1_sha256_finalize(&ctx, out32);
}

void secp256k1_tagged_hash_precomputed(uint8_t *out32,
                                        const uint8_t *prefix64,
                                        const uint8_t *msg, size_t msg_len) {
    secp256k1_sha256 ctx;
    secp256k1_sha256_init(&ctx);
    secp256k1_sha256_update(&ctx, prefix64, 64);
    secp256k1_sha256_update(&ctx, msg, msg_len);
    secp256k1_sha256_finalize(&ctx, out32);
}
