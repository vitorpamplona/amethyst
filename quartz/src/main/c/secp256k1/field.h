/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Field arithmetic modulo p = 2^256 - 2^32 - 977 using 5x52-bit limbs.
 *
 * Each limb holds up to 52 bits with 12 bits of headroom, allowing
 * multiple additions without reduction (lazy reduction). This is the
 * key advantage over the Kotlin 4x64-bit representation which requires
 * reduction after every add/sub.
 *
 * On ARM64: uses UMULH/MUL instructions via __int128
 * On x86_64: uses MULQ via __int128
 * Fallback: portable 64-bit C
 */
#ifndef SECP256K1_FIELD_H
#define SECP256K1_FIELD_H

#include "secp256k1_c.h"

#define FE_LIMB_BITS 52
#define FE_LIMB_MASK ((uint64_t)0xFFFFFFFFFFFFF) /* 52-bit mask */

/* ==================== Constants ==================== */

static const secp256k1_fe FE_ZERO = {{0, 0, 0, 0, 0}};
static const secp256k1_fe FE_ONE  = {{1, 0, 0, 0, 0}};

/* p = 2^256 - 2^32 - 977 in 5x52 limbs */
static const secp256k1_fe FE_P = {{
    0xFFFFEFFFFFC2FULL,   /* 4503595332402223 */
    0xFFFFFFFFFFFFFULL,   /* 4503599627370495 */
    0xFFFFFFFFFFFFFULL,   /* 4503599627370495 */
    0xFFFFFFFFFFFFFULL,   /* 4503599627370495 */
    0x0FFFFFFFFFFFFULL    /* 281474976710655 (48-bit top limb) */
}};

/* ==================== Core Operations ==================== */

/* Normalize to canonical form [0, p) */
static inline void fe_normalize(secp256k1_fe *r) {
    uint64_t t0 = r->d[0], t1 = r->d[1], t2 = r->d[2], t3 = r->d[3], t4 = r->d[4];
    uint64_t m;

    /* Reduce carries */
    t1 += t0 >> 52; t0 &= FE_LIMB_MASK;
    t2 += t1 >> 52; t1 &= FE_LIMB_MASK;
    t3 += t2 >> 52; t2 &= FE_LIMB_MASK;
    t4 += t3 >> 52; t3 &= FE_LIMB_MASK;

    /* t4 may overflow 48 bits; fold top bits: 2^256 = 2^32 + 977 (mod p) */
    m = t4 >> 48;
    t4 &= 0xFFFFFFFFFFFFULL; /* 48-bit mask */
    t0 += m * 0x1000003D1ULL;
    t1 += t0 >> 52; t0 &= FE_LIMB_MASK;
    t2 += t1 >> 52; t1 &= FE_LIMB_MASK;
    t3 += t2 >> 52; t2 &= FE_LIMB_MASK;
    t4 += t3 >> 52; t3 &= FE_LIMB_MASK;

    /* Final conditional subtraction of p */
    /* p in 5x52: [0xFFFFEFFFFFC2F, 0xFFFFFFFFFFFFF, 0xFFFFFFFFFFFFF, 0xFFFFFFFFFFFFF, 0x0FFFFFFFFFFFF] */
    m = (t4 == 0x0FFFFFFFFFFFFULL) &
        (t3 == FE_LIMB_MASK) &
        (t2 == FE_LIMB_MASK) &
        (t1 == FE_LIMB_MASK) &
        (t0 >= 0xFFFFEFFFFFC2FULL);
    t0 -= m * 0xFFFFEFFFFFC2FULL;
    t1 -= m * FE_LIMB_MASK;
    t2 -= m * FE_LIMB_MASK;
    t3 -= m * FE_LIMB_MASK;
    t4 -= m * 0x0FFFFFFFFFFFFULL;

    /* Re-propagate borrows */
    if (m) {
        /* After subtracting p, no borrows are possible if t >= p */
        /* But handle just in case of numerical edge cases */
    }

    r->d[0] = t0; r->d[1] = t1; r->d[2] = t2; r->d[3] = t3; r->d[4] = t4;
}

/* Normalize fully (for comparison/serialization) */
static inline void fe_normalize_full(secp256k1_fe *r) {
    fe_normalize(r);
    fe_normalize(r); /* Second pass for edge cases */
}

static inline int fe_is_zero(const secp256k1_fe *a) {
    secp256k1_fe t = *a;
    fe_normalize_full(&t);
    return (t.d[0] | t.d[1] | t.d[2] | t.d[3] | t.d[4]) == 0;
}

static inline int fe_equal(const secp256k1_fe *a, const secp256k1_fe *b) {
    secp256k1_fe ta = *a, tb = *b;
    fe_normalize_full(&ta);
    fe_normalize_full(&tb);
    return (ta.d[0] == tb.d[0]) & (ta.d[1] == tb.d[1]) & (ta.d[2] == tb.d[2]) &
           (ta.d[3] == tb.d[3]) & (ta.d[4] == tb.d[4]);
}

static inline int fe_is_odd(const secp256k1_fe *a) {
    secp256k1_fe t = *a;
    fe_normalize_full(&t);
    return (int)(t.d[0] & 1);
}

/* r = a + b (lazy: no reduction) */
static inline void fe_add(secp256k1_fe *r, const secp256k1_fe *a, const secp256k1_fe *b) {
    r->d[0] = a->d[0] + b->d[0];
    r->d[1] = a->d[1] + b->d[1];
    r->d[2] = a->d[2] + b->d[2];
    r->d[3] = a->d[3] + b->d[3];
    r->d[4] = a->d[4] + b->d[4];
}

/* r += a (in-place lazy add) */
static inline void fe_add_assign(secp256k1_fe *r, const secp256k1_fe *a) {
    r->d[0] += a->d[0];
    r->d[1] += a->d[1];
    r->d[2] += a->d[2];
    r->d[3] += a->d[3];
    r->d[4] += a->d[4];
}

/* r = -a mod p. Computes (m+1)*p - a to keep limbs positive (works for magnitude <= m) */
static inline void fe_negate(secp256k1_fe *r, const secp256k1_fe *a, int m) {
    /* Add (m+1)*p and subtract a */
    uint64_t mp = (uint64_t)(m + 1);
    r->d[0] = mp * 0xFFFFEFFFFFC2FULL  - a->d[0];
    r->d[1] = mp * 0xFFFFFFFFFFFFFULL  - a->d[1];
    r->d[2] = mp * 0xFFFFFFFFFFFFFULL  - a->d[2];
    r->d[3] = mp * 0xFFFFFFFFFFFFFULL  - a->d[3];
    r->d[4] = mp * 0x0FFFFFFFFFFFFULL  - a->d[4];
}

/* r = a * b mod p */
void fe_mul(secp256k1_fe *r, const secp256k1_fe *a, const secp256k1_fe *b);

/* r = a^2 mod p */
void fe_sqr(secp256k1_fe *r, const secp256k1_fe *a);

/* r = a^(-1) mod p (Fermat: a^(p-2)) */
void fe_inv(secp256k1_fe *r, const secp256k1_fe *a);

/* r = sqrt(a) mod p, returns 1 on success */
int fe_sqrt(secp256k1_fe *r, const secp256k1_fe *a);

/* r = a/2 mod p */
void fe_half(secp256k1_fe *r, const secp256k1_fe *a);

/* Serialize field element to 32-byte big-endian */
void fe_to_bytes(uint8_t *out32, const secp256k1_fe *a);

/* Deserialize 32-byte big-endian to field element */
int fe_from_bytes(secp256k1_fe *r, const uint8_t *in32);

/* Compare field elements: -1, 0, 1 */
int fe_cmp(const secp256k1_fe *a, const secp256k1_fe *b);

#endif /* SECP256K1_FIELD_H */
