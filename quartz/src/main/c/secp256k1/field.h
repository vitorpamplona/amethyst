/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Field arithmetic modulo p = 2^256 - 2^32 - 977 using 4x64-bit limbs.
 *
 * Same representation as the Kotlin implementation (Fe4): 4 fully-packed
 * 64-bit limbs in little-endian order. This was chosen over the 5x52-bit
 * representation because benchmark testing showed fewer multiplies (16 vs 25)
 * outweighs the lazy reduction advantage of 5x52 on both JVM and native.
 */
#ifndef SECP256K1_FIELD_H
#define SECP256K1_FIELD_H

#include "secp256k1_c.h"

/* ==================== Field Element (4x64-bit limbs, little-endian) ==================== */

/* p = 2^256 - 2^32 - 977 in 4x64 little-endian */
static const secp256k1_fe FE_P = {{
    0xFFFFFFFEFFFFFC2FULL,
    0xFFFFFFFFFFFFFFFFULL,
    0xFFFFFFFFFFFFFFFFULL,
    0xFFFFFFFFFFFFFFFFULL
}};

static const secp256k1_fe FE_ZERO = {{0, 0, 0, 0}};
static const secp256k1_fe FE_ONE  = {{1, 0, 0, 0}};

/* P[0] cached for hot path */
#define FE_P0 0xFFFFFFFEFFFFFC2FULL

/* ==================== Inline Helpers ==================== */

static inline int fe_is_zero(const secp256k1_fe *a) {
    /* Normalize before checking — elements may be unreduced */
    secp256k1_fe t = *a;
    /* Quick check: if all limbs are in range and < p, it's normalized */
    if (t.d[3] == UINT64_MAX && t.d[2] == UINT64_MAX &&
        t.d[1] == UINT64_MAX && t.d[0] >= FE_P0) {
        /* >= p, reduce */
        t.d[0] -= FE_P0; t.d[1] = 0; t.d[2] = 0; t.d[3] = 0;
    }
    return (t.d[0] | t.d[1] | t.d[2] | t.d[3]) == 0;
}

static inline int fe_equal(const secp256k1_fe *a, const secp256k1_fe *b) {
    secp256k1_fe ta = *a, tb = *b;
    /* Normalize both */
    if (ta.d[3] == UINT64_MAX && ta.d[2] == UINT64_MAX &&
        ta.d[1] == UINT64_MAX && ta.d[0] >= FE_P0) {
        ta.d[0] -= FE_P0; ta.d[1] = 0; ta.d[2] = 0; ta.d[3] = 0;
    }
    if (tb.d[3] == UINT64_MAX && tb.d[2] == UINT64_MAX &&
        tb.d[1] == UINT64_MAX && tb.d[0] >= FE_P0) {
        tb.d[0] -= FE_P0; tb.d[1] = 0; tb.d[2] = 0; tb.d[3] = 0;
    }
    return (ta.d[0] == tb.d[0]) & (ta.d[1] == tb.d[1]) &
           (ta.d[2] == tb.d[2]) & (ta.d[3] == tb.d[3]);
}

static inline int fe_is_odd(const secp256k1_fe *a) {
    secp256k1_fe t = *a;
    if (t.d[3] == UINT64_MAX && t.d[2] == UINT64_MAX &&
        t.d[1] == UINT64_MAX && t.d[0] >= FE_P0) {
        t.d[0] -= FE_P0; t.d[1] = 0; t.d[2] = 0; t.d[3] = 0;
    }
    return (int)(t.d[0] & 1);
}

/* Normalize: if a >= p, subtract p.
 * On ARM64: branchless (CSEL avoids misprediction on mobile SoCs).
 * On x86_64: branching (>99.99% correct prediction, branch is cheaper). */
static inline void fe_normalize(secp256k1_fe *a) {
#if SECP_ARM64
    /* Branchless for ARM64: compute mask, conditionally subtract */
    uint64_t ge = (a->d[3] == UINT64_MAX) & (a->d[2] == UINT64_MAX) &
                  (a->d[1] == UINT64_MAX) & (a->d[0] >= FE_P0);
    uint64_t mask = -(uint64_t)ge;
    a->d[0] -= FE_P0 & mask;
    a->d[1] &= ~mask;
    a->d[2] &= ~mask;
    a->d[3] &= ~mask;
#else
    /* Branching for x86_64: branch predictor handles the >99.99% case */
    if (a->d[3] == UINT64_MAX && a->d[2] == UINT64_MAX &&
        a->d[1] == UINT64_MAX && a->d[0] >= FE_P0) {
        a->d[0] -= FE_P0;
        a->d[1] = 0;
        a->d[2] = 0;
        a->d[3] = 0;
    }
#endif
}

static inline void fe_normalize_full(secp256k1_fe *a) {
    fe_normalize(a);
}

/* r = a + b.
 * LAZY: does NOT normalize. Result may be in [0, 2^256 + C).
 * fe_mul/fe_sqr handle unnormalized inputs via their reduction.
 * Call fe_normalize() explicitly before comparisons or serialization. */
static inline void fe_add(secp256k1_fe *r, const secp256k1_fe *a, const secp256k1_fe *b) {
    uint64_t carry = 0;
    for (int i = 0; i < 4; i++) {
        uint64_t sum = a->d[i] + b->d[i] + carry;
        carry = (sum < a->d[i]) || (carry && sum == a->d[i]) ? 1 : 0;
        r->d[i] = sum;
    }
    if (carry) {
        /* Overflow past 2^256: fold using 2^256 mod p = C = 0x1000003D1 */
        uint64_t s = r->d[0] + 0x1000003D1ULL;
        uint64_t c = (s < r->d[0]) ? 1 : 0;
        r->d[0] = s;
        if (c) { r->d[1]++; if (!r->d[1]) { r->d[2]++; if (!r->d[2]) r->d[3]++; } }
    }
    /* No normalize — result may be in [P, 2^256). This is fine because:
     * - fe_mul/fe_sqr reduce any 256-bit input correctly
     * - fe_negate uses 2P - a which handles values up to 2P
     * - fe_half handles values up to 2P
     * Only fe_is_zero, fe_cmp, fe_to_bytes need explicit normalize first. */
}

/* r += a (lazy, no normalize) */
static inline void fe_add_assign(secp256k1_fe *r, const secp256k1_fe *a) {
    secp256k1_fe t = *r;
    fe_add(r, &t, a);
}

/* r = -a mod p.
 * Uses 2P - a instead of P - a to handle unnormalized inputs in [0, 2P).
 * Result is in [0, 2P). */
static inline void fe_negate(secp256k1_fe *r, const secp256k1_fe *a, int m) {
    (void)m;
    /* 2P = [2*P0, MAX, MAX, MAX-1] + carry handling.
     * Since P = [P0, MAX, MAX, MAX], 2P = [2*P0, MAX+carry, ...].
     * Actually 2P mod 2^256 = 2*P0 with carries. Let's just do P + (P - a). */
    /* Simpler: normalize a first, then P - a. The normalize is fast (usually no-op). */
    secp256k1_fe t = *a;
    fe_normalize(&t);
    if (t.d[0] == 0 && t.d[1] == 0 && t.d[2] == 0 && t.d[3] == 0) {
        *r = FE_ZERO;
        return;
    }
    uint64_t borrow = 0;
    for (int i = 0; i < 4; i++) {
        uint64_t diff = FE_P.d[i] - t.d[i] - borrow;
        borrow = (FE_P.d[i] < t.d[i] + borrow) || (borrow && t.d[i] == UINT64_MAX) ? 1 : 0;
        r->d[i] = diff;
    }
}

/* ==================== Function declarations ==================== */

/* Field multiply and square — declared here, defined in field.c.
 * On platforms with ASM (x86_64 MULX, ARM64 CE), fe_mul dispatches
 * to the inline fe_mul_asm which the compiler can inline into callers
 * within the same compilation unit. For cross-unit inlining (point.c
 * calling fe_mul), we rely on LTO or the static inline below. */
#include "field_asm.h"

#if FE_MUL_ASM
/* Use the ASM version directly as static inline so point.c can inline it */
static inline void fe_mul(secp256k1_fe *r, const secp256k1_fe *a, const secp256k1_fe *b) {
    fe_mul_asm(r, a, b);
}
static inline void fe_sqr(secp256k1_fe *r, const secp256k1_fe *a) {
    fe_mul_asm(r, a, a);
}
#else
void fe_mul(secp256k1_fe *r, const secp256k1_fe *a, const secp256k1_fe *b);
void fe_sqr(secp256k1_fe *r, const secp256k1_fe *a);
#endif
void fe_inv(secp256k1_fe *r, const secp256k1_fe *a);
int fe_sqrt(secp256k1_fe *r, const secp256k1_fe *a);
void fe_half(secp256k1_fe *r, const secp256k1_fe *a);
void fe_to_bytes(uint8_t *out32, const secp256k1_fe *a);
int fe_from_bytes(secp256k1_fe *r, const uint8_t *in32);
int fe_cmp(const secp256k1_fe *a, const secp256k1_fe *b);

#endif /* SECP256K1_FIELD_H */
