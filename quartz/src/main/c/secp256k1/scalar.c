/*
 * Copyright (c) 2025 Vitor Pamplona
 * Scalar arithmetic mod n with GLV decomposition and wNAF encoding.
 */
#include "scalar.h"
#include <string.h>

int scalar_is_zero(const secp256k1_scalar *a) {
    return (a->d[0] | a->d[1] | a->d[2] | a->d[3]) == 0;
}

int scalar_cmp(const secp256k1_scalar *a, const secp256k1_scalar *b) {
    for (int i = 3; i >= 0; i--) {
        if (a->d[i] < b->d[i]) return -1;
        if (a->d[i] > b->d[i]) return 1;
    }
    return 0;
}

int scalar_is_valid(const secp256k1_scalar *a) {
    return !scalar_is_zero(a) && scalar_cmp(a, &SCALAR_N) < 0;
}

/* Helper: add 4x64 with carry, returns overflow */
static int add256(uint64_t *r, const uint64_t *a, const uint64_t *b) {
    uint64_t carry = 0;
    for (int i = 0; i < 4; i++) {
        uint64_t sum = a[i] + b[i] + carry;
        carry = (sum < a[i]) || (carry && sum == a[i]) ? 1 : 0;
        r[i] = sum;
    }
    return (int)carry;
}

/* Helper: sub 4x64 with borrow, returns underflow */
static int sub256(uint64_t *r, const uint64_t *a, const uint64_t *b) {
    uint64_t borrow = 0;
    for (int i = 0; i < 4; i++) {
        uint64_t diff = a[i] - b[i] - borrow;
        borrow = (a[i] < b[i] + borrow) || (borrow && b[i] == UINT64_MAX) ? 1 : 0;
        r[i] = diff;
    }
    return (int)borrow;
}

void scalar_reduce(secp256k1_scalar *r) {
    if (scalar_cmp(r, &SCALAR_N) >= 0) {
        sub256(r->d, r->d, SCALAR_N.d);
    }
}

void scalar_add(secp256k1_scalar *r, const secp256k1_scalar *a, const secp256k1_scalar *b) {
    int carry = add256(r->d, a->d, b->d);
    if (carry) {
        /* Overflow: subtract n. n_complement = 2^256 - n */
        uint64_t nc[4] = {
            ~SCALAR_N.d[0] + 1,
            ~SCALAR_N.d[1] + (!SCALAR_N.d[0] ? 1ULL : 0ULL),
            ~SCALAR_N.d[2] + (!(SCALAR_N.d[0] | SCALAR_N.d[1]) ? 1ULL : 0ULL),
            ~SCALAR_N.d[3]
        };
        add256(r->d, r->d, nc);
    }
    scalar_reduce(r);
}

void scalar_negate(secp256k1_scalar *r, const secp256k1_scalar *a) {
    if (scalar_is_zero(a)) {
        *r = SCALAR_ZERO;
    } else {
        sub256(r->d, SCALAR_N.d, a->d);
    }
}

void scalar_sub(secp256k1_scalar *r, const secp256k1_scalar *a, const secp256k1_scalar *b) {
    secp256k1_scalar neg_b;
    scalar_negate(&neg_b, b);
    scalar_add(r, a, &neg_b);
}

/* Multiply mod n using schoolbook 4x4 -> 8 limb, then Barrett reduction */
void scalar_mul(secp256k1_scalar *r, const secp256k1_scalar *a, const secp256k1_scalar *b) {
    /* Full 512-bit product */
    uint64_t t[8] = {0};

#if HAVE_INT128
    for (int i = 0; i < 4; i++) {
        uint128_t carry = 0;
        for (int j = 0; j < 4; j++) {
            carry += (uint128_t)a->d[i] * b->d[j] + t[i + j];
            t[i + j] = (uint64_t)carry;
            carry >>= 64;
        }
        t[i + 4] = (uint64_t)carry;
    }
#else
    /* Portable fallback */
    for (int i = 0; i < 4; i++) {
        uint64_t carry = 0;
        for (int j = 0; j < 4; j++) {
            uint64_t a_lo = a->d[i] & 0xFFFFFFFF;
            uint64_t a_hi = a->d[i] >> 32;
            uint64_t b_lo = b->d[j] & 0xFFFFFFFF;
            uint64_t b_hi = b->d[j] >> 32;

            uint64_t ll = a_lo * b_lo;
            uint64_t lh = a_lo * b_hi;
            uint64_t hl = a_hi * b_lo;
            uint64_t hh = a_hi * b_hi;

            uint64_t mid = (ll >> 32) + (lh & 0xFFFFFFFF) + (hl & 0xFFFFFFFF);
            uint64_t lo = (ll & 0xFFFFFFFF) | (mid << 32);
            uint64_t hi = hh + (lh >> 32) + (hl >> 32) + (mid >> 32);

            uint64_t sum = t[i + j] + lo + carry;
            carry = hi + (sum < t[i + j] ? 1 : 0) + (sum < lo && carry ? 1 : 0);
            t[i + j] = sum;
        }
        t[i + 4] += carry;
    }
#endif

    /* Reduce 512-bit product mod n using 2^256 mod n = MOD_C.
     * For inputs < n (< 2^256), the product is < 2^512.
     * We fold the high 256 bits using: hi * 2^256 ≡ hi * MOD_C (mod n).
     * The result may still exceed 256 bits, so we do a second fold. */
    static const uint64_t MOD_C[4] = {
        0x402DA1732FC9BEBFULL, 0x4551231950B75FC4ULL, 1, 0
    };

#if HAVE_INT128
    {
        /* Fold: r = t[0..3] + t[4..7] * MOD_C, row-based (same as fe mul_wide) */
        uint64_t mid[8] = {0};
        mid[0] = t[0]; mid[1] = t[1]; mid[2] = t[2]; mid[3] = t[3];

        /* Add t[4] * MOD_C at position 0 */
        for (int i = 4; i < 8; i++) {
            if (t[i] == 0) continue;
            uint128_t carry = 0;
            for (int j = 0; j < 4; j++) {
                int k = (i - 4) + j;
                carry += (uint128_t)t[i] * MOD_C[j] + mid[k];
                mid[k] = (uint64_t)carry;
                carry >>= 64;
            }
            /* Propagate carry into higher positions */
            for (int k = (i - 4) + 4; carry && k < 8; k++) {
                carry += mid[k];
                mid[k] = (uint64_t)carry;
                carry >>= 64;
            }
        }

        /* Second fold: mid[4..7] * MOD_C */
        r->d[0] = mid[0]; r->d[1] = mid[1]; r->d[2] = mid[2]; r->d[3] = mid[3];
        if (mid[4] | mid[5] | mid[6] | mid[7]) {
            for (int i = 4; i < 8; i++) {
                if (mid[i] == 0) continue;
                uint128_t carry = 0;
                for (int j = 0; j < 4; j++) {
                    int k = (i - 4) + j;
                    if (k < 4) {
                        carry += (uint128_t)mid[i] * MOD_C[j] + r->d[k];
                        r->d[k] = (uint64_t)carry;
                        carry >>= 64;
                    }
                }
            }
        }
    }
#else
    r->d[0] = t[0]; r->d[1] = t[1]; r->d[2] = t[2]; r->d[3] = t[3];
#endif
    /* Final reduction: subtract n while >= n */
    while (scalar_cmp(r, &SCALAR_N) >= 0) {
        sub256(r->d, r->d, SCALAR_N.d);
    }
}

void scalar_to_bytes(uint8_t *out32, const secp256k1_scalar *a) {
    for (int i = 0; i < 4; i++) {
        uint64_t v = a->d[3 - i];
        for (int j = 0; j < 8; j++) {
            out32[i * 8 + j] = (uint8_t)(v >> ((7 - j) * 8));
        }
    }
}

void scalar_from_bytes(secp256k1_scalar *r, const uint8_t *in32) {
    for (int i = 0; i < 4; i++) {
        uint64_t v = 0;
        for (int j = 0; j < 8; j++) {
            v = (v << 8) | in32[i * 8 + j];
        }
        r->d[3 - i] = v;
    }
}

/* ==================== GLV Decomposition ==================== */

/*
 * GLV constants for secp256k1.
 * Split k into k1, k2 where k = k1 + k2*lambda mod n, |k1|,|k2| ~ 128 bits.
 */

/* Precomputed GLV constants (from Kotlin Fe4 signed longs, converted to uint64) */
static const uint64_t GLV_G1[4] = {
    0xE893209A45DBB031ULL, 0x3DAA8A1471E8CA7FULL,
    0xE86C90E49284EB15ULL, 0x3086D221A7D46BCDULL
};
static const uint64_t GLV_G2[4] = {
    0x1571B4AE8AC47F71ULL, 0x221208AC9DF506C6ULL,
    0x6F547FA90ABFE4C4ULL, 0xE4437ED6010E8828ULL
};

static const secp256k1_scalar GLV_MINUS_B1 = {{
    0x6F547FA90ABFE4C3ULL, 0xE4437ED6010E8828ULL, 0, 0
}};

static const secp256k1_scalar GLV_MINUS_B2 = {{
    0xD765CDA83DB1562CULL, 0x8A280AC50774346DULL,
    0xFFFFFFFFFFFFFFFEULL, 0xFFFFFFFFFFFFFFFFULL
}};

static const secp256k1_scalar GLV_MINUS_LAMBDA = {{
    0xE0CFC810B51283CFULL, 0xC8B936E903BCBCBEULL,
    0x5AD9E3FD77ED9BA3ULL, 0xAC9C52B33FA3CF1FULL
}};

/*
 * mulShift384: compute (k * g) >> 384 for 256x256->512 bit product.
 * Only the upper 128 bits (bits 384..511) are needed for the GLV decomposition.
 */
static void mul_shift384(secp256k1_scalar *r, const secp256k1_scalar *k, const uint64_t g[4]) {
    uint64_t t[8] = {0};
#if HAVE_INT128
    for (int i = 0; i < 4; i++) {
        uint128_t carry = 0;
        for (int j = 0; j < 4; j++) {
            carry += (uint128_t)k->d[i] * g[j] + t[i + j];
            t[i + j] = (uint64_t)carry;
            carry >>= 64;
        }
        t[i + 4] = (uint64_t)carry;
    }
#else
    /* Portable fallback */
    for (int i = 0; i < 4; i++) {
        uint64_t carry = 0;
        for (int j = 0; j < 4; j++) {
            uint64_t a_lo = k->d[i] & 0xFFFFFFFF;
            uint64_t a_hi = k->d[i] >> 32;
            uint64_t b_lo = g[j] & 0xFFFFFFFF;
            uint64_t b_hi = g[j] >> 32;
            uint64_t ll = a_lo * b_lo;
            uint64_t lh = a_lo * b_hi;
            uint64_t hl = a_hi * b_lo;
            uint64_t hh = a_hi * b_hi;
            uint64_t mid = (ll >> 32) + (lh & 0xFFFFFFFF) + (hl & 0xFFFFFFFF);
            uint64_t lo = (ll & 0xFFFFFFFF) | (mid << 32);
            uint64_t hi = hh + (lh >> 32) + (hl >> 32) + (mid >> 32);
            uint64_t sum = t[i + j] + lo + carry;
            carry = hi + (sum < lo ? 1 : 0);
            if (carry < hi) carry++; /* handle double overflow */
            t[i + j] = sum;
        }
        t[i + 4] += carry;
    }
#endif
    /* Extract bits [384..511] = t[6] and t[7], rounded */
    /* Add rounding bit at position 383 */
    uint64_t round = (t[5] >> 63) & 1;
    r->d[0] = t[6] + round;
    r->d[1] = t[7] + (r->d[0] < t[6] ? 1 : 0);
    r->d[2] = 0;
    r->d[3] = 0;
}

void glv_split_scalar(glv_split *out, const secp256k1_scalar *k) {
    secp256k1_scalar c1, c2, t1, t2;

    mul_shift384(&c1, k, GLV_G1);
    mul_shift384(&c2, k, GLV_G2);

    /* r2 = c1 * (-b1) + c2 * (-b2) mod n */
    scalar_mul(&t1, &c1, &GLV_MINUS_B1);
    scalar_mul(&t2, &c2, &GLV_MINUS_B2);
    scalar_add(&out->k2, &t1, &t2);

    /* r1 = r2 * (-lambda) + k mod n */
    scalar_mul(&t1, &out->k2, &GLV_MINUS_LAMBDA);
    scalar_add(&out->k1, &t1, k);

    /* Ensure k1, k2 are in the lower half */
    out->neg_k1 = scalar_cmp(&out->k1, &SCALAR_N_HALF) > 0;
    out->neg_k2 = scalar_cmp(&out->k2, &SCALAR_N_HALF) > 0;
    if (out->neg_k1) scalar_negate(&out->k1, &out->k1);
    if (out->neg_k2) scalar_negate(&out->k2, &out->k2);
}

/* ==================== wNAF Encoding ==================== */

int wnaf_encode(int *wnaf, int max_len, const secp256k1_scalar *s, int w) {
    secp256k1_scalar sc = *s;
    int len = 0;
    int window = 1 << w; /* 2^w */
    int half = window >> 1; /* 2^(w-1) */

    memset(wnaf, 0, (size_t)max_len * sizeof(int));

    while (!scalar_is_zero(&sc) && len < max_len) {
        if (sc.d[0] & 1) {
            int digit = (int)(sc.d[0] & (uint64_t)(window - 1));
            if (digit >= half) digit -= window;
            wnaf[len] = digit;
            /* Subtract digit from sc */
            if (digit > 0) {
                /* sc -= digit */
                uint64_t borrow = 0;
                uint64_t val = (uint64_t)digit;
                for (int i = 0; i < 4; i++) {
                    uint64_t diff = sc.d[i] - val - borrow;
                    borrow = (sc.d[i] < val + borrow) ? 1 : 0;
                    sc.d[i] = diff;
                    val = 0;
                }
            } else if (digit < 0) {
                /* sc += (-digit) */
                uint64_t carry = 0;
                uint64_t val = (uint64_t)(-digit);
                for (int i = 0; i < 4; i++) {
                    uint64_t sum = sc.d[i] + val + carry;
                    carry = (sum < sc.d[i]) ? 1 : 0;
                    sc.d[i] = sum;
                    val = 0;
                }
            }
        } else {
            wnaf[len] = 0;
        }
        /* Right shift by 1 */
        sc.d[0] = (sc.d[0] >> 1) | (sc.d[1] << 63);
        sc.d[1] = (sc.d[1] >> 1) | (sc.d[2] << 63);
        sc.d[2] = (sc.d[2] >> 1) | (sc.d[3] << 63);
        sc.d[3] = sc.d[3] >> 1;
        len++;
    }
    return len;
}
