/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Point operations on secp256k1 with comb, GLV+wNAF, Strauss/Shamir.
 */
#include "point.h"
#include <string.h>
#include <stdlib.h>

/* ==================== Generator Point ==================== */

/* G_x = 0x79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798 */
/* G_y = 0x483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8 */
/* 4x64 little-endian */
const secp256k1_ge SECP256K1_G = {
    .x = {{0x59F2815B16F81798ULL, 0x029BFCDB2DCE28D9ULL,
            0x55A06295CE870B07ULL, 0x79BE667EF9DCBBACULL}},
    .y = {{0x9C47D08FFB10D4B8ULL, 0xFD17B448A6855419ULL,
            0x5DA4FBFC0E1108A8ULL, 0x483ADA7726A3C465ULL}}
};

/* GLV beta: cube root of unity mod p (4x64 little-endian) */
/* beta = 0x7AE96A2B657C07106E64479EAC3434E99CF0497512F58995C1396C28719501EE */
static const secp256k1_fe GLV_BETA = {{
    0xC1396C28719501EEULL, 0x9CF0497512F58995ULL,
    0x6E64479EAC3434E9ULL, 0x7AE96A2B657C0710ULL
}};

/* Curve constant b = 7 */
static const secp256k1_fe FE_SEVEN = {{7, 0, 0, 0}};

/* ==================== Precomputed Tables ==================== */

#define COMB_BLOCKS 11
#define COMB_TEETH  6
#define COMB_SPACING 4
#define COMB_POINTS (1 << COMB_TEETH) /* 64 */
#define COMB_TABLE_SIZE (COMB_BLOCKS * COMB_POINTS) /* 704 */

#define WINDOW_G 12
#define G_TABLE_SIZE (1 << (WINDOW_G - 2)) /* 1024 */

static secp256k1_ge comb_table[COMB_TABLE_SIZE];
static secp256k1_ge g_odd_table[G_TABLE_SIZE];
static secp256k1_ge g_lam_table[G_TABLE_SIZE];
static int tables_initialized = 0;

/* ==================== P-side wNAF Table Cache ==================== */
/*
 * Cache P-side affine wNAF tables keyed by pubkey x-coordinate.
 * In Nostr, the same pubkeys are verified repeatedly (feed from one author).
 * Building the P-side table costs ~437 field ops (~27% of verify). Caching
 * skips this entirely on repeat verifications.
 *
 * 1024 entries × 16 AffinePoints × 64 bytes ≈ 1MB. Acceptable for mobile.
 */
#define P_TABLE_CACHE_SIZE 1024
#define P_TABLE_CACHE_MASK (P_TABLE_CACHE_SIZE - 1)
#define P_TABLE_ENTRIES 8 /* 2^(wP-2) for wP=5 */

typedef struct {
    secp256k1_fe px;                          /* cache key: x-coordinate */
    secp256k1_ge p_odd[P_TABLE_ENTRIES];      /* affine odd-multiples of P */
    secp256k1_ge p_lam_odd[P_TABLE_ENTRIES];  /* affine odd-multiples of lambda(P) */
    int valid;
} cached_p_table;

static cached_p_table p_table_cache[P_TABLE_CACHE_SIZE];

static int p_cache_slot(const secp256k1_fe *px) {
    return ((int)(px->d[0] ^ (px->d[1] << 3))) & P_TABLE_CACHE_MASK;
}

/* ==================== Point Operations ==================== */

void gej_set_infinity(secp256k1_gej *r) {
    r->x = FE_ZERO;
    r->y = FE_ONE;
    r->z = FE_ZERO;
    r->infinity = 1;
}

void gej_set_ge(secp256k1_gej *r, const secp256k1_ge *a) {
    r->x = a->x;
    r->y = a->y;
    r->z = FE_ONE;
    r->infinity = 0;
}

int gej_is_infinity(const secp256k1_gej *r) {
    return r->infinity;
}

/* Point doubling: r = 2*p (3M + 4S) using a=0 formula from libsecp256k1 */
void gej_double(secp256k1_gej *r, const secp256k1_gej *p) {
    secp256k1_fe s, l, t, u;

    /* Handle aliasing: if r == p, copy input first */
    secp256k1_gej tmp;
    if (r == p) {
        tmp = *p;
        p = &tmp;
    }

    if (p->infinity) {
        gej_set_infinity(r);
        return;
    }

    /* S = Y^2 */
    fe_sqr(&s, &p->y);

    /* L = (3/2) * X^2 */
    fe_sqr(&l, &p->x);
    secp256k1_fe l3;
    fe_add(&l3, &l, &l);
    fe_add(&l3, &l3, &l);
    fe_half(&l, &l3);

    /* T = -X*S */
    fe_mul(&t, &p->x, &s);
    fe_negate(&t, &t, 1);

    /* X3 = L^2 + 2T */
    fe_sqr(&r->x, &l);
    fe_add_assign(&r->x, &t);
    fe_add_assign(&r->x, &t);
    fe_normalize(&r->x);

    /* Y3 = -(L*(X3+T) + S^2) */
    fe_add(&u, &r->x, &t);
    fe_mul(&u, &l, &u);
    secp256k1_fe s2;
    fe_sqr(&s2, &s);
    fe_add_assign(&u, &s2);
    fe_negate(&r->y, &u, 2);
    fe_normalize(&r->y);

    /* Z3 = Y*Z */
    fe_mul(&r->z, &p->y, &p->z);
    fe_normalize(&r->z);

    r->infinity = 0;
}

/* Mixed addition: r = p + q where q is affine (Z=1). 8M + 3S.
 * Callers in the hot path (ecmult loops) always pass r != p.
 * The aliasing check is kept for safety in table-build code. */
void gej_add_ge(secp256k1_gej *r, const secp256k1_gej *p, const secp256k1_ge *q) {
    secp256k1_gej _tmp;
    if (__builtin_expect(r == p, 0)) { _tmp = *p; p = &_tmp; }
    secp256k1_fe z12, z13, u2, s2, h, h2, i, j, rr, v, t;

    if (p->infinity) {
        gej_set_ge(r, q);
        return;
    }

    /* Z1^2, Z1^3 */
    fe_sqr(&z12, &p->z);
    fe_mul(&z13, &z12, &p->z);

    /* U2 = qx * Z1^2, S2 = qy * Z1^3 */
    fe_mul(&u2, &q->x, &z12);
    fe_mul(&s2, &q->y, &z13);

    /* H = U2 - X1 */
    fe_negate(&t, &p->x, 1);
    fe_add(&h, &u2, &t);

    if (fe_is_zero(&h)) {
        fe_negate(&t, &p->y, 1);
        fe_add(&t, &s2, &t);
        if (fe_is_zero(&t)) { gej_double(r, p); }
        else { gej_set_infinity(r); }
        return;
    }

    fe_add(&h2, &h, &h);
    fe_sqr(&i, &h2);           /* I = (2H)² */
    fe_mul(&j, &h, &i);        /* J = H*I */
    fe_negate(&t, &p->y, 1);
    fe_add(&rr, &s2, &t);
    fe_add(&rr, &rr, &rr);     /* r = 2*(S2-Y1) */
    fe_mul(&v, &p->x, &i);     /* V = X1*I */

    fe_sqr(&r->x, &rr);        /* X3 = r² */
    fe_negate(&t, &j, 1);
    fe_add_assign(&r->x, &t);
    fe_negate(&t, &v, 1);
    fe_add_assign(&r->x, &t);
    fe_add_assign(&r->x, &t);

    fe_negate(&t, &r->x, 5);
    fe_add(&t, &v, &t);        /* V - X3 */
    fe_mul(&r->y, &rr, &t);    /* r*(V-X3) */
    fe_mul(&t, &p->y, &j);
    fe_add(&t, &t, &t);
    fe_negate(&t, &t, 2);
    fe_add_assign(&r->y, &t);  /* - 2*Y1*J */

    fe_mul(&r->z, &p->z, &h);
    fe_add(&r->z, &r->z, &r->z); /* Z3 = 2*Z1*H */

    r->infinity = 0;
}

/* Full Jacobian addition: r = p + q (11M + 5S) */
void gej_add(secp256k1_gej *r, const secp256k1_gej *p, const secp256k1_gej *q) {
    /* Handle aliasing */
    secp256k1_gej tp, tq;
    if (r == p) { tp = *p; p = &tp; }
    if (r == q) { tq = *q; q = &tq; }
    secp256k1_fe z12, z22, u1, u2, s1, s2, h, h2, i, j, rr, v, t;

    if (p->infinity) { *r = *q; return; }
    if (q->infinity) { *r = *p; return; }

    fe_sqr(&z12, &p->z);
    fe_sqr(&z22, &q->z);
    fe_mul(&u1, &p->x, &z22);
    fe_mul(&u2, &q->x, &z12);

    secp256k1_fe z23, z13;
    fe_mul(&z23, &z22, &q->z);
    fe_mul(&z13, &z12, &p->z);
    fe_mul(&s1, &p->y, &z23);
    fe_mul(&s2, &q->y, &z13);

    fe_negate(&t, &u1, 1);
    fe_add(&h, &u2, &t);
    fe_normalize(&h);

    if (fe_is_zero(&h)) {
        fe_negate(&t, &s1, 1);
        fe_add(&t, &s2, &t);
        fe_normalize(&t);
        if (fe_is_zero(&t)) {
            gej_double(r, p);
        } else {
            gej_set_infinity(r);
        }
        return;
    }

    fe_add(&h2, &h, &h);
    fe_sqr(&i, &h2);
    fe_mul(&j, &h, &i);

    fe_negate(&t, &s1, 1);
    fe_add(&rr, &s2, &t);
    fe_add(&rr, &rr, &rr);
    fe_normalize(&rr);

    fe_mul(&v, &u1, &i);

    fe_sqr(&r->x, &rr);
    fe_negate(&t, &j, 1);
    fe_add_assign(&r->x, &t);
    fe_negate(&t, &v, 1);
    fe_add_assign(&r->x, &t);
    fe_add_assign(&r->x, &t);
    fe_normalize(&r->x);

    fe_negate(&t, &r->x, 5);
    fe_add(&t, &v, &t);
    fe_mul(&r->y, &rr, &t);
    fe_mul(&t, &s1, &j);
    fe_add(&t, &t, &t);
    fe_negate(&t, &t, 2);
    fe_add_assign(&r->y, &t);
    fe_normalize(&r->y);

    fe_add(&r->z, &p->z, &q->z);
    fe_sqr(&r->z, &r->z);
    fe_negate(&t, &z12, 1);
    fe_add_assign(&r->z, &t);
    fe_negate(&t, &z22, 1);
    fe_add_assign(&r->z, &t);
    fe_mul(&r->z, &r->z, &h);
    fe_normalize(&r->z);

    r->infinity = 0;
}

/* Convert Jacobian to affine */
int gej_to_ge(secp256k1_ge *r, const secp256k1_gej *p) {
    secp256k1_fe zi, zi2, zi3;
    if (p->infinity) return 0;

    fe_inv(&zi, &p->z);
    fe_sqr(&zi2, &zi);
    fe_mul(&zi3, &zi2, &zi);
    fe_mul(&r->x, &p->x, &zi2);
    fe_mul(&r->y, &p->y, &zi3);
    fe_normalize_full(&r->x);
    fe_normalize_full(&r->y);
    return 1;
}

int gej_to_ge_x(secp256k1_fe *rx, const secp256k1_gej *p) {
    secp256k1_fe zi, zi2;
    if (p->infinity) return 0;

    fe_inv(&zi, &p->z);
    fe_sqr(&zi2, &zi);
    fe_mul(rx, &p->x, &zi2);
    fe_normalize_full(rx);
    return 1;
}

/* ==================== Key/Point Codec ==================== */

int point_lift_x(secp256k1_fe *out_x, secp256k1_fe *out_y, const secp256k1_fe *x) {
    secp256k1_fe x2, x3, c;

    /* y^2 = x^3 + 7 */
    fe_sqr(&x2, x);
    fe_mul(&x3, &x2, x);
    fe_add(&c, &x3, &FE_SEVEN);
    fe_normalize(&c);

    if (!fe_sqrt(out_y, &c)) return 0;
    fe_normalize_full(out_y);

    /* Ensure even y */
    if (fe_is_odd(out_y)) {
        fe_negate(out_y, out_y, 1);
        fe_normalize_full(out_y);
    }

    *out_x = *x;
    return 1;
}

int point_parse_pubkey(secp256k1_ge *r, const uint8_t *pubkey, size_t len) {
    secp256k1_fe x;

    if (len == 33 && (pubkey[0] == 0x02 || pubkey[0] == 0x03)) {
        fe_from_bytes(&x, pubkey + 1);
        secp256k1_fe y;
        if (!point_lift_x(&r->x, &y, &x)) return 0;
        r->y = y;
        /* If prefix is 03 (odd y), negate */
        if (pubkey[0] == 0x03 && !fe_is_odd(&r->y)) {
            fe_negate(&r->y, &r->y, 1);
            fe_normalize_full(&r->y);
        } else if (pubkey[0] == 0x02 && fe_is_odd(&r->y)) {
            fe_negate(&r->y, &r->y, 1);
            fe_normalize_full(&r->y);
        }
        return 1;
    } else if (len == 65 && pubkey[0] == 0x04) {
        fe_from_bytes(&r->x, pubkey + 1);
        fe_from_bytes(&r->y, pubkey + 33);
        return 1;
    }
    return 0;
}

void point_serialize_uncompressed(uint8_t *out65, const secp256k1_ge *p) {
    out65[0] = 0x04;
    fe_to_bytes(out65 + 1, &p->x);
    fe_to_bytes(out65 + 33, &p->y);
}

void point_serialize_compressed(uint8_t *out33, const secp256k1_ge *p) {
    out33[0] = fe_is_odd(&p->y) ? 0x03 : 0x02;
    fe_to_bytes(out33 + 1, &p->x);
}

int point_has_even_y(const secp256k1_fe *y) {
    return !fe_is_odd(y);
}

/* ==================== Batch to Affine (Montgomery's Trick) ==================== */

void batch_to_affine(secp256k1_ge *out, const secp256k1_gej *in, int count) {
    if (count == 0) return;

    secp256k1_fe *cumz = (secp256k1_fe *)malloc((size_t)count * sizeof(secp256k1_fe));
    if (!cumz) return;

    /* Build cumulative Z products, skipping infinity points (Z=0).
     * For infinity points, carry forward the previous cumulative product. */
    int first_valid = -1;
    for (int i = 0; i < count; i++) {
        if (in[i].infinity) {
            /* Infinity: carry previous product (or set to 1 if first) */
            if (i == 0) {
                cumz[0] = FE_ONE;
            } else {
                cumz[i] = cumz[i-1];
            }
            out[i] = (secp256k1_ge){ .x = FE_ZERO, .y = FE_ZERO };
        } else {
            if (first_valid < 0) first_valid = i;
            if (i == 0 || first_valid == i) {
                cumz[i] = in[i].z;
            } else {
                fe_mul(&cumz[i], &cumz[i-1], &in[i].z);
            }
        }
    }

    if (first_valid < 0) { free(cumz); return; } /* All infinity */

    secp256k1_fe inv, zi, zi2, zi3;
    fe_inv(&inv, &cumz[count-1]);

    for (int i = count - 1; i >= 1; i--) {
        if (in[i].infinity) continue; /* Skip, already set to zero */
        fe_mul(&zi, &inv, &cumz[i-1]);
        fe_mul(&inv, &inv, &in[i].z);
        fe_sqr(&zi2, &zi);
        fe_mul(&zi3, &zi2, &zi);
        fe_mul(&out[i].x, &in[i].x, &zi2);
        fe_mul(&out[i].y, &in[i].y, &zi3);
        fe_normalize_full(&out[i].x);
        fe_normalize_full(&out[i].y);
    }
    /* i=0 */
    if (!in[0].infinity) {
        fe_sqr(&zi2, &inv);
        fe_mul(&zi3, &zi2, &inv);
        fe_mul(&out[0].x, &in[0].x, &zi2);
        fe_mul(&out[0].y, &in[0].y, &zi3);
        fe_normalize_full(&out[0].x);
        fe_normalize_full(&out[0].y);
    }

    free(cumz);
}

/* ==================== Table Initialization ==================== */

static void build_g_odd_table(void) {
    secp256k1_gej g, g2;
    gej_set_ge(&g, &SECP256K1_G);
    gej_double(&g2, &g);

    secp256k1_gej *jac = (secp256k1_gej *)malloc(G_TABLE_SIZE * sizeof(secp256k1_gej));
    if (!jac) return;

    jac[0] = g;
    for (int i = 1; i < G_TABLE_SIZE; i++) {
        gej_add(&jac[i], &jac[i-1], &g2);
    }

    batch_to_affine(g_odd_table, jac, G_TABLE_SIZE);

    /* Build lambda(G) table: lambda((x,y)) = (beta*x, y) */
    for (int i = 0; i < G_TABLE_SIZE; i++) {
        fe_mul(&g_lam_table[i].x, &g_odd_table[i].x, &GLV_BETA);
        fe_normalize_full(&g_lam_table[i].x);
        g_lam_table[i].y = g_odd_table[i].y;
    }

    free(jac);
}

static void build_comb_table(void) {
    int num_teeth = COMB_BLOCKS * COMB_TEETH;

    secp256k1_gej *tooth_g = (secp256k1_gej *)malloc((size_t)num_teeth * sizeof(secp256k1_gej));
    if (!tooth_g) return;

    gej_set_ge(&tooth_g[0], &SECP256K1_G);
    for (int i = 1; i < num_teeth; i++) {
        tooth_g[i] = tooth_g[i-1];
        for (int j = 0; j < COMB_SPACING; j++) {
            gej_double(&tooth_g[i], &tooth_g[i]);
        }
    }

    /* Convert tooth points to affine for efficient mixed addition */
    secp256k1_ge *tooth_aff = (secp256k1_ge *)malloc((size_t)num_teeth * sizeof(secp256k1_ge));
    if (!tooth_aff) { free(tooth_g); return; }
    batch_to_affine(tooth_aff, tooth_g, num_teeth);

    /* Build all 2^TEETH combinations per block */
    secp256k1_gej *jac = (secp256k1_gej *)malloc(COMB_TABLE_SIZE * sizeof(secp256k1_gej));
    if (!jac) { free(tooth_g); free(tooth_aff); return; }

    for (int b = 0; b < COMB_BLOCKS; b++) {
        int base = b * COMB_POINTS;
        gej_set_infinity(&jac[base]); /* index 0 = infinity */
        for (int m = 1; m < COMB_POINTS; m++) {
            int changed_bit = __builtin_ctz(m);
            if ((m & (m - 1)) == 0) {
                /* Power of 2: just the tooth point */
                gej_set_ge(&jac[base + m], &tooth_aff[b * COMB_TEETH + changed_bit]);
            } else {
                int prev = m ^ (1 << changed_bit);
                gej_add_ge(&jac[base + m], &jac[base + prev],
                           &tooth_aff[b * COMB_TEETH + changed_bit]);
            }
        }
    }

    batch_to_affine(comb_table, jac, COMB_TABLE_SIZE);

    free(jac);
    free(tooth_aff);
    free(tooth_g);
}

void ecmult_tables_init(void) {
    if (tables_initialized) return;
    build_g_odd_table();
    build_comb_table();
    tables_initialized = 1;
}

/* ==================== Scalar Multiplication ==================== */

/* Helper: test bit n in a scalar */
static inline int scalar_test_bit(const secp256k1_scalar *s, int bit) {
    return (int)((s->d[bit >> 6] >> (bit & 63)) & 1);
}

/* G multiplication using comb method: only 3 doublings + ~43 table lookups.
 * ~2.2x faster than GLV+wNAF (~130 doublings + ~32 additions). */
void ecmult_gen(secp256k1_gej *r, const secp256k1_scalar *scalar) {
    if (scalar_is_zero(scalar)) {
        gej_set_infinity(r);
        return;
    }

    const secp256k1_ge *table = comb_table;
    gej_set_infinity(r);

    for (int comb_off = COMB_SPACING - 1; comb_off >= 0; comb_off--) {
        if (comb_off < COMB_SPACING - 1) {
            secp256k1_gej tmp;
            gej_double(&tmp, r);
            *r = tmp;
        }
        for (int block = 0; block < COMB_BLOCKS; block++) {
            int mask = 0;
            for (int tooth = 0; tooth < COMB_TEETH; tooth++) {
                int bit_pos = (block * COMB_TEETH + tooth) * COMB_SPACING + comb_off;
                if (bit_pos < 256 && scalar_test_bit(scalar, bit_pos)) {
                    mask |= (1 << tooth);
                }
            }
            if (mask != 0) {
                const secp256k1_ge *entry = &table[block * COMB_POINTS + mask];
                secp256k1_gej tmp;
                gej_add_ge(&tmp, r, entry);
                *r = tmp;
            }
        }
    }
}

/* Arbitrary point multiplication using GLV + wNAF-5 */
void ecmult(secp256k1_gej *r, const secp256k1_gej *p, const secp256k1_scalar *scalar) {
    if (scalar_is_zero(scalar) || p->infinity) {
        gej_set_infinity(r);
        return;
    }

    int w = 5;
    int table_size = 1 << (w - 2); /* 8 */

    /* GLV split */
    glv_split split;
    glv_split_scalar(&split, scalar);

    /* wNAF encode */
    int wnaf1[145], wnaf2[145];
    memset(wnaf1, 0, sizeof(wnaf1));
    memset(wnaf2, 0, sizeof(wnaf2));
    int len1 = wnaf_encode(wnaf1, 145, &split.k1, w);
    int len2 = wnaf_encode(wnaf2, 145, &split.k2, w);

    /* P-side tables: check cache first (same cache as ecmult_double_g).
     * For ECDH, the same peer key is used repeatedly (NIP-44 conversations). */
    const secp256k1_ge *p_odd;
    const secp256k1_ge *p_lam_odd;
    int slot = p_cache_slot(&p->x);
    cached_p_table *cached = &p_table_cache[slot];

    if (cached->valid && fe_equal(&cached->px, &p->x)) {
        p_odd = cached->p_odd;
        p_lam_odd = cached->p_lam_odd;
    } else {
        secp256k1_gej p2;
        gej_double(&p2, p);

        secp256k1_gej p_odd_jac[8];
        p_odd_jac[0] = *p;
        for (int i = 1; i < table_size; i++) {
            gej_add(&p_odd_jac[i], &p_odd_jac[i-1], &p2);
        }

        secp256k1_gej p_lam_jac[8];
        for (int i = 0; i < table_size; i++) {
            fe_mul(&p_lam_jac[i].x, &p_odd_jac[i].x, &GLV_BETA);
            p_lam_jac[i].y = p_odd_jac[i].y;
            p_lam_jac[i].z = p_odd_jac[i].z;
            p_lam_jac[i].infinity = 0;
        }

        batch_to_affine(cached->p_odd, p_odd_jac, table_size);
        batch_to_affine(cached->p_lam_odd, p_lam_jac, table_size);
        cached->px = p->x;
        cached->valid = 1;

        p_odd = cached->p_odd;
        p_lam_odd = cached->p_lam_odd;
    }

    /* Find highest non-zero digit */
    int bits = (len1 > len2) ? len1 : len2;
    if (bits == 0) bits = 1;

    gej_set_infinity(r);

    for (int i = bits - 1; i >= 0; i--) {
        gej_double(r, r);

        int d;

        /* Stream 1: k1 * P */
        d = (i < 145) ? wnaf1[i] : 0;
        if (d != 0) {
            int idx = (d > 0 ? d : -d) / 2;
            secp256k1_ge pt = p_odd[idx];
            if ((d < 0) ^ split.neg_k1) {
                fe_negate(&pt.y, &pt.y, 1);
                fe_normalize(&pt.y);
            }
            gej_add_ge(r, r, &pt);
        }

        /* Stream 2: k2 * lambda(P) */
        d = (i < 145) ? wnaf2[i] : 0;
        if (d != 0) {
            int idx = (d > 0 ? d : -d) / 2;
            secp256k1_ge pt = p_lam_odd[idx];
            if ((d < 0) ^ split.neg_k2) {
                fe_negate(&pt.y, &pt.y, 1);
                fe_normalize(&pt.y);
            }
            gej_add_ge(r, r, &pt);
        }
    }
}

/* Dual scalar multiplication: r = s*G + e*P (Strauss + GLV) */
void ecmult_double_g(secp256k1_gej *r, const secp256k1_scalar *s_scalar,
                     const secp256k1_ge *p, const secp256k1_scalar *e_scalar) {
    int wP = 5;
    int p_table_size = 1 << (wP - 2); /* 8 */

    /* GLV split both scalars */
    glv_split s_split, e_split;
    glv_split_scalar(&s_split, s_scalar);
    glv_split_scalar(&e_split, e_scalar);

    /* wNAF encode all 4 half-scalars */
    int wnaf_s1[145], wnaf_s2[145], wnaf_e1[145], wnaf_e2[145];
    memset(wnaf_s1, 0, sizeof(wnaf_s1));
    memset(wnaf_s2, 0, sizeof(wnaf_s2));
    memset(wnaf_e1, 0, sizeof(wnaf_e1));
    memset(wnaf_e2, 0, sizeof(wnaf_e2));
    wnaf_encode(wnaf_s1, 145, &s_split.k1, WINDOW_G);
    wnaf_encode(wnaf_s2, 145, &s_split.k2, WINDOW_G);
    wnaf_encode(wnaf_e1, 145, &e_split.k1, wP);
    wnaf_encode(wnaf_e2, 145, &e_split.k2, wP);

    /* P-side tables: check cache first, build only on miss */
    const secp256k1_ge *p_odd;
    const secp256k1_ge *p_lam_odd;
    int slot = p_cache_slot(&p->x);
    cached_p_table *cached = &p_table_cache[slot];

    if (cached->valid && fe_equal(&cached->px, &p->x)) {
        /* Cache hit — use cached tables directly */
        p_odd = cached->p_odd;
        p_lam_odd = cached->p_lam_odd;
    } else {
        /* Cache miss — build tables and store in cache */
        secp256k1_gej pj;
        gej_set_ge(&pj, p);
        secp256k1_gej p2j;
        gej_double(&p2j, &pj);

        secp256k1_gej p_odd_jac[8], p_lam_jac[8];
        p_odd_jac[0] = pj;
        for (int i = 1; i < p_table_size; i++) {
            gej_add(&p_odd_jac[i], &p_odd_jac[i-1], &p2j);
        }
        for (int i = 0; i < p_table_size; i++) {
            fe_mul(&p_lam_jac[i].x, &p_odd_jac[i].x, &GLV_BETA);
            p_lam_jac[i].y = p_odd_jac[i].y;
            p_lam_jac[i].z = p_odd_jac[i].z;
            p_lam_jac[i].infinity = 0;
        }

        batch_to_affine(cached->p_odd, p_odd_jac, p_table_size);
        batch_to_affine(cached->p_lam_odd, p_lam_jac, p_table_size);
        cached->px = p->x;
        cached->valid = 1;

        p_odd = cached->p_odd;
        p_lam_odd = cached->p_lam_odd;
    }

    /* G tables are pre-computed */
    const secp256k1_ge *g_odd = g_odd_table;
    const secp256k1_ge *g_lam = g_lam_table;

    /* Find highest non-zero digit across all 4 streams */
    int bits = 129 + WINDOW_G;
    while (bits > 0 && wnaf_s1[bits-1] == 0 && wnaf_s2[bits-1] == 0 &&
           wnaf_e1[bits-1] == 0 && wnaf_e2[bits-1] == 0) {
        bits--;
    }

    gej_set_infinity(r);

    for (int i = bits - 1; i >= 0; i--) {
        gej_double(r, r);

        int d;
        secp256k1_ge pt;

        /* Stream 1: s1 (G-side) */
        d = wnaf_s1[i];
        if (d != 0) {
            int idx = (d > 0 ? d : -d) / 2;
            pt = g_odd[idx];
            if ((d < 0) ^ s_split.neg_k1) {
                fe_negate(&pt.y, &pt.y, 1);
                fe_normalize(&pt.y);
            }
            gej_add_ge(r, r, &pt);
        }

        /* Stream 2: s2 (lambda(G)-side) */
        d = wnaf_s2[i];
        if (d != 0) {
            int idx = (d > 0 ? d : -d) / 2;
            pt = g_lam[idx];
            if ((d < 0) ^ s_split.neg_k2) {
                fe_negate(&pt.y, &pt.y, 1);
                fe_normalize(&pt.y);
            }
            gej_add_ge(r, r, &pt);
        }

        /* Stream 3: e1 (P-side) */
        d = wnaf_e1[i];
        if (d != 0) {
            int idx = (d > 0 ? d : -d) / 2;
            pt = p_odd[idx];
            if ((d < 0) ^ e_split.neg_k1) {
                fe_negate(&pt.y, &pt.y, 1);
                fe_normalize(&pt.y);
            }
            gej_add_ge(r, r, &pt);
        }

        /* Stream 4: e2 (lambda(P)-side) */
        d = wnaf_e2[i];
        if (d != 0) {
            int idx = (d > 0 ? d : -d) / 2;
            pt = p_lam_odd[idx];
            if ((d < 0) ^ e_split.neg_k2) {
                fe_negate(&pt.y, &pt.y, 1);
                fe_normalize(&pt.y);
            }
            gej_add_ge(r, r, &pt);
        }
    }
}
