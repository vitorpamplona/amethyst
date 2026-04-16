/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Scalar arithmetic modulo n (group order of secp256k1).
 * n = FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141
 * Uses 4x64-bit limbs (fully packed, little-endian).
 */
#ifndef SECP256K1_SCALAR_H
#define SECP256K1_SCALAR_H

#include "secp256k1_internal.h"

/* n in 4x64 little-endian */
static const secp256k1_scalar SCALAR_N = {{
    0xBFD25E8CD0364141ULL,
    0xBAAEDCE6AF48A03BULL,
    0xFFFFFFFFFFFFFFFEULL,
    0xFFFFFFFFFFFFFFFFULL
}};

static const secp256k1_scalar SCALAR_ZERO = {{0, 0, 0, 0}};

/* n/2 for GLV split sign check */
static const secp256k1_scalar SCALAR_N_HALF = {{
    0xDFE92F46681B20A0ULL,
    0x5D576E7357A4501DULL,
    0xFFFFFFFFFFFFFFFFULL,
    0x7FFFFFFFFFFFFFFFULL
}};

/* lambda for GLV endomorphism */
static const secp256k1_scalar SCALAR_LAMBDA = {{
    0xDF02967C1B23BD72ULL,
    0x122E22EA20816678ULL,
    0xA5261C028812645AULL,
    0x5363AD4CC05C30E0ULL
}};

int scalar_is_zero(const secp256k1_scalar *a);
int scalar_is_valid(const secp256k1_scalar *a);
int scalar_cmp(const secp256k1_scalar *a, const secp256k1_scalar *b);

/* r = (a + b) mod n */
void scalar_add(secp256k1_scalar *r, const secp256k1_scalar *a, const secp256k1_scalar *b);

/* r = (a - b) mod n */
void scalar_sub(secp256k1_scalar *r, const secp256k1_scalar *a, const secp256k1_scalar *b);

/* r = -a mod n */
void scalar_negate(secp256k1_scalar *r, const secp256k1_scalar *a);

/* r = (a * b) mod n */
void scalar_mul(secp256k1_scalar *r, const secp256k1_scalar *a, const secp256k1_scalar *b);

/* Reduce: if a >= n, subtract n */
void scalar_reduce(secp256k1_scalar *r);

/* Serialize/deserialize (big-endian 32 bytes) */
void scalar_to_bytes(uint8_t *out32, const secp256k1_scalar *a);
void scalar_from_bytes(secp256k1_scalar *r, const uint8_t *in32);

/* GLV decomposition: k = k1 + k2*lambda, |k1|,|k2| ~ 128 bits */
typedef struct {
    secp256k1_scalar k1;
    secp256k1_scalar k2;
    int neg_k1;
    int neg_k2;
} glv_split;

void glv_split_scalar(glv_split *out, const secp256k1_scalar *k);

/* wNAF encoding: encode scalar into width-w NAF digits */
int wnaf_encode(int *wnaf, int max_len, const secp256k1_scalar *s, int w);

#endif /* SECP256K1_SCALAR_H */
