/*
 * Copyright (c) 2025 Vitor Pamplona
 * Field arithmetic tests (mod p). Ported from FieldPTest.kt
 */
#include "test_framework.h"
#include "field.h"

/* Helper: hex string → field element */
static void fe_hex(secp256k1_fe *r, const char *hex) {
    uint8_t bytes[32];
    hex_to_bytes(bytes, hex, 32);
    fe_from_bytes(r, bytes);
}

/* Helper: field element → hex string comparison */
static void fe_to_hex(char *out, const secp256k1_fe *a) {
    uint8_t bytes[32];
    secp256k1_fe t = *a;
    fe_to_bytes(bytes, &t);
    for (int i = 0; i < 32; i++) {
        out[i*2]   = "0123456789abcdef"[bytes[i] >> 4];
        out[i*2+1] = "0123456789abcdef"[bytes[i] & 0xf];
    }
    out[64] = '\0';
}

static int fe_hex_eq(const secp256k1_fe *a, const char *hex) {
    char got[65];
    fe_to_hex(got, a);
    return strcmp(got, hex) == 0;
}

/* ==================== Basic Identities ==================== */

static void add_zero_identity(void) {
    secp256k1_fe a, zero, r;
    fe_hex(&a, "67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530");
    zero = FE_ZERO;
    fe_add(&r, &a, &zero);
    ASSERT_TRUE(fe_hex_eq(&r, "67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530"));
}

static void sub_self_is_zero(void) {
    secp256k1_fe a, neg_a, r;
    fe_hex(&a, "67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530");
    fe_negate(&neg_a, &a, 1);
    fe_add(&r, &a, &neg_a);
    fe_normalize(&r);
    ASSERT_TRUE(fe_is_zero(&r));
}

static void add_then_sub_roundtrips(void) {
    secp256k1_fe a, b, sum, neg_b, back;
    fe_hex(&a, "67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530");
    fe_hex(&b, "3982f19bef1615bccfbb05e321c10e1d4cba3df0e841c2e41eeb6016347653c3");
    fe_add(&sum, &a, &b);
    fe_negate(&neg_b, &b, 1);
    fe_add(&back, &sum, &neg_b);
    ASSERT_TRUE(fe_hex_eq(&back, "67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530"));
}

static void mul_one_identity(void) {
    secp256k1_fe a, one, r;
    fe_hex(&a, "67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530");
    one = FE_ONE;
    fe_mul(&r, &a, &one);
    ASSERT_TRUE(fe_hex_eq(&r, "67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530"));
}

/* ==================== Reduction Near P ==================== */

static void add_near_p(void) {
    secp256k1_fe pm1, one, r;
    fe_hex(&pm1, "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e");
    one = FE_ONE;
    fe_add(&r, &pm1, &one);
    fe_normalize(&r);
    ASSERT_TRUE(fe_is_zero(&r));
}

static void add_near_p_overflow(void) {
    secp256k1_fe pm1, r;
    fe_hex(&pm1, "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e");
    fe_add(&r, &pm1, &pm1);
    /* (p-1) + (p-1) = 2p-2 ≡ p-2 */
    ASSERT_TRUE(fe_hex_eq(&r, "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2d"));
}

static void sub_underflow(void) {
    secp256k1_fe zero, one, neg_one;
    zero = FE_ZERO;
    one = FE_ONE;
    fe_negate(&neg_one, &one, 1);
    fe_normalize(&neg_one);
    /* 0 - 1 ≡ p - 1 */
    ASSERT_TRUE(fe_hex_eq(&neg_one, "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e"));
}

/* ==================== Negation ==================== */

static void neg_twice_is_identity(void) {
    secp256k1_fe a, neg_a, neg_neg_a;
    fe_hex(&a, "67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530");
    fe_negate(&neg_a, &a, 1);
    fe_negate(&neg_neg_a, &neg_a, 2);
    ASSERT_TRUE(fe_hex_eq(&neg_neg_a, "67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530"));
}

static void add_neg_is_zero(void) {
    secp256k1_fe a, neg_a, r;
    fe_hex(&a, "67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530");
    fe_negate(&neg_a, &a, 1);
    fe_add(&r, &a, &neg_a);
    fe_normalize(&r);
    ASSERT_TRUE(fe_is_zero(&r));
}

/* ==================== Multiplication ==================== */

static void mul_commutative(void) {
    secp256k1_fe a, b, ab, ba;
    fe_hex(&a, "67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530");
    fe_hex(&b, "3982f19bef1615bccfbb05e321c10e1d4cba3df0e841c2e41eeb6016347653c3");
    fe_mul(&ab, &a, &b);
    fe_mul(&ba, &b, &a);
    ASSERT_TRUE(fe_equal(&ab, &ba));
}

static void sqr_matches_mul(void) {
    secp256k1_fe a, sqr_a, mul_aa;
    fe_hex(&a, "67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530");
    fe_sqr(&sqr_a, &a);
    fe_mul(&mul_aa, &a, &a);
    ASSERT_TRUE(fe_equal(&sqr_a, &mul_aa));
}

static void mul_distributive(void) {
    /* a * (b + c) = a*b + a*c */
    secp256k1_fe a, b, c, bc, lhs, ab, ac, rhs;
    fe_hex(&a, "67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530");
    fe_hex(&b, "3982f19bef1615bccfbb05e321c10e1d4cba3df0e841c2e41eeb6016347653c3");
    c = (secp256k1_fe){{7, 0, 0, 0}};
    fe_add(&bc, &b, &c);
    fe_mul(&lhs, &a, &bc);
    fe_mul(&ab, &a, &b);
    fe_mul(&ac, &a, &c);
    fe_add(&rhs, &ab, &ac);
    ASSERT_TRUE(fe_equal(&lhs, &rhs));
}

/* ==================== Inversion ==================== */

static void inv_mul_is_one(void) {
    secp256k1_fe a, a_inv, product;
    fe_hex(&a, "67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530");
    fe_inv(&a_inv, &a);
    fe_mul(&product, &a, &a_inv);
    fe_normalize_full(&product);
    ASSERT_TRUE(fe_equal(&product, &FE_ONE));
}

static void inv_of_one(void) {
    secp256k1_fe r;
    fe_inv(&r, &FE_ONE);
    ASSERT_TRUE(fe_equal(&r, &FE_ONE));
}

static void inv_of_p_minus_1(void) {
    secp256k1_fe pm1, r;
    fe_hex(&pm1, "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e");
    fe_inv(&r, &pm1);
    /* (p-1)^(-1) = p-1 because (p-1)^2 = 1 mod p */
    ASSERT_TRUE(fe_hex_eq(&r, "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e"));
}

/* ==================== Half ==================== */

static void half_of_even(void) {
    secp256k1_fe four = {{4, 0, 0, 0}}, r;
    fe_half(&r, &four);
    ASSERT_EQ_INT(r.d[0], 2);
    ASSERT_EQ_INT(r.d[1], 0);
}

static void half_then_double_roundtrips(void) {
    secp256k1_fe a, h, doubled;
    fe_hex(&a, "67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530");
    fe_half(&h, &a);
    fe_add(&doubled, &h, &h);
    ASSERT_TRUE(fe_hex_eq(&doubled, "67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530"));
}

/* ==================== Square Root ==================== */

static void sqrt_of_square(void) {
    secp256k1_fe a, a_sq, root;
    fe_hex(&a, "67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530");
    fe_sqr(&a_sq, &a);
    ASSERT_TRUE(fe_sqrt(&root, &a_sq));
    /* root² should equal a² */
    secp256k1_fe root_sq;
    fe_sqr(&root_sq, &root);
    ASSERT_TRUE(fe_equal(&root_sq, &a_sq));
}

static void sqrt_of_non_residue(void) {
    /* 3 is not a quadratic residue mod p */
    secp256k1_fe three = {{3, 0, 0, 0}}, root;
    ASSERT_FALSE(fe_sqrt(&root, &three));
}

/* ==================== Carry-Fold Regression ==================== */

static void chained_lazy_add_then_mul(void) {
    /* 4*(p-1) ≡ p-4 (mod p) */
    secp256k1_fe pm1, two, four, one, product;
    fe_hex(&pm1, "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e");
    fe_add(&two, &pm1, &pm1);
    fe_add(&four, &two, &two);
    one = FE_ONE;
    fe_mul(&product, &four, &one);
    fe_normalize_full(&product);
    ASSERT_TRUE(fe_hex_eq(&product, "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2b"));
}

static void mul_of_large_values_stays_reduced(void) {
    /* ((p-1)^2)^2 ≡ 1 (mod p) */
    secp256k1_fe pm1, sq, sq_again;
    fe_hex(&pm1, "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e");
    fe_sqr(&sq, &pm1);
    fe_mul(&sq_again, &sq, &sq);
    fe_normalize_full(&sq_again);
    ASSERT_TRUE(fe_equal(&sq_again, &FE_ONE));
}

static void repeated_squaring_converges(void) {
    /* Squaring chain: a, a², a⁴, …, a^(2^10) via mul and sqr must match */
    secp256k1_fe a;
    fe_hex(&a, "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2d");
    secp256k1_fe by_mul = a, by_sqr = a, tmp;
    for (int i = 0; i < 10; i++) {
        fe_mul(&tmp, &by_mul, &by_mul);
        by_mul = tmp;
    }
    for (int i = 0; i < 10; i++) {
        fe_sqr(&tmp, &by_sqr);
        by_sqr = tmp;
    }
    fe_normalize_full(&by_mul);
    fe_normalize_full(&by_sqr);
    ASSERT_TRUE(fe_equal(&by_mul, &by_sqr));
}

static void mul_roundtrip_via_inv(void) {
    secp256k1_fe pm1, lazy_big, b, ref, b_inv, back;
    fe_hex(&pm1, "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e");
    fe_add(&lazy_big, &pm1, &pm1);
    b = (secp256k1_fe){{0x42, 0, 0, 0}};
    fe_mul(&ref, &lazy_big, &b);
    fe_inv(&b_inv, &b);
    fe_mul(&back, &ref, &b_inv);
    fe_normalize_full(&lazy_big);
    fe_normalize_full(&back);
    ASSERT_TRUE(fe_equal(&lazy_big, &back));
}

/* ==================== Runner ==================== */

void run_field_tests(void) {
    SUITE(field);
    printf("Running field arithmetic tests...\n");
    /* Identities */
    TEST(add_zero_identity);
    TEST(sub_self_is_zero);
    TEST(add_then_sub_roundtrips);
    TEST(mul_one_identity);
    /* Reduction */
    TEST(add_near_p);
    TEST(add_near_p_overflow);
    TEST(sub_underflow);
    /* Negation */
    TEST(neg_twice_is_identity);
    TEST(add_neg_is_zero);
    /* Multiplication */
    TEST(mul_commutative);
    TEST(sqr_matches_mul);
    TEST(mul_distributive);
    /* Inversion */
    TEST(inv_mul_is_one);
    TEST(inv_of_one);
    TEST(inv_of_p_minus_1);
    /* Half */
    TEST(half_of_even);
    TEST(half_then_double_roundtrips);
    /* Sqrt */
    TEST(sqrt_of_square);
    TEST(sqrt_of_non_residue);
    /* Carry-fold regression */
    TEST(chained_lazy_add_then_mul);
    TEST(mul_of_large_values_stays_reduced);
    TEST(repeated_squaring_converges);
    TEST(mul_roundtrip_via_inv);
    printf("  Field: done\n");
}
