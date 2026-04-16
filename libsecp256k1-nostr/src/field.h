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

#include "secp256k1_internal.h"

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
 *
 * Normalizes a first (fast — usually a no-op), then computes P - a. When
 * a == 0, P - 0 = P, which a final fe_normalize collapses back to 0. This
 * branch-free form matches the Kotlin FieldP.neg path and avoids the
 * data-dependent early-return the previous version had. */
static inline void fe_negate(secp256k1_fe *r, const secp256k1_fe *a, int m) {
    (void)m;
    secp256k1_fe t = *a;
    fe_normalize(&t);
    uint64_t borrow = 0;
    for (int i = 0; i < 4; i++) {
        uint64_t diff = FE_P.d[i] - t.d[i] - borrow;
        borrow = (FE_P.d[i] < t.d[i] + borrow) || (borrow && t.d[i] == UINT64_MAX) ? 1 : 0;
        r->d[i] = diff;
    }
    /* If a was 0, r is now P; fold back to 0. Normal inputs leave r < P. */
    fe_normalize(r);
}

/* ==================== Function declarations ==================== */

/* Field multiply and square — declared here, defined in field.c.
 * On platforms with ASM (x86_64 MULX, ARM64 CE), fe_mul dispatches
 * to the inline fe_mul_asm which the compiler can inline into callers
 * within the same compilation unit. For cross-unit inlining (point.c
 * calling fe_mul), we rely on LTO or the static inline below. */
#include "field_asm.h"

#if HAVE_INT128
/*
 * Dedicated field squaring using 10 multiplications instead of the 16 that
 * an unspecialized 4x4 schoolbook mul would use. Exploits symmetry
 * a[i]*a[j] == a[j]*a[i] with 4 diagonal + 6 doubled cross products.
 *
 * Three-pass structure (same as the Kotlin U256.sqrWide):
 *   Pass 1: compute un-doubled cross-product sum in 6 limbs
 *   Pass 2: shift left by 1 (doubles every cross product simultaneously)
 *   Pass 3: add the 4 diagonal products a[i]^2
 *
 * NOTE: this inline is only wired up for the non-FE_MUL_ASM build path.
 * On x86_64 GCC with BMI2/ADX and on ARM64 GCC, fe_mul_asm's hand-tuned
 * MULX + ADCX/ADOX (or ARM64 MUL/UMULH) carry-chain scheduling beats the
 * __int128 path despite fe_mul_asm using 16 muls. Benchmark measurement
 * (bench_vs_acinq) showed fe_sqr_inline regressed verify/batch-verify by
 * ~10-25% on x86_64 when used in place of fe_mul_asm(r, a, a), so on
 * ASM platforms we keep fe_mul_asm(r, a, a) for fe_sqr. The 10-mul
 * version still helps portable builds (clang, MSVC, non-GCC toolchains).
 */
static inline void fe_sqr_inline(secp256k1_fe *r, const secp256k1_fe *a) {
    uint64_t a0 = a->d[0], a1 = a->d[1], a2 = a->d[2], a3 = a->d[3];
    uint128_t acc;

    /* ===== Pass 1: un-doubled cross-product sum =====
     * Cross products and their column positions:
     *   col 1: a0*a1
     *   col 2: a0*a2
     *   col 3: a0*a3 + a1*a2  (two products in one column)
     *   col 4: a1*a3
     *   col 5: a2*a3
     *
     * Cols 1,2,4,5 each have a single product, so the 128-bit accumulator
     * trick (`acc += new_product; extract low 64`) stays within bounds:
     * after the extract, acc < 2^64, and adding another full 128-bit product
     * gives at most (2^64-1)+(2^128-2^65+1) = 2^128-2^64 which still fits.
     *
     * Col 3 is the only column with two products, so the straightforward
     * `acc += p1; acc += p2` sum can overflow 128 bits. We instead extract
     * the low limb after the first product, then manually fold the second
     * product's low/high halves into the running accumulator with an
     * explicit carry flag.
     */
    acc = (uint128_t)a0 * a1;
    uint64_t x1 = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)a0 * a2;
    uint64_t x2 = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)a0 * a3;
    uint64_t x3_partial = (uint64_t)acc; acc >>= 64;
    /* Fold the second col-3 product (a1*a2) into x3 and the running carry. */
    uint128_t p12 = (uint128_t)a1 * a2;
    uint64_t p12_lo = (uint64_t)p12;
    uint64_t p12_hi = (uint64_t)(p12 >> 64);
    uint64_t x3 = x3_partial + p12_lo;
    uint64_t x3_carry = (x3 < x3_partial) ? 1ULL : 0ULL;
    acc += (uint128_t)p12_hi + x3_carry;
    acc += (uint128_t)a1 * a3;
    uint64_t x4 = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)a2 * a3;
    uint64_t x5 = (uint64_t)acc;
    uint64_t x6 = (uint64_t)(acc >> 64);

    /* ===== Pass 2: shift the cross-product sum left by one bit ===== */
    uint64_t x7 = x6 >> 63;
    x6 = (x6 << 1) | (x5 >> 63);
    x5 = (x5 << 1) | (x4 >> 63);
    x4 = (x4 << 1) | (x3 >> 63);
    x3 = (x3 << 1) | (x2 >> 63);
    x2 = (x2 << 1) | (x1 >> 63);
    x1 = x1 << 1;

    /* ===== Pass 3: add the 4 diagonal products a[i]^2 ===== */
    uint64_t d0lo, d0hi, d1lo, d1hi, d2lo, d2hi, d3lo, d3hi;
    acc = (uint128_t)a0 * a0;
    d0lo = (uint64_t)acc; d0hi = (uint64_t)(acc >> 64);
    acc = (uint128_t)a1 * a1;
    d1lo = (uint64_t)acc; d1hi = (uint64_t)(acc >> 64);
    acc = (uint128_t)a2 * a2;
    d2lo = (uint64_t)acc; d2hi = (uint64_t)(acc >> 64);
    acc = (uint128_t)a3 * a3;
    d3lo = (uint64_t)acc; d3hi = (uint64_t)(acc >> 64);

    /* w[] = full 8-limb product, cross-sum already doubled. */
    uint64_t w0 = d0lo;
    acc = (uint128_t)x1 + d0hi;
    uint64_t w1 = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)x2 + d1lo;
    uint64_t w2 = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)x3 + d1hi;
    uint64_t w3 = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)x4 + d2lo;
    uint64_t w4 = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)x5 + d2hi;
    uint64_t w5 = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)x6 + d3lo;
    uint64_t w6 = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)x7 + d3hi;
    uint64_t w7 = (uint64_t)acc;
    /* Any carry out of w7 is impossible: the full product a^2 < 2^512,
     * and we've accounted for every bit. */

    /* ===== Reduce: r = w[0..3] + w[4..7] * C ===== */
    const uint64_t FC = 0x1000003D1ULL;
    acc = (uint128_t)w0 + (uint128_t)w4 * FC;
    r->d[0] = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)w1 + (uint128_t)w5 * FC;
    r->d[1] = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)w2 + (uint128_t)w6 * FC;
    r->d[2] = (uint64_t)acc; acc >>= 64;
    acc += (uint128_t)w3 + (uint128_t)w7 * FC;
    r->d[3] = (uint64_t)acc;
    uint64_t carry = (uint64_t)(acc >> 64);

    /* Final fold loop (same shape as fe_mul). */
    while (carry) {
        acc = (uint128_t)r->d[0] + (uint128_t)carry * FC;
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
}
#endif /* HAVE_INT128 */

#if FE_MUL_ASM
/* Use the ASM version directly as static inline so point.c can inline it.
 *
 * fe_sqr: on FE_MUL_ASM platforms (x86_64 GCC with BMI2/ADX, ARM64 GCC) the
 * hand-tuned fe_mul_asm uses MULX + dual ADCX/ADOX carry chains that the
 * __int128-based fe_sqr_inline cannot match in practice. Measurement showed
 * that saving 6 multiplications via symmetry doesn't compensate for losing
 * the ASM's optimized carry-chain scheduling and register usage. So on ASM
 * platforms we just square via fe_mul_asm(r, a, a), which is faster in
 * wall-clock terms. The 10-mul fe_sqr_inline is still used for compilers
 * without the ASM path (clang, MSVC, portable fallback). */
static inline void fe_mul(secp256k1_fe *r, const secp256k1_fe *a, const secp256k1_fe *b) {
    fe_mul_asm(r, a, b);
}
static inline void fe_sqr(secp256k1_fe *r, const secp256k1_fe *a) {
    fe_mul_asm(r, a, a);
}
#else
void fe_mul(secp256k1_fe *r, const secp256k1_fe *a, const secp256k1_fe *b);
#if HAVE_INT128
static inline void fe_sqr(secp256k1_fe *r, const secp256k1_fe *a) {
    fe_sqr_inline(r, a);
}
#else
void fe_sqr(secp256k1_fe *r, const secp256k1_fe *a);
#endif
#endif
void fe_inv(secp256k1_fe *r, const secp256k1_fe *a);
int fe_sqrt(secp256k1_fe *r, const secp256k1_fe *a);
void fe_half(secp256k1_fe *r, const secp256k1_fe *a);
void fe_to_bytes(uint8_t *out32, const secp256k1_fe *a);
int fe_from_bytes(secp256k1_fe *r, const uint8_t *in32);
int fe_cmp(const secp256k1_fe *a, const secp256k1_fe *b);

#endif /* SECP256K1_FIELD_H */
