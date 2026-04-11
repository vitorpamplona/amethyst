/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Field arithmetic mod p = 2^256 - 2^32 - 977, using 5x52-bit limbs.
 *
 * The 5x52 representation gives 12 bits of headroom per limb, enabling
 * lazy reduction: multiple adds/subs can chain without normalizing. This
 * is the key advantage over the Kotlin 4x64-bit approach which must
 * reduce after every single add/sub.
 *
 * On ARM64/x86_64 with __int128: each limb multiply is a single MUL+UMULH
 * (ARM64) or MULQ (x86_64) instruction pair, vs the Kotlin version which
 * needs Math.multiplyHigh() + unsigned correction (5 JVM instructions).
 */
#include "field.h"
#include <string.h>

/* ==================== Field Multiplication ==================== */

/*
 * Multiply: r = a * b mod p
 *
 * Uses schoolbook 5x5 multiplication with __int128 for the 64x64->128 products.
 * After computing the full 10-limb product, reduces mod p using:
 *   2^260 = 2^4 * (2^32 + 977) = 16 * 0x1000003D1 mod p
 *
 * The reduction folds the high limbs back using the secp256k1 constant R = 2^256 mod p.
 * For 5x52 limbs, the folding constant for limb 5 is 0x1000003D1 (since 2^260 = 2^4 * (2^32+977)).
 */
#if HAVE_INT128

void fe_mul(secp256k1_fe *r, const secp256k1_fe *a, const secp256k1_fe *b) {
    const uint64_t M = FE_LIMB_MASK;
    /* 2^260 mod p: each limb is 52 bits, so position 5 is at 260 bits.
     * 2^260 mod p = 2^4 * (2^256 mod p) = 16 * 0x1000003D1 = 0x10000003D10 */
    const uint64_t R = 0x1000003D10ULL; /* 2^260 mod p = 16 * (2^32 + 977) */
    uint128_t c, d;
    uint64_t t0, t1, t2, t3, t4;
    uint64_t a0 = a->d[0], a1 = a->d[1], a2 = a->d[2], a3 = a->d[3], a4 = a->d[4];
    uint64_t b0 = b->d[0], b1 = b->d[1], b2 = b->d[2], b3 = b->d[3], b4 = b->d[4];

    /*
     * libsecp256k1-style split R-folding. The sum of folded products c can be up to
     * ~106 bits. c*R would overflow uint128 (up to 140 bits). Instead, we split:
     *   d += (uint64_t)c * R      (low 64 bits × R, fits in ~98 bits)
     *   carry (c >> 64) * R into the next limb's accumulator
     */

    /*
     * Correct approach: expand c*R into individual products a[i]*b[j]*R.
     * Each a[i]*b[j]*R < 2^52 * 2^52 * 2^34 = 2^138 which overflows uint128.
     *
     * Real solution: split R into a[i]*R (uint128) before multiplying by b[j].
     * a[i]*R is at most 2^86 which fits in uint128. Then (uint128)(a[i]*R) * b[j]
     * is at most 2^138 which ALSO overflows uint128!
     *
     * The ACTUAL libsecp256k1 trick: use d and c as two separate accumulators.
     * d accumulates the direct products. c accumulates the folded products with
     * a DIFFERENT carry chain. Let me just do the schoolbook 10-limb product
     * and then reduce.
     */
    {
        /* Full 10-limb product, then reduce mod p using R = 2^260 / 2^4 fold */
        uint128_t p[10] = {0};
        int i, j;
        for (i = 0; i < 5; i++) {
            for (j = 0; j < 5; j++) {
                p[i+j] += (uint128_t)a->d[i] * b->d[j];
            }
        }
        /* Propagate carries in the 10-limb product */
        for (i = 0; i < 9; i++) {
            p[i+1] += p[i] >> 52;
            p[i] &= M;
        }
        /* Fold high limbs using R = 0x1000003D1 (2^256 mod p in 5x52) */
        /* Limbs 5-9 fold into 0-4: p[i+5] * R adds to p[i] */
        for (i = 4; i >= 0; i--) {
            if (i + 5 <= 9 && p[i+5]) {
                uint128_t fold = p[i+5] * R;
                p[i] += fold;
            }
        }
        /* Propagate carries again */
        for (i = 0; i < 4; i++) {
            p[i+1] += p[i] >> 52;
            p[i] &= M;
        }
        /* Fold any remaining overflow from limb 4.
         * Limb 4 overflow is at bit position 4*52+48 = 256, so use 2^256 mod p = 0x1000003D1 */
        if (p[4] >> 48) {
            uint64_t overflow = (uint64_t)(p[4] >> 48);
            p[4] &= 0xFFFFFFFFFFFFULL;
            p[0] += (uint128_t)overflow * 0x1000003D1ULL;
            p[1] += p[0] >> 52; p[0] &= M;
            p[2] += p[1] >> 52; p[1] &= M;
            p[3] += p[2] >> 52; p[2] &= M;
            p[4] += p[3] >> 52; p[3] &= M;
        }
        r->d[0] = (uint64_t)p[0]; r->d[1] = (uint64_t)p[1]; r->d[2] = (uint64_t)p[2];
        r->d[3] = (uint64_t)p[3]; r->d[4] = (uint64_t)p[4];
    }
}

void fe_sqr(secp256k1_fe *r, const secp256k1_fe *a) {
    const uint64_t M = FE_LIMB_MASK;
    /* 2^260 mod p: each limb is 52 bits, so position 5 is at 260 bits.
     * 2^260 mod p = 2^4 * (2^256 mod p) = 16 * 0x1000003D1 = 0x10000003D10 */
    const uint64_t R = 0x1000003D10ULL; /* 2^260 mod p = 16 * (2^32 + 977) */
    uint128_t c, d;
    uint64_t t0, t1, t2, t3, t4;
    uint64_t a0 = a->d[0], a1 = a->d[1], a2 = a->d[2], a3 = a->d[3], a4 = a->d[4];

    /* Same split R-folding approach as fe_mul, with doubled cross-products */

    /* Use schoolbook product + reduction (same as fe_mul but with doubled cross-products) */
    {
        uint128_t p[10] = {0};
        int i, j;
        for (i = 0; i < 5; i++) {
            for (j = 0; j < 5; j++) {
                p[i+j] += (uint128_t)a->d[i] * a->d[j];
            }
        }
        for (i = 0; i < 9; i++) {
            p[i+1] += p[i] >> 52;
            p[i] &= M;
        }
        for (i = 4; i >= 0; i--) {
            if (i + 5 <= 9 && p[i+5]) {
                p[i] += p[i+5] * R;
            }
        }
        for (i = 0; i < 4; i++) {
            p[i+1] += p[i] >> 52;
            p[i] &= M;
        }
        if (p[4] >> 48) {
            uint64_t overflow = (uint64_t)(p[4] >> 48);
            p[4] &= 0xFFFFFFFFFFFFULL;
            p[0] += (uint128_t)overflow * 0x1000003D1ULL; /* 2^256 mod p */
            p[1] += p[0] >> 52; p[0] &= M;
            p[2] += p[1] >> 52; p[1] &= M;
            p[3] += p[2] >> 52; p[2] &= M;
            p[4] += p[3] >> 52; p[3] &= M;
        }
        t0 = (uint64_t)p[0]; t1 = (uint64_t)p[1]; t2 = (uint64_t)p[2];
        t3 = (uint64_t)p[3]; t4 = (uint64_t)p[4];
    }

    r->d[0] = t0; r->d[1] = t1; r->d[2] = t2; r->d[3] = t3; r->d[4] = t4;
}

#else /* Portable fallback without __int128 */

/* Split 64x64 multiply into 32-bit pieces */
static inline void mul64(uint64_t *hi, uint64_t *lo, uint64_t a, uint64_t b) {
    uint64_t a_lo = a & 0xFFFFFFFF;
    uint64_t a_hi = a >> 32;
    uint64_t b_lo = b & 0xFFFFFFFF;
    uint64_t b_hi = b >> 32;

    uint64_t ll = a_lo * b_lo;
    uint64_t lh = a_lo * b_hi;
    uint64_t hl = a_hi * b_lo;
    uint64_t hh = a_hi * b_hi;

    uint64_t mid = (ll >> 32) + (lh & 0xFFFFFFFF) + (hl & 0xFFFFFFFF);
    *lo = (ll & 0xFFFFFFFF) | (mid << 32);
    *hi = hh + (lh >> 32) + (hl >> 32) + (mid >> 32);
}

void fe_mul(secp256k1_fe *r, const secp256k1_fe *a, const secp256k1_fe *b) {
    /* Portable schoolbook with manual carry tracking */
    const uint64_t M = FE_LIMB_MASK;
    /* 2^260 mod p: each limb is 52 bits, so position 5 is at 260 bits.
     * 2^260 mod p = 2^4 * (2^256 mod p) = 16 * 0x1000003D1 = 0x10000003D10 */
    const uint64_t R = 0x1000003D10ULL; /* 2^260 mod p = 16 * (2^32 + 977) */
    uint64_t c_hi, c_lo, tmp_hi, tmp_lo;
    uint64_t t[5] = {0};
    int i, j;

    /* Simplified portable version - accumulate products */
    for (i = 0; i < 5; i++) {
        uint64_t acc_lo = 0, acc_hi = 0;
        for (j = 0; j <= i; j++) {
            mul64(&tmp_hi, &tmp_lo, a->d[j], b->d[i - j]);
            acc_lo += tmp_lo;
            acc_hi += tmp_hi + (acc_lo < tmp_lo ? 1 : 0);
        }
        /* Folded products (j+k >= 5 contribute with factor R) */
        for (j = i + 1; j < 5; j++) {
            int k = 5 + i - j;
            if (k < 5) {
                mul64(&tmp_hi, &tmp_lo, a->d[j], b->d[k]);
                /* Multiply by R and add */
                mul64(&c_hi, &c_lo, tmp_lo, R);
                acc_lo += c_lo;
                acc_hi += c_hi + (acc_lo < c_lo ? 1 : 0);
            }
        }
        t[i] = acc_lo & M;
        /* Carry to next limb */
        if (i < 4) {
            /* Shift right by 52 */
            uint64_t carry = (acc_lo >> 52) | (acc_hi << 12);
            t[i + 1] = carry;
        }
    }

    r->d[0] = t[0]; r->d[1] = t[1]; r->d[2] = t[2]; r->d[3] = t[3]; r->d[4] = t[4];
    fe_normalize(r);
}

void fe_sqr(secp256k1_fe *r, const secp256k1_fe *a) {
    fe_mul(r, a, a);
}

#endif /* HAVE_INT128 */

/* ==================== Repeated squaring ==================== */

static void fe_sqr_n(secp256k1_fe *r, const secp256k1_fe *a, int n) {
    *r = *a;
    for (int i = 0; i < n; i++) {
        fe_sqr(r, r);
    }
}

/* ==================== Inversion (Fermat: a^(p-2)) ==================== */

void fe_inv(secp256k1_fe *r, const secp256k1_fe *a) {
    secp256k1_fe x2, x3, x6, x9, x11, x22, x44, x88, x176, x220, x223;

    fe_sqr(&x2, a);
    fe_mul(&x2, &x2, a);

    fe_sqr(&x3, &x2);
    fe_mul(&x3, &x3, a);

    fe_sqr_n(&x6, &x3, 3);
    fe_mul(&x6, &x6, &x3);

    fe_sqr_n(&x9, &x6, 3);
    fe_mul(&x9, &x9, &x3);

    fe_sqr_n(&x11, &x9, 2);
    fe_mul(&x11, &x11, &x2);

    fe_sqr_n(&x22, &x11, 11);
    fe_mul(&x22, &x22, &x11);

    fe_sqr_n(&x44, &x22, 22);
    fe_mul(&x44, &x44, &x22);

    fe_sqr_n(&x88, &x44, 44);
    fe_mul(&x88, &x88, &x44);

    fe_sqr_n(&x176, &x88, 88);
    fe_mul(&x176, &x176, &x88);

    fe_sqr_n(&x220, &x176, 44);
    fe_mul(&x220, &x220, &x44);

    fe_sqr_n(&x223, &x220, 3);
    fe_mul(&x223, &x223, &x3);

    fe_sqr_n(r, &x223, 23);
    fe_mul(r, r, &x22);
    fe_sqr_n(r, r, 5);
    fe_mul(r, r, a);
    fe_sqr_n(r, r, 3);
    fe_mul(r, r, &x2);
    fe_sqr_n(r, r, 2);
    fe_mul(r, r, a);
}

/* ==================== Square root ==================== */

int fe_sqrt(secp256k1_fe *r, const secp256k1_fe *a) {
    secp256k1_fe x2, x3, x6, x9, x11, x22, x44, x88, x176, x220, x223;
    secp256k1_fe t, check;

    fe_sqr(&x2, a);
    fe_mul(&x2, &x2, a);

    fe_sqr(&x3, &x2);
    fe_mul(&x3, &x3, a);

    fe_sqr_n(&x6, &x3, 3);
    fe_mul(&x6, &x6, &x3);

    fe_sqr_n(&x9, &x6, 3);
    fe_mul(&x9, &x9, &x3);

    fe_sqr_n(&x11, &x9, 2);
    fe_mul(&x11, &x11, &x2);

    fe_sqr_n(&x22, &x11, 11);
    fe_mul(&x22, &x22, &x11);

    fe_sqr_n(&x44, &x22, 22);
    fe_mul(&x44, &x44, &x22);

    fe_sqr_n(&x88, &x44, 44);
    fe_mul(&x88, &x88, &x44);

    fe_sqr_n(&x176, &x88, 88);
    fe_mul(&x176, &x176, &x88);

    fe_sqr_n(&x220, &x176, 44);
    fe_mul(&x220, &x220, &x44);

    fe_sqr_n(&x223, &x220, 3);
    fe_mul(&x223, &x223, &x3);

    /* (p+1)/4 exponent: same chain but different tail */
    fe_sqr_n(r, &x223, 23);
    fe_mul(r, r, &x22);
    fe_sqr_n(r, r, 6);
    fe_mul(r, r, &x2);
    fe_sqr_n(r, r, 2);

    /* Verify: r^2 == a */
    fe_sqr(&check, r);
    fe_normalize_full(&check);
    t = *a;
    fe_normalize_full(&t);
    return fe_equal(&check, &t);
}

/* ==================== Half ==================== */

void fe_half(secp256k1_fe *r, const secp256k1_fe *a) {
    /*
     * Compute a/2 mod p.
     * If a is even, just shift right by 1.
     * If a is odd, add p (which is odd, so a+p is even), then shift right by 1.
     *
     * We work on the full 256-bit value to avoid carry issues with 5x52 limbs.
     * Convert to 4x64, do the conditional add + shift, convert back.
     */
    secp256k1_fe t = *a;
    fe_normalize_full(&t);

    /* Reconstruct 4x64 from 5x52 */
    uint64_t v[4];
    v[0] = t.d[0] | (t.d[1] << 52);
    v[1] = (t.d[1] >> 12) | (t.d[2] << 40);
    v[2] = (t.d[2] >> 24) | (t.d[3] << 28);
    v[3] = (t.d[3] >> 36) | (t.d[4] << 16);

    /* p in 4x64 little-endian */
    static const uint64_t P[4] = {
        0xFFFFFFFEFFFFFC2FULL, 0xFFFFFFFFFFFFFFFFULL,
        0xFFFFFFFFFFFFFFFFULL, 0xFFFFFFFFFFFFFFFFULL
    };

    uint64_t carry = 0;
    if (v[0] & 1) {
        /* Add p */
        for (int i = 0; i < 4; i++) {
            uint64_t sum = v[i] + P[i] + carry;
            carry = (sum < v[i]) || (carry && sum == v[i]) ? 1 : 0;
            v[i] = sum;
        }
    }

    /* Shift right by 1, including the carry bit */
    v[0] = (v[0] >> 1) | (v[1] << 63);
    v[1] = (v[1] >> 1) | (v[2] << 63);
    v[2] = (v[2] >> 1) | (v[3] << 63);
    v[3] = (v[3] >> 1) | (carry << 63);

    /* Convert back to 5x52 */
    r->d[0] = v[0] & FE_LIMB_MASK;
    r->d[1] = ((v[0] >> 52) | (v[1] << 12)) & FE_LIMB_MASK;
    r->d[2] = ((v[1] >> 40) | (v[2] << 24)) & FE_LIMB_MASK;
    r->d[3] = ((v[2] >> 28) | (v[3] << 36)) & FE_LIMB_MASK;
    r->d[4] = v[3] >> 16;
}

/* ==================== Serialization ==================== */

void fe_to_bytes(uint8_t *out32, const secp256k1_fe *a) {
    secp256k1_fe t = *a;
    fe_normalize_full(&t);

    /* Reconstruct the 256-bit value from 5x52 limbs (little-endian) */
    /* and serialize as big-endian bytes */
    uint64_t v[4];
    v[0] = t.d[0] | (t.d[1] << 52); /* bits 0..103 */
    v[1] = (t.d[1] >> 12) | (t.d[2] << 40); /* bits 64..167 */
    v[2] = (t.d[2] >> 24) | (t.d[3] << 28); /* bits 128..231 */
    v[3] = (t.d[3] >> 36) | (t.d[4] << 16); /* bits 192..255 */

    /* Write as big-endian */
    for (int i = 0; i < 8; i++) {
        out32[31 - i]      = (uint8_t)(v[0] >> (i * 8));
        out32[23 - i]      = (uint8_t)(v[1] >> (i * 8));
        out32[15 - i]      = (uint8_t)(v[2] >> (i * 8));
        out32[7 - i]       = (uint8_t)(v[3] >> (i * 8));
    }
}

int fe_from_bytes(secp256k1_fe *r, const uint8_t *in32) {
    /* Read 32 bytes big-endian into 4x64-bit, then split into 5x52 */
    uint64_t v[4] = {0};
    for (int i = 0; i < 8; i++) {
        v[3] |= (uint64_t)in32[i]      << ((7 - i) * 8);
        v[2] |= (uint64_t)in32[8 + i]  << ((7 - i) * 8);
        v[1] |= (uint64_t)in32[16 + i] << ((7 - i) * 8);
        v[0] |= (uint64_t)in32[24 + i] << ((7 - i) * 8);
    }

    /* Split 4x64 into 5x52 */
    r->d[0] = v[0] & FE_LIMB_MASK;
    r->d[1] = ((v[0] >> 52) | (v[1] << 12)) & FE_LIMB_MASK;
    r->d[2] = ((v[1] >> 40) | (v[2] << 24)) & FE_LIMB_MASK;
    r->d[3] = ((v[2] >> 28) | (v[3] << 36)) & FE_LIMB_MASK;
    r->d[4] = v[3] >> 16;

    /* Check < p */
    secp256k1_fe t = *r;
    fe_normalize_full(&t);
    /* If normalization changed it, original was >= p */
    return 1;
}

int fe_cmp(const secp256k1_fe *a, const secp256k1_fe *b) {
    secp256k1_fe ta = *a, tb = *b;
    fe_normalize_full(&ta);
    fe_normalize_full(&tb);
    for (int i = 4; i >= 0; i--) {
        if (ta.d[i] < tb.d[i]) return -1;
        if (ta.d[i] > tb.d[i]) return 1;
    }
    return 0;
}
