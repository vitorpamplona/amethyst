/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Elliptic curve point operations on secp256k1: y^2 = x^3 + 7 (mod p).
 *
 * Point arithmetic in Jacobian coordinates with:
 * - Comb method for G multiplication (3 doublings + ~43 lookups)
 * - GLV + wNAF for arbitrary point multiplication
 * - Strauss/Shamir + GLV for dual scalar multiplication (verify)
 * - Batch affine conversion (Montgomery's trick)
 */
#ifndef SECP256K1_POINT_H
#define SECP256K1_POINT_H

#include "field.h"
#include "scalar.h"

/* Generator point G */
extern const secp256k1_ge SECP256K1_G;

/* ==================== Point Operations ==================== */

/* Set Jacobian point to infinity */
void gej_set_infinity(secp256k1_gej *r);

/* Set Jacobian point from affine */
void gej_set_ge(secp256k1_gej *r, const secp256k1_ge *a);

/* Check if point is at infinity */
int gej_is_infinity(const secp256k1_gej *r);

/* Point doubling: out = 2*p (3M + 4S) */
void gej_double(secp256k1_gej *r, const secp256k1_gej *p);

/* Mixed addition: out = p + q (Jacobian + Affine, 8M + 3S) */
void gej_add_ge(secp256k1_gej *r, const secp256k1_gej *p, const secp256k1_ge *q);

/* Full Jacobian addition: out = p + q (11M + 5S) */
void gej_add(secp256k1_gej *r, const secp256k1_gej *p, const secp256k1_gej *q);

/* Convert Jacobian to affine */
int gej_to_ge(secp256k1_ge *r, const secp256k1_gej *p);

/* x-only affine conversion (skip y) */
int gej_to_ge_x(secp256k1_fe *rx, const secp256k1_gej *p);

/* ==================== Scalar Multiplication ==================== */

/* G multiplication using comb method: out = scalar * G */
void ecmult_gen(secp256k1_gej *r, const secp256k1_scalar *scalar);

/* Arbitrary point multiplication using GLV + wNAF: out = scalar * p */
void ecmult(secp256k1_gej *r, const secp256k1_gej *p, const secp256k1_scalar *scalar);

/* Dual scalar multiplication (Strauss + GLV): out = s*G + e*P */
void ecmult_double_g(secp256k1_gej *r, const secp256k1_scalar *s,
                     const secp256k1_ge *p, const secp256k1_scalar *e);

/* ==================== Key/Point Codec ==================== */

/* Decompress x-only pubkey: lift x to (x, y) with even y */
int point_lift_x(secp256k1_fe *out_x, secp256k1_fe *out_y, const secp256k1_fe *x);

/* Parse public key (33 or 65 bytes) into affine point */
int point_parse_pubkey(secp256k1_ge *r, const uint8_t *pubkey, size_t len);

/* Serialize affine point as uncompressed (65 bytes: 04 || x || y) */
void point_serialize_uncompressed(uint8_t *out65, const secp256k1_ge *p);

/* Serialize affine point as compressed (33 bytes: 02/03 || x) */
void point_serialize_compressed(uint8_t *out33, const secp256k1_ge *p);

/* Check if y is even */
int point_has_even_y(const secp256k1_fe *y);

/* ==================== Batch Operations ==================== */

/* Batch convert array of Jacobian points to affine using Montgomery's trick */
void batch_to_affine(secp256k1_ge *out, const secp256k1_gej *in, int count);

/* Initialize precomputed tables (called by secp256k1c_init) */
void ecmult_tables_init(void);

#endif /* SECP256K1_POINT_H */
