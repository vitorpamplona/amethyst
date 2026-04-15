/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Field arithmetic modulo p = 2^256 - 2^32 - 977 using 4x64-bit limbs.
 *
 * Same representation as the Kotlin Fe4 class. Performance analysis showed
 * 4x64 is faster than 5x52 because fewer multiplies (16 vs 25) outweighs
 * the lazy reduction advantage of 5x52 on both JVM and native.
 */
#include "field.h"
#include "field_asm.h"
#include <string.h>

#define FIELD_C 0x1000003D1ULL

/* ==================== 8-limb product computation ==================== */

/*
 * Compute 512-bit product of two 256-bit numbers in 4x64 representation.
 * Output: 8 limbs in little-endian. Uses __int128 for 64x64->128 products.
 *
 * The key insight: we CAN'T accumulate multiple 128-bit products in a single
 * uint128 because 4 products overflow (4 * 2^128 > 2^128). Instead, we compute
 * each column separately and propagate carries explicitly.
 */
#if HAVE_INT128

void mul_wide(uint64_t out[8], const uint64_t a[4], const uint64_t b[4]) {
    /*
     * Schoolbook 4x4 multiplication into 8 limbs. Uses a row-based approach:
     * multiply each a[i] by the full b[0..3] vector and accumulate into out.
     * This avoids the column-based carry overflow problem.
     */
    uint128_t acc;

    /* Row 0: out += a[0] * b */
    acc = (uint128_t)a[0] * b[0];
    out[0] = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)a[0] * b[1];
    out[1] = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)a[0] * b[2];
    out[2] = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)a[0] * b[3];
    out[3] = (uint64_t)acc;
    out[4] = (uint64_t)(acc >> 64);
    out[5] = out[6] = out[7] = 0;

    /* Row 1: out[1..5] += a[1] * b */
    acc = (uint128_t)out[1] + (uint128_t)a[1] * b[0];
    out[1] = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)out[2] + (uint128_t)a[1] * b[1];
    out[2] = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)out[3] + (uint128_t)a[1] * b[2];
    out[3] = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)out[4] + (uint128_t)a[1] * b[3];
    out[4] = (uint64_t)acc;
    out[5] = (uint64_t)(acc >> 64);

    /* Row 2: out[2..6] += a[2] * b */
    acc = (uint128_t)out[2] + (uint128_t)a[2] * b[0];
    out[2] = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)out[3] + (uint128_t)a[2] * b[1];
    out[3] = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)out[4] + (uint128_t)a[2] * b[2];
    out[4] = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)out[5] + (uint128_t)a[2] * b[3];
    out[5] = (uint64_t)acc;
    out[6] = (uint64_t)(acc >> 64);

    /* Row 3: out[3..7] += a[3] * b */
    acc = (uint128_t)out[3] + (uint128_t)a[3] * b[0];
    out[3] = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)out[4] + (uint128_t)a[3] * b[1];
    out[4] = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)out[5] + (uint128_t)a[3] * b[2];
    out[5] = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)out[6] + (uint128_t)a[3] * b[3];
    out[6] = (uint64_t)acc;
    out[7] = (uint64_t)(acc >> 64);
}

/*
 * Reduce a 512-bit value (8 limbs) modulo p.
 * Uses: 2^256 ≡ C (mod p) where C = 0x1000003D1.
 * Two reduction rounds: first folds hi[0..3] into lo[0..3] using C,
 * second handles any remaining overflow.
 */
void reduce_wide(secp256k1_fe *r, const uint64_t w[8]) {
    uint128_t acc;

    /* Round 1: result = w[0..3] + w[4..7] * C */
    acc = (uint128_t)w[0] + (uint128_t)w[4] * FIELD_C;
    r->d[0] = (uint64_t)acc;
    acc >>= 64;

    acc += (uint128_t)w[1] + (uint128_t)w[5] * FIELD_C;
    r->d[1] = (uint64_t)acc;
    acc >>= 64;

    acc += (uint128_t)w[2] + (uint128_t)w[6] * FIELD_C;
    r->d[2] = (uint64_t)acc;
    acc >>= 64;

    acc += (uint128_t)w[3] + (uint128_t)w[7] * FIELD_C;
    r->d[3] = (uint64_t)acc;
    uint64_t carry = (uint64_t)(acc >> 64);

    /* Round 2+: fold remaining carry through C. Looped for correctness
     * against any reachable input (see fe_mul for the detailed bound). */
    while (carry) {
        acc = (uint128_t)r->d[0] + (uint128_t)carry * FIELD_C;
        r->d[0] = (uint64_t)acc;
        uint64_t c1 = (uint64_t)(acc >> 64);
        if (c1) {
            uint64_t s = r->d[1] + c1;
            uint64_t cc = (s < c1) ? 1 : 0;
            r->d[1] = s;
            if (cc) {
                s = r->d[2] + 1;
                cc = (s == 0) ? 1 : 0;
                r->d[2] = s;
                if (cc) {
                    s = r->d[3] + 1;
                    r->d[3] = s;
                    carry = (s == 0) ? 1 : 0;
                    continue;
                }
            }
        }
        carry = 0;
    }

    /* No fe_normalize — lazy. Output is in [0, 2^256), possibly in [P, P+C).
     * This is safe: mul/add/sub all handle unreduced inputs.
     * Only neg/half/isZero/cmp/toBytes need explicit normalize. */
}

#if FE_MUL_ASM
/* fe_mul and fe_sqr are static inline in field.h when ASM is available.
 * They get inlined directly into gej_double/gej_add_ge callers,
 * eliminating ~9 function call boundaries per doublePoint. */
#else
void fe_mul(secp256k1_fe *r, const secp256k1_fe *a, const secp256k1_fe *b) {
#if HAVE_INT128
    /* Inline mul + reduce to avoid function call overhead and enable
     * the compiler to keep intermediates in registers. */
    uint64_t a0=a->d[0], a1=a->d[1], a2=a->d[2], a3=a->d[3];
    uint64_t b0=b->d[0], b1=b->d[1], b2=b->d[2], b3=b->d[3];
    uint128_t acc;
    uint64_t lo0, lo1, lo2, lo3, hi0, hi1, hi2, hi3;

    /* 4x4 schoolbook product (row-based, no overflow) */
    acc = (uint128_t)a0*b0;
    lo0 = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)a0*b1;
    lo1 = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)a0*b2;
    lo2 = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)a0*b3;
    lo3 = (uint64_t)acc; hi0 = (uint64_t)(acc>>64);

    acc = (uint128_t)lo1 + (uint128_t)a1*b0;
    lo1 = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)lo2 + (uint128_t)a1*b1;
    lo2 = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)lo3 + (uint128_t)a1*b2;
    lo3 = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)hi0 + (uint128_t)a1*b3;
    hi0 = (uint64_t)acc; hi1 = (uint64_t)(acc>>64);

    acc = (uint128_t)lo2 + (uint128_t)a2*b0;
    lo2 = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)lo3 + (uint128_t)a2*b1;
    lo3 = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)hi0 + (uint128_t)a2*b2;
    hi0 = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)hi1 + (uint128_t)a2*b3;
    hi1 = (uint64_t)acc; hi2 = (uint64_t)(acc>>64);

    acc = (uint128_t)lo3 + (uint128_t)a3*b0;
    lo3 = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)hi0 + (uint128_t)a3*b1;
    hi0 = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)hi1 + (uint128_t)a3*b2;
    hi1 = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)hi2 + (uint128_t)a3*b3;
    hi2 = (uint64_t)acc; hi3 = (uint64_t)(acc>>64);

    /* Reduce: lo + hi * C */
    acc = (uint128_t)lo0 + (uint128_t)hi0 * FIELD_C;
    r->d[0] = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)lo1 + (uint128_t)hi1 * FIELD_C;
    r->d[1] = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)lo2 + (uint128_t)hi2 * FIELD_C;
    r->d[2] = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)lo3 + (uint128_t)hi3 * FIELD_C;
    r->d[3] = (uint64_t)acc;
    uint64_t carry = (uint64_t)(acc >> 64);
    /* Final fold: carry * C back into the low limbs. In theory two rounds
     * suffice because carry ≤ ~2^34 on the first pass and carry*C < 2^67
     * produces at most a 3-bit secondary carry. The loop is cheap and makes
     * the invariant "output < 2^256" hold for any reachable inputs — not
     * only from fe_mul itself but also from chained lazy fe_add results. */
    while (carry) {
        acc = (uint128_t)r->d[0] + (uint128_t)carry * FIELD_C;
        r->d[0] = (uint64_t)acc;
        uint64_t c1 = (uint64_t)(acc >> 64);
        if (c1) {
            uint64_t s = r->d[1] + c1;
            uint64_t cc = (s < c1) ? 1 : 0;
            r->d[1] = s;
            if (cc) {
                s = r->d[2] + 1;
                cc = (s == 0) ? 1 : 0;
                r->d[2] = s;
                if (cc) {
                    s = r->d[3] + 1;
                    r->d[3] = s;
                    carry = (s == 0) ? 1 : 0;
                    continue;
                }
            }
        }
        carry = 0;
    }
    /* No fe_normalize — lazy. Output is in [0, 2^256), possibly in [P, P+C).
     * This is safe: mul/add/sub all handle unreduced inputs.
     * Only neg/half/isZero/cmp/toBytes need explicit normalize. */
#else
    uint64_t w[8];
    mul_wide(w, a->d, b->d);
    reduce_wide(r, w);
#endif
}

/* fe_sqr is now defined as static inline in field.h (10-mul dedicated
 * squaring using __int128). This routes through fe_sqr_inline to save 6
 * multiplications per squaring compared to fe_mul(a, a). */
#endif /* !FE_MUL_ASM */

#else /* Portable fallback (no HAVE_INT128) */

static inline void mul64(uint64_t *hi, uint64_t *lo, uint64_t a, uint64_t b) {
    uint64_t a_lo = a & 0xFFFFFFFF, a_hi = a >> 32;
    uint64_t b_lo = b & 0xFFFFFFFF, b_hi = b >> 32;
    uint64_t ll = a_lo * b_lo, lh = a_lo * b_hi, hl = a_hi * b_lo, hh = a_hi * b_hi;
    uint64_t mid = (ll >> 32) + (lh & 0xFFFFFFFF) + (hl & 0xFFFFFFFF);
    *lo = (ll & 0xFFFFFFFF) | (mid << 32);
    *hi = hh + (lh >> 32) + (hl >> 32) + (mid >> 32);
}

void fe_mul(secp256k1_fe *r, const secp256k1_fe *a, const secp256k1_fe *b) {
    uint64_t w[8] = {0};
    for (int i = 0; i < 4; i++) {
        uint64_t carry = 0;
        for (int j = 0; j < 4; j++) {
            uint64_t hi, lo;
            mul64(&hi, &lo, a->d[i], b->d[j]);
            uint64_t sum = w[i+j] + lo + carry;
            carry = hi + (sum < w[i+j] ? 1 : 0);
            w[i+j] = sum;
        }
        w[i+4] += carry;
    }
    /* Reduce: w[0..3] + w[4..7] * C */
    uint64_t c_lo, c_hi;
    uint64_t carry2 = 0;
    for (int i = 0; i < 4; i++) {
        mul64(&c_hi, &c_lo, w[i+4], FIELD_C);
        uint64_t sum = w[i] + c_lo + carry2;
        carry2 = c_hi + (sum < w[i] ? 1 : 0);
        r->d[i] = sum;
    }
    while (carry2) {
        mul64(&c_hi, &c_lo, carry2, FIELD_C);
        uint64_t sum = r->d[0] + c_lo;
        uint64_t new_carry = (sum < c_lo) ? 1 : 0;
        r->d[0] = sum;
        uint64_t s1 = r->d[1] + c_hi + new_carry;
        uint64_t nc1 = (s1 < c_hi) || (new_carry && s1 == c_hi) ? 1 : 0;
        r->d[1] = s1;
        if (nc1) {
            uint64_t s2 = r->d[2] + 1;
            uint64_t nc2 = (s2 == 0) ? 1 : 0;
            r->d[2] = s2;
            if (nc2) {
                uint64_t s3 = r->d[3] + 1;
                r->d[3] = s3;
                carry2 = (s3 == 0) ? 1 : 0;
                continue;
            }
        }
        carry2 = 0;
    }
    /* No fe_normalize — lazy. Output is in [0, 2^256), possibly in [P, P+C).
     * This is safe: mul/add/sub all handle unreduced inputs.
     * Only neg/half/isZero/cmp/toBytes need explicit normalize. */
}

/* Portable fallback squaring: use fe_mul(a, a) because the 10-mul inline path
 * requires __int128 and this branch is for compilers without it. */
void fe_sqr(secp256k1_fe *r, const secp256k1_fe *a) { fe_mul(r, a, a); }

#endif /* HAVE_INT128 */

/* ==================== Repeated squaring ==================== */

static void fe_sqr_n(secp256k1_fe *r, const secp256k1_fe *a, int n) {
    *r = *a;
    for (int i = 0; i < n; i++) fe_sqr(r, r);
}

/* ==================== Inversion (Fermat: a^(p-2)) ==================== */

void fe_inv(secp256k1_fe *r, const secp256k1_fe *a) {
    secp256k1_fe x2, x3, x6, x9, x11, x22, x44, x88, x176, x220, x223;
    fe_sqr(&x2, a); fe_mul(&x2, &x2, a);
    fe_sqr(&x3, &x2); fe_mul(&x3, &x3, a);
    fe_sqr_n(&x6, &x3, 3); fe_mul(&x6, &x6, &x3);
    fe_sqr_n(&x9, &x6, 3); fe_mul(&x9, &x9, &x3);
    fe_sqr_n(&x11, &x9, 2); fe_mul(&x11, &x11, &x2);
    fe_sqr_n(&x22, &x11, 11); fe_mul(&x22, &x22, &x11);
    fe_sqr_n(&x44, &x22, 22); fe_mul(&x44, &x44, &x22);
    fe_sqr_n(&x88, &x44, 44); fe_mul(&x88, &x88, &x44);
    fe_sqr_n(&x176, &x88, 88); fe_mul(&x176, &x176, &x88);
    fe_sqr_n(&x220, &x176, 44); fe_mul(&x220, &x220, &x44);
    fe_sqr_n(&x223, &x220, 3); fe_mul(&x223, &x223, &x3);
    fe_sqr_n(r, &x223, 23); fe_mul(r, r, &x22);
    fe_sqr_n(r, r, 5); fe_mul(r, r, a);
    fe_sqr_n(r, r, 3); fe_mul(r, r, &x2);
    fe_sqr_n(r, r, 2); fe_mul(r, r, a);
}

/* ==================== Square root ==================== */

int fe_sqrt(secp256k1_fe *r, const secp256k1_fe *a) {
    secp256k1_fe x2, x3, x6, x9, x11, x22, x44, x88, x176, x220, x223, check;
    fe_sqr(&x2, a); fe_mul(&x2, &x2, a);
    fe_sqr(&x3, &x2); fe_mul(&x3, &x3, a);
    fe_sqr_n(&x6, &x3, 3); fe_mul(&x6, &x6, &x3);
    fe_sqr_n(&x9, &x6, 3); fe_mul(&x9, &x9, &x3);
    fe_sqr_n(&x11, &x9, 2); fe_mul(&x11, &x11, &x2);
    fe_sqr_n(&x22, &x11, 11); fe_mul(&x22, &x22, &x11);
    fe_sqr_n(&x44, &x22, 22); fe_mul(&x44, &x44, &x22);
    fe_sqr_n(&x88, &x44, 44); fe_mul(&x88, &x88, &x44);
    fe_sqr_n(&x176, &x88, 88); fe_mul(&x176, &x176, &x88);
    fe_sqr_n(&x220, &x176, 44); fe_mul(&x220, &x220, &x44);
    fe_sqr_n(&x223, &x220, 3); fe_mul(&x223, &x223, &x3);
    fe_sqr_n(r, &x223, 23); fe_mul(r, r, &x22);
    fe_sqr_n(r, r, 6); fe_mul(r, r, &x2);
    fe_sqr_n(r, r, 2);
    fe_sqr(&check, r);
    return fe_equal(&check, a);
}

/* ==================== Half ==================== */

void fe_half(secp256k1_fe *r, const secp256k1_fe *a) {
    /* Branchless: mask = all-1s if odd, all-0s if even.
     * Conditionally add P before shifting. Avoids normalization. */
    uint64_t mask = -(a->d[0] & 1); /* 0xFFF...F if odd, 0 if even */
    uint64_t p0 = 0xFFFFFFFEFFFFFC2FULL & mask;
    /* P[1..3] = 0xFFFF...FFFF, so P[i] & mask = mask */

    uint64_t s0 = a->d[0] + p0;
    uint64_t c0 = (s0 < a->d[0]) ? 1ULL : 0ULL;
    uint64_t s1 = a->d[1] + mask + c0;
    uint64_t c1 = (s1 < a->d[1]) || (c0 && s1 == a->d[1]) ? 1ULL : 0ULL;
    uint64_t s2 = a->d[2] + mask + c1;
    uint64_t c2 = (s2 < a->d[2]) || (c1 && s2 == a->d[2]) ? 1ULL : 0ULL;
    uint64_t s3 = a->d[3] + mask + c2;
    uint64_t c3 = (s3 < a->d[3]) || (c2 && s3 == a->d[3]) ? 1ULL : 0ULL;

    r->d[0] = (s0 >> 1) | (s1 << 63);
    r->d[1] = (s1 >> 1) | (s2 << 63);
    r->d[2] = (s2 >> 1) | (s3 << 63);
    r->d[3] = (s3 >> 1) | (c3 << 63);
}

/* ==================== Serialization ==================== */

void fe_to_bytes(uint8_t *out32, const secp256k1_fe *a) {
    secp256k1_fe t = *a;
    fe_normalize_full(&t);
    for (int i = 0; i < 4; i++) {
        uint64_t v = t.d[3 - i];
        for (int j = 0; j < 8; j++)
            out32[i * 8 + j] = (uint8_t)(v >> ((7 - j) * 8));
    }
}

int fe_from_bytes(secp256k1_fe *r, const uint8_t *in32) {
    for (int i = 0; i < 4; i++) {
        uint64_t v = 0;
        for (int j = 0; j < 8; j++)
            v = (v << 8) | in32[i * 8 + j];
        r->d[3 - i] = v;
    }
    return 1;
}

int fe_cmp(const secp256k1_fe *a, const secp256k1_fe *b) {
    /* Compare raw limb values without normalization.
     * fe_normalize reduces values in [P, 2^256) to [0, 2^32+977),
     * which would turn P itself into 0 and break comparisons against P. */
    for (int i = 3; i >= 0; i--) {
        if (a->d[i] < b->d[i]) return -1;
        if (a->d[i] > b->d[i]) return 1;
    }
    return 0;
}
