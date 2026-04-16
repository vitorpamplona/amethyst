/*
 * Copyright (c) 2025 Vitor Pamplona
 * EC point operation tests. Ported from PointTest.kt
 */
#include "test_framework.h"
#include "point.h"

/* Helper: compare affine x coordinates */
static int affine_x_eq(const secp256k1_gej *a, const secp256k1_gej *b) {
    secp256k1_ge ga, gb;
    if (!gej_to_ge(&ga, a) || !gej_to_ge(&gb, b)) return 0;
    return fe_equal(&ga.x, &gb.x);
}

static int affine_xy_eq(const secp256k1_gej *a, const secp256k1_gej *b) {
    secp256k1_ge ga, gb;
    if (!gej_to_ge(&ga, a) || !gej_to_ge(&gb, b)) return 0;
    return fe_equal(&ga.x, &gb.x) && fe_equal(&ga.y, &gb.y);
}

/* ==================== Generator Point ==================== */

static void generator_is_on_curve(void) {
    /* y² = x³ + 7 */
    secp256k1_fe x2, x3, y2_expected, y2_actual, seven;
    fe_sqr(&x2, &SECP256K1_G.x);
    fe_mul(&x3, &x2, &SECP256K1_G.x);
    seven = (secp256k1_fe){{7, 0, 0, 0}};
    fe_add(&y2_expected, &x3, &seven);
    fe_sqr(&y2_actual, &SECP256K1_G.y);
    ASSERT_TRUE(fe_equal(&y2_expected, &y2_actual));
}

/* ==================== Point Doubling ==================== */

static void double_g_matches_two_g(void) {
    /* 2·G via doubling */
    secp256k1_gej p, doubled;
    gej_set_ge(&p, &SECP256K1_G);
    gej_double(&doubled, &p);

    /* 2·G via scalar multiplication */
    secp256k1_scalar two = {{2, 0, 0, 0}};
    secp256k1_gej mul_result;
    ecmult_gen(&mul_result, &two);

    ASSERT_TRUE(affine_xy_eq(&doubled, &mul_result));
}

static void double_in_place(void) {
    secp256k1_gej p;
    gej_set_ge(&p, &SECP256K1_G);
    gej_double(&p, &p); /* out == in */

    secp256k1_scalar two = {{2, 0, 0, 0}};
    secp256k1_gej expected;
    ecmult_gen(&expected, &two);

    ASSERT_TRUE(affine_x_eq(&p, &expected));
}

static void double_infinity_is_infinity(void) {
    secp256k1_gej inf, result;
    gej_set_infinity(&inf);
    gej_double(&result, &inf);
    ASSERT_TRUE(gej_is_infinity(&result));
}

/* ==================== Point Addition ==================== */

static void add_g_plus_g_equals_double_g(void) {
    secp256k1_gej g, sum, doubled;
    gej_set_ge(&g, &SECP256K1_G);
    gej_add(&sum, &g, &g);
    gej_double(&doubled, &g);
    ASSERT_TRUE(affine_xy_eq(&sum, &doubled));
}

static void add_infinity_identity(void) {
    secp256k1_gej g, inf, r1, r2;
    gej_set_ge(&g, &SECP256K1_G);
    gej_set_infinity(&inf);

    gej_add(&r1, &g, &inf);
    secp256k1_ge r1_ge;
    gej_to_ge(&r1_ge, &r1);
    ASSERT_TRUE(fe_equal(&r1_ge.x, &SECP256K1_G.x));

    gej_add(&r2, &inf, &g);
    secp256k1_ge r2_ge;
    gej_to_ge(&r2_ge, &r2);
    ASSERT_TRUE(fe_equal(&r2_ge.x, &SECP256K1_G.x));
}

static void add_inverse_is_infinity(void) {
    /* G + (-G) = infinity */
    secp256k1_gej g, neg_g, result;
    gej_set_ge(&g, &SECP256K1_G);
    secp256k1_fe neg_gy;
    fe_negate(&neg_gy, &SECP256K1_G.y, 1);
    secp256k1_ge neg_g_ge = {.x = SECP256K1_G.x, .y = neg_gy};
    gej_set_ge(&neg_g, &neg_g_ge);
    gej_add(&result, &g, &neg_g);
    ASSERT_TRUE(gej_is_infinity(&result));
}

/* ==================== Mixed Addition ==================== */

static void add_mixed_matches_full(void) {
    secp256k1_scalar three = {{3, 0, 0, 0}};
    secp256k1_gej p;
    ecmult_gen(&p, &three); /* 3G in Jacobian */

    /* Mixed: p + G */
    secp256k1_gej mixed;
    gej_add_ge(&mixed, &p, &SECP256K1_G);

    /* Full: p + G (as Jacobian) */
    secp256k1_gej g_jac, full;
    gej_set_ge(&g_jac, &SECP256K1_G);
    gej_add(&full, &p, &g_jac);

    ASSERT_TRUE(affine_xy_eq(&mixed, &full));
}

static void add_mixed_infinity_input(void) {
    secp256k1_gej inf, result;
    gej_set_infinity(&inf);
    gej_add_ge(&result, &inf, &SECP256K1_G);
    secp256k1_ge r_ge;
    gej_to_ge(&r_ge, &result);
    ASSERT_TRUE(fe_equal(&r_ge.x, &SECP256K1_G.x));
}

/* ==================== Scalar Multiplication ==================== */

static void mul_g_by_one(void) {
    secp256k1_scalar one = {{1, 0, 0, 0}};
    secp256k1_gej result;
    ecmult_gen(&result, &one);
    secp256k1_ge r_ge;
    gej_to_ge(&r_ge, &result);
    ASSERT_TRUE(fe_equal(&r_ge.x, &SECP256K1_G.x));
    ASSERT_TRUE(fe_equal(&r_ge.y, &SECP256K1_G.y));
}

static void mul_g_by_zero_is_infinity(void) {
    secp256k1_scalar zero = SCALAR_ZERO;
    secp256k1_gej result;
    ecmult_gen(&result, &zero);
    ASSERT_TRUE(gej_is_infinity(&result));
}

static void mul_g_by_n_is_infinity(void) {
    secp256k1_gej result;
    ecmult_gen(&result, &SCALAR_N);
    ASSERT_TRUE(gej_is_infinity(&result));
}

static void mul_g_matches_mul(void) {
    /* mulG(k) should equal mul(G, k) */
    secp256k1_scalar k;
    uint8_t k_bytes[32];
    hex_to_bytes(k_bytes, "67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530", 32);
    scalar_from_bytes(&k, k_bytes);

    secp256k1_gej g_result;
    ecmult_gen(&g_result, &k);

    secp256k1_gej g_jac, m_result;
    gej_set_ge(&g_jac, &SECP256K1_G);
    ecmult(&m_result, &g_jac, &k);

    ASSERT_TRUE(affine_xy_eq(&g_result, &m_result));
}

/* ==================== Dual Scalar Multiplication ==================== */

static void mul_double_g_separate_vs_combined(void) {
    secp256k1_scalar s, e;
    uint8_t s_bytes[32], e_bytes[32];
    hex_to_bytes(s_bytes, "67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530", 32);
    hex_to_bytes(e_bytes, "3982f19bef1615bccfbb05e321c10e1d4cba3df0e841c2e41eeb6016347653c3", 32);
    scalar_from_bytes(&s, s_bytes);
    scalar_from_bytes(&e, e_bytes);

    /* P = 2·G */
    secp256k1_scalar two = {{2, 0, 0, 0}};
    secp256k1_gej p_jac;
    ecmult_gen(&p_jac, &two);
    secp256k1_ge p_ge;
    gej_to_ge(&p_ge, &p_jac);

    /* Combined: s*G + e*P */
    secp256k1_gej combined;
    ecmult_double_g(&combined, &s, &p_ge, &e);

    /* Separate: s*G + e*P */
    secp256k1_gej sG, eP, sep;
    ecmult_gen(&sG, &s);
    secp256k1_gej p_jac2;
    gej_set_ge(&p_jac2, &p_ge);
    ecmult(&eP, &p_jac2, &e);
    gej_add(&sep, &sG, &eP);

    ASSERT_TRUE(affine_xy_eq(&combined, &sep));
}

/* ==================== Runner ==================== */

void run_point_tests(void) {
    SUITE(point);
    printf("Running point operation tests...\n");
    /* Generator */
    TEST(generator_is_on_curve);
    /* Doubling */
    TEST(double_g_matches_two_g);
    TEST(double_in_place);
    TEST(double_infinity_is_infinity);
    /* Addition */
    TEST(add_g_plus_g_equals_double_g);
    TEST(add_infinity_identity);
    TEST(add_inverse_is_infinity);
    /* Mixed addition */
    TEST(add_mixed_matches_full);
    TEST(add_mixed_infinity_input);
    /* Scalar multiplication */
    TEST(mul_g_by_one);
    TEST(mul_g_by_zero_is_infinity);
    TEST(mul_g_by_n_is_infinity);
    TEST(mul_g_matches_mul);
    /* Dual scalar */
    TEST(mul_double_g_separate_vs_combined);
    printf("  Point: done\n");
}
