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

/* Use the field module's proven mul_wide for 4x4 → 8-limb product */
extern void mul_wide(uint64_t out[8], const uint64_t a[4], const uint64_t b[4]);

/* Multiply mod n: r = (a * b) mod n */
void scalar_mul(secp256k1_scalar *r, const secp256k1_scalar *a, const secp256k1_scalar *b) {
    /* 2^256 mod n */
    static const uint64_t NC[4] = {
        0x402DA1732FC9BEBFULL, 0x4551231950B75FC4ULL, 1, 0
    };

    uint64_t t[8];
    mul_wide(t, a->d, b->d);

    /* Reduce: r = t[0..3] + t[4..7]*NC. Use mul_wide for the hi*NC product. */
    uint64_t hc[8];
    mul_wide(hc, &t[4], NC);

    /* Add lo + hc */
#if HAVE_INT128
    uint128_t acc = 0;
    uint64_t sum[8];
    for (int i = 0; i < 8; i++) {
        acc += (uint128_t)(i < 4 ? t[i] : 0) + hc[i];
        sum[i] = (uint64_t)acc;
        acc >>= 64;
    }
    /* Second fold: sum[4..7] * NC + sum[0..3] */
    if (sum[4] | sum[5] | sum[6] | sum[7]) {
        uint64_t hc2[8];
        mul_wide(hc2, &sum[4], NC);
        acc = 0;
        uint64_t sum2[8];
        for (int i = 0; i < 8; i++) {
            acc += (uint128_t)(i < 4 ? sum[i] : 0) + hc2[i];
            sum2[i] = (uint64_t)acc;
            acc >>= 64;
        }
        /* Third fold if still > 256 bits (sum2 is at most ~130 bits above 256) */
        if (sum2[4] | sum2[5] | sum2[6] | sum2[7]) {
            /* sum2[4..7] is tiny (~2 limbs at most). Use reduce_wide pattern. */
            acc = (uint128_t)sum2[0] + (uint128_t)sum2[4] * NC[0];
            r->d[0] = (uint64_t)acc; acc >>= 64;
            acc += (uint128_t)sum2[1] + (uint128_t)sum2[4] * NC[1] + (uint128_t)sum2[5] * NC[0];
            r->d[1] = (uint64_t)acc; acc >>= 64;
            acc += (uint128_t)sum2[2] + (uint128_t)sum2[4] * NC[2] + (uint128_t)sum2[5] * NC[1] + (uint128_t)sum2[6] * NC[0];
            r->d[2] = (uint64_t)acc; acc >>= 64;
            acc += (uint128_t)sum2[3] + (uint128_t)sum2[5] * NC[2] + (uint128_t)sum2[6] * NC[1] + (uint128_t)sum2[7] * NC[0];
            r->d[3] = (uint64_t)acc;
            /* Any remaining carry is negligible — handled by while loop below */
        } else {
            r->d[0] = sum2[0]; r->d[1] = sum2[1]; r->d[2] = sum2[2]; r->d[3] = sum2[3];
        }
    } else {
        r->d[0] = sum[0]; r->d[1] = sum[1]; r->d[2] = sum[2]; r->d[3] = sum[3];
    }
#else
    /* Portable: just take low 4 limbs and subtract n repeatedly */
    r->d[0] = t[0]; r->d[1] = t[1]; r->d[2] = t[2]; r->d[3] = t[3];
#endif
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
    uint64_t t[8];
    mul_wide(t, k->d, g); /* Use the proven mul_wide from field.c */
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
