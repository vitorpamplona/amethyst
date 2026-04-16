/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * BIP-340 Schnorr signatures for Nostr with fast verification,
 * batch verification, and cached pubkey signing.
 */
#include "secp256k1_internal.h"
#include "field.h"
#include "scalar.h"
#include "point.h"
#include "sha256.h"
#include <string.h>
#include <stdlib.h>

/* ==================== Precomputed BIP-340 Tag Prefixes ==================== */

static uint8_t CHALLENGE_PREFIX[64];
static uint8_t AUX_PREFIX[64];
static uint8_t NONCE_PREFIX[64];
static int prefixes_initialized = 0;

static void init_prefixes(void) {
    if (prefixes_initialized) return;
    uint8_t tag_hash[32];

    secp256k1_sha256_hash(tag_hash, (const uint8_t *)"BIP0340/challenge", 17);
    memcpy(CHALLENGE_PREFIX, tag_hash, 32);
    memcpy(CHALLENGE_PREFIX + 32, tag_hash, 32);

    secp256k1_sha256_hash(tag_hash, (const uint8_t *)"BIP0340/aux", 11);
    memcpy(AUX_PREFIX, tag_hash, 32);
    memcpy(AUX_PREFIX + 32, tag_hash, 32);

    secp256k1_sha256_hash(tag_hash, (const uint8_t *)"BIP0340/nonce", 13);
    memcpy(NONCE_PREFIX, tag_hash, 32);
    memcpy(NONCE_PREFIX + 32, tag_hash, 32);

    prefixes_initialized = 1;
}

/* ==================== Pubkey Decompression Cache ==================== */

#define PUBKEY_CACHE_SIZE 1024
#define PUBKEY_CACHE_MASK (PUBKEY_CACHE_SIZE - 1)

typedef struct {
    uint8_t key_bytes[32];
    secp256k1_fe px;
    secp256k1_fe py;
    int valid;
} cached_pubkey;

static cached_pubkey pubkey_cache[PUBKEY_CACHE_SIZE];

static int cache_slot(const uint8_t *pub32) {
    return ((int)pub32[0] | ((int)pub32[1] << 8)) & PUBKEY_CACHE_MASK;
}

static int lift_x_cached(secp256k1_fe *out_x, secp256k1_fe *out_y, const uint8_t *pub32) {
    int slot = cache_slot(pub32);
    cached_pubkey *c = &pubkey_cache[slot];

    if (c->valid && memcmp(c->key_bytes, pub32, 32) == 0) {
        *out_x = c->px;
        *out_y = c->py;
        return 1;
    }

    secp256k1_fe x;
    fe_from_bytes(&x, pub32);
    if (!point_lift_x(out_x, out_y, &x)) return 0;

    memcpy(c->key_bytes, pub32, 32);
    c->px = *out_x;
    c->py = *out_y;
    c->valid = 1;
    return 1;
}

/* ==================== Library Init ==================== */

void secp256k1c_init(void) {
    ecmult_tables_init();
    init_prefixes();
    memset(pubkey_cache, 0, sizeof(pubkey_cache));
}

/* ==================== Key Operations ==================== */

int secp256k1c_pubkey_create(uint8_t *pub65, const uint8_t *seckey32) {
    secp256k1_scalar sk;
    scalar_from_bytes(&sk, seckey32);
    if (!scalar_is_valid(&sk)) return 0;

    secp256k1_gej rj;
    ecmult_gen(&rj, &sk);

    secp256k1_ge r;
    if (!gej_to_ge(&r, &rj)) return 0;

    point_serialize_uncompressed(pub65, &r);
    return 1;
}

int secp256k1c_pubkey_compress(uint8_t *pub33, const uint8_t *pub65) {
    if (pub65[0] != 0x04) return 0;
    pub33[0] = (pub65[64] & 1) ? 0x03 : 0x02;
    memcpy(pub33 + 1, pub65 + 1, 32);
    return 1;
}

int secp256k1c_seckey_verify(const uint8_t *seckey32) {
    secp256k1_scalar sk;
    scalar_from_bytes(&sk, seckey32);
    return scalar_is_valid(&sk);
}

/* ==================== Schnorr Sign (internal) ==================== */

static int schnorr_sign_internal(
    uint8_t *sig64,
    const uint8_t *msg, size_t msg_len,
    const secp256k1_scalar *d0,
    const uint8_t *pub_x_bytes32,
    int pub_has_even_y,
    const uint8_t *auxrand32
) {
    secp256k1_scalar d;
    uint8_t d_bytes[32];
    secp256k1_sha256 ctx;

    /* Negate d if y is odd */
    if (pub_has_even_y) {
        d = *d0;
    } else {
        scalar_negate(&d, d0);
    }
    scalar_to_bytes(d_bytes, &d);

    /* Compute t = d XOR H(aux) */
    uint8_t t_bytes[32];
    if (auxrand32) {
        uint8_t aux_hash[32];
        secp256k1_tagged_hash_precomputed(aux_hash, AUX_PREFIX, auxrand32, 32);
        for (int i = 0; i < 32; i++) {
            t_bytes[i] = d_bytes[i] ^ aux_hash[i];
        }
    } else {
        memcpy(t_bytes, d_bytes, 32);
    }

    /* Nonce: k0 = H(t || pub || msg) */
    uint8_t rand_hash[32];
    secp256k1_sha256_init(&ctx);
    secp256k1_sha256_update(&ctx, NONCE_PREFIX, 64);
    secp256k1_sha256_update(&ctx, t_bytes, 32);
    secp256k1_sha256_update(&ctx, pub_x_bytes32, 32);
    secp256k1_sha256_update(&ctx, msg, msg_len);
    secp256k1_sha256_finalize(&ctx, rand_hash);

    secp256k1_scalar k0;
    scalar_from_bytes(&k0, rand_hash);
    scalar_reduce(&k0);
    if (scalar_is_zero(&k0)) return 0;

    /* R = k0 * G */
    secp256k1_gej rj;
    ecmult_gen(&rj, &k0);
    secp256k1_ge r;
    if (!gej_to_ge(&r, &rj)) return 0;

    /* Negate k if R.y is odd */
    secp256k1_scalar k;
    if (point_has_even_y(&r.y)) {
        k = k0;
    } else {
        scalar_negate(&k, &k0);
    }

    /* Challenge: e = H(R.x || pub || msg) */
    uint8_t rx_bytes[32], e_hash[32];
    fe_to_bytes(rx_bytes, &r.x);

    secp256k1_sha256_init(&ctx);
    secp256k1_sha256_update(&ctx, CHALLENGE_PREFIX, 64);
    secp256k1_sha256_update(&ctx, rx_bytes, 32);
    secp256k1_sha256_update(&ctx, pub_x_bytes32, 32);
    secp256k1_sha256_update(&ctx, msg, msg_len);
    secp256k1_sha256_finalize(&ctx, e_hash);

    secp256k1_scalar e;
    scalar_from_bytes(&e, e_hash);
    scalar_reduce(&e);

    /* s = k + e*d mod n */
    secp256k1_scalar ed, s;
    scalar_mul(&ed, &e, &d);
    scalar_add(&s, &k, &ed);

    /* Output signature: R.x || s */
    memcpy(sig64, rx_bytes, 32);
    scalar_to_bytes(sig64 + 32, &s);
    return 1;
}

/* ==================== Public Schnorr Sign ==================== */

int secp256k1c_schnorr_sign(
    uint8_t *sig64,
    const uint8_t *msg, size_t msg_len,
    const uint8_t *seckey32,
    const uint8_t *auxrand32
) {
    secp256k1_scalar d0;
    scalar_from_bytes(&d0, seckey32);
    if (!scalar_is_valid(&d0)) return 0;

    /* Derive pubkey */
    secp256k1_gej pj;
    ecmult_gen(&pj, &d0);
    secp256k1_ge p;
    if (!gej_to_ge(&p, &pj)) return 0;

    uint8_t pub_x[32];
    fe_to_bytes(pub_x, &p.x);
    int even_y = point_has_even_y(&p.y);

    return schnorr_sign_internal(sig64, msg, msg_len, &d0, pub_x, even_y, auxrand32);
}

/*
 * Fast signing with pre-computed x-only pubkey.
 * ASSUMES the private key already produces an even-y pubkey (BIP-340 convention).
 * This is the case for Nostr keys managed by KeyPair, which pre-negates if needed.
 * For arbitrary keys, use secp256k1c_schnorr_sign which derives y-parity.
 */
int secp256k1c_schnorr_sign_xonly(
    uint8_t *sig64,
    const uint8_t *msg, size_t msg_len,
    const uint8_t *seckey32,
    const uint8_t *xonly_pub32,
    const uint8_t *auxrand32
) {
    secp256k1_scalar d0;
    scalar_from_bytes(&d0, seckey32);
    if (!scalar_is_valid(&d0)) return 0;

    /* BIP-340 x-only pubkeys always have even y by convention.
     * The caller must ensure the private key produces an even-y pubkey. */
    return schnorr_sign_internal(sig64, msg, msg_len, &d0, xonly_pub32, 1, auxrand32);
}

/* ==================== Schnorr Verify (core) ==================== */

/*
 * Core verification: compute Q = s*G + (-e)*P and check X matches.
 * Returns 1 if x-coordinate matches (in Jacobian: X == r*Z^2).
 * Leaves the Jacobian result for callers that need y-parity.
 */
static int schnorr_verify_core(
    const uint8_t *sig64,
    const uint8_t *msg, size_t msg_len,
    const uint8_t *pub32,
    secp256k1_gej *result_out
) {
    /* Decompress pubkey */
    secp256k1_fe px, py;
    if (!lift_x_cached(&px, &py, pub32)) return 0;

    /* Parse r, s from signature */
    secp256k1_fe r_fe;
    fe_from_bytes(&r_fe, sig64);
    /* Check r < p */
    if (fe_cmp(&r_fe, &FE_P) >= 0) return 0;

    secp256k1_scalar s;
    scalar_from_bytes(&s, sig64 + 32);
    if (scalar_cmp(&s, &SCALAR_N) >= 0) return 0;

    /* Challenge: e = H(R.x || pub || msg) */
    uint8_t e_hash[32];
    secp256k1_sha256 ctx;
    secp256k1_sha256_init(&ctx);
    secp256k1_sha256_update(&ctx, CHALLENGE_PREFIX, 64);
    secp256k1_sha256_update(&ctx, sig64, 32); /* R.x from signature */
    secp256k1_sha256_update(&ctx, pub32, 32);
    secp256k1_sha256_update(&ctx, msg, msg_len);
    secp256k1_sha256_finalize(&ctx, e_hash);

    secp256k1_scalar e;
    scalar_from_bytes(&e, e_hash);
    scalar_reduce(&e);

    /* Q = s*G + (-e)*P via Shamir's trick */
    scalar_negate(&e, &e);
    secp256k1_ge p_aff;
    p_aff.x = px;
    p_aff.y = py;
    ecmult_double_g(result_out, &s, &p_aff, &e);

    if (gej_is_infinity(result_out)) return 0;

    /* Jacobian x-check: X == r*Z^2 (no inversion needed) */
    secp256k1_fe z2, rz2;
    fe_sqr(&z2, &result_out->z);
    fe_mul(&rz2, &r_fe, &z2);
    fe_normalize_full(&rz2);

    secp256k1_fe qx = result_out->x;
    fe_normalize_full(&qx);

    return fe_equal(&qx, &rz2);
}

/* ==================== Public Verify ==================== */

int secp256k1c_schnorr_verify(
    const uint8_t *sig64,
    const uint8_t *msg, size_t msg_len,
    const uint8_t *pub32
) {
    if (!sig64 || !pub32) return 0;

    secp256k1_gej result;
    if (!schnorr_verify_core(sig64, msg, msg_len, pub32, &result)) return 0;

    /* Full BIP-340: check y-parity (requires inversion) */
    secp256k1_ge r_aff;
    if (!gej_to_ge(&r_aff, &result)) return 0;
    return point_has_even_y(&r_aff.y);
}

int secp256k1c_schnorr_verify_fast(
    const uint8_t *sig64,
    const uint8_t *msg, size_t msg_len,
    const uint8_t *pub32
) {
    if (!sig64 || !pub32) return 0;

    secp256k1_gej result;
    return schnorr_verify_core(sig64, msg, msg_len, pub32, &result);
}

/* ==================== Batch Verification ==================== */

int secp256k1c_schnorr_verify_batch(
    const uint8_t *pub32,
    const uint8_t *const *sigs64,
    const uint8_t *const *msgs,
    const size_t *msg_lens,
    size_t count
) {
    if (count == 0) return 1;
    if (count == 1) return secp256k1c_schnorr_verify(sigs64[0], msgs[0], msg_lens[0], pub32);
    if (!pub32) return 0;

    /* Decompress pubkey once */
    secp256k1_fe px, py;
    if (!lift_x_cached(&px, &py, pub32)) return 0;

    /* Accumulators */
    secp256k1_scalar s_sum = SCALAR_ZERO;
    secp256k1_scalar e_sum = SCALAR_ZERO;
    secp256k1_gej r_sum;
    gej_set_infinity(&r_sum);

    for (size_t i = 0; i < count; i++) {
        const uint8_t *sig = sigs64[i];
        const uint8_t *msg = msgs[i];
        size_t msg_len = msg_lens[i];

        if (!sig) return 0;

        /* Parse r, s */
        secp256k1_fe r_fe;
        fe_from_bytes(&r_fe, sig);
        if (fe_cmp(&r_fe, &FE_P) >= 0) return 0;

        secp256k1_scalar s;
        scalar_from_bytes(&s, sig + 32);
        if (scalar_cmp(&s, &SCALAR_N) >= 0) return 0;

        /* Accumulate s */
        scalar_add(&s_sum, &s_sum, &s);

        /* Challenge e_i */
        uint8_t e_hash[32];
        secp256k1_sha256 ctx;
        secp256k1_sha256_init(&ctx);
        secp256k1_sha256_update(&ctx, CHALLENGE_PREFIX, 64);
        secp256k1_sha256_update(&ctx, sig, 32);
        secp256k1_sha256_update(&ctx, pub32, 32);
        secp256k1_sha256_update(&ctx, msg, msg_len);
        secp256k1_sha256_finalize(&ctx, e_hash);

        secp256k1_scalar e;
        scalar_from_bytes(&e, e_hash);
        scalar_reduce(&e);
        scalar_add(&e_sum, &e_sum, &e);

        /* Lift R_i = liftX(r_i) */
        secp256k1_fe rx, ry;
        if (!point_lift_x(&rx, &ry, &r_fe)) return 0;

        /* Accumulate R_sum += R_i */
        if (gej_is_infinity(&r_sum)) {
            gej_set_ge(&r_sum, &(secp256k1_ge){.x = rx, .y = ry});
        } else {
            secp256k1_ge r_aff = {.x = rx, .y = ry};
            secp256k1_gej tmp;
            gej_add_ge(&tmp, &r_sum, &r_aff);
            r_sum = tmp;
        }
    }

    /* Q = s_sum*G + (-e_sum)*P */
    scalar_negate(&e_sum, &e_sum);
    secp256k1_ge p_aff = {.x = px, .y = py};
    secp256k1_gej q;
    ecmult_double_g(&q, &s_sum, &p_aff, &e_sum);

    /* Check Q - R_sum == infinity */
    /* Negate R_sum.y */
    fe_negate(&r_sum.y, &r_sum.y, 1);
    fe_normalize(&r_sum.y);

    secp256k1_gej result;
    gej_add(&result, &q, &r_sum);

    return gej_is_infinity(&result);
}

/* ==================== Tweak Operations ==================== */

int secp256k1c_privkey_tweak_add(uint8_t *result32, const uint8_t *seckey32, const uint8_t *tweak32) {
    secp256k1_scalar a, b, r;
    scalar_from_bytes(&a, seckey32);
    scalar_from_bytes(&b, tweak32);
    scalar_add(&r, &a, &b);
    if (scalar_is_zero(&r) || scalar_cmp(&r, &SCALAR_N) >= 0) return 0;
    scalar_to_bytes(result32, &r);
    return 1;
}

int secp256k1c_pubkey_tweak_mul(uint8_t *result, size_t result_len,
                                 const uint8_t *pubkey, size_t pubkey_len,
                                 const uint8_t *tweak32) {
    secp256k1_ge p;
    if (!point_parse_pubkey(&p, pubkey, pubkey_len)) return 0;

    secp256k1_scalar scalar;
    scalar_from_bytes(&scalar, tweak32);
    if (!scalar_is_valid(&scalar)) return 0;

    secp256k1_gej pj, rj;
    gej_set_ge(&pj, &p);
    ecmult(&rj, &pj, &scalar);

    secp256k1_ge r;
    if (!gej_to_ge(&r, &rj)) return 0;

    if (result_len == 33) {
        point_serialize_compressed(result, &r);
    } else if (result_len == 65) {
        point_serialize_uncompressed(result, &r);
    } else {
        return 0;
    }
    return 1;
}

int secp256k1c_ecdh_xonly(uint8_t *result32, const uint8_t *xonly_pub32, const uint8_t *scalar32) {
    /* Use cached liftX — same peer key in NIP-44 conversations */
    secp256k1_fe px, py;
    if (!lift_x_cached(&px, &py, xonly_pub32)) return 0;

    secp256k1_scalar k;
    scalar_from_bytes(&k, scalar32);
    if (!scalar_is_valid(&k)) return 0;

    secp256k1_gej pj, rj;
    gej_set_ge(&pj, &(secp256k1_ge){.x = px, .y = py});
    ecmult(&rj, &pj, &k);

    secp256k1_fe rx;
    if (!gej_to_ge_x(&rx, &rj)) return 0;
    fe_to_bytes(result32, &rx);
    return 1;
}

/* ==================== SHA-256 Public Wrappers ==================== */

void secp256k1c_sha256(uint8_t *out32, const uint8_t *data, size_t len) {
    secp256k1_sha256_hash(out32, data, len);
}

void secp256k1c_tagged_hash(uint8_t *out32, const char *tag,
                             const uint8_t *msg, size_t msg_len) {
    secp256k1_tagged_hash(out32, tag, msg, msg_len);
}
