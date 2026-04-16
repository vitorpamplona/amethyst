/*
 * Copyright (c) 2025 Vitor Pamplona
 * SHA-256 known-answer tests and tagged hash consistency.
 */
#include "test_framework.h"
#include "schnorr256k1.h"
#include "sha256.h"

/* ==================== Known-Answer Tests ==================== */

static void sha256_empty(void) {
    uint8_t out[32];
    secp256k1_sha256_hash(out, NULL, 0);
    ASSERT_HEX_EQ(out,
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 32);
}

static void sha256_abc(void) {
    uint8_t out[32];
    secp256k1_sha256_hash(out, (const uint8_t *)"abc", 3);
    ASSERT_HEX_EQ(out,
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", 32);
}

static void sha256_448bits(void) {
    /* "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq" */
    const char *msg = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq";
    uint8_t out[32];
    secp256k1_sha256_hash(out, (const uint8_t *)msg, strlen(msg));
    ASSERT_HEX_EQ(out,
        "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1", 32);
}

static void sha256_incremental(void) {
    /* Incremental vs one-shot should produce the same result */
    const char *part1 = "hello ";
    const char *part2 = "world";
    uint8_t out_inc[32], out_one[32];

    secp256k1_sha256 ctx;
    secp256k1_sha256_init(&ctx);
    secp256k1_sha256_update(&ctx, (const uint8_t *)part1, strlen(part1));
    secp256k1_sha256_update(&ctx, (const uint8_t *)part2, strlen(part2));
    secp256k1_sha256_finalize(&ctx, out_inc);

    secp256k1_sha256_hash(out_one, (const uint8_t *)"hello world", 11);
    ASSERT_MEM_EQ(out_inc, out_one, 32);
}

/* ==================== Tagged Hash ==================== */

static void tagged_hash_consistency(void) {
    /* tagged_hash("BIP0340/challenge", msg) should equal
     * SHA256(SHA256(tag) || SHA256(tag) || msg) computed step by step */
    const char *tag = "BIP0340/challenge";
    uint8_t msg[32];
    memset(msg, 0x42, 32);

    uint8_t result[32];
    secp256k1_tagged_hash(result, tag, msg, 32);

    /* Manual computation */
    uint8_t tag_hash[32];
    secp256k1_sha256_hash(tag_hash, (const uint8_t *)tag, strlen(tag));

    uint8_t preimage[64 + 32];
    memcpy(preimage, tag_hash, 32);
    memcpy(preimage + 32, tag_hash, 32);
    memcpy(preimage + 64, msg, 32);

    uint8_t expected[32];
    secp256k1_sha256_hash(expected, preimage, 96);

    ASSERT_MEM_EQ(result, expected, 32);
}

static void tagged_hash_precomputed_matches(void) {
    /* Precomputed prefix version should match the string version */
    const char *tag = "BIP0340/aux";
    uint8_t msg[32];
    memset(msg, 0xAB, 32);

    uint8_t result_str[32], result_pre[32];
    secp256k1_tagged_hash(result_str, tag, msg, 32);

    /* Compute prefix manually */
    uint8_t tag_hash[32];
    secp256k1_sha256_hash(tag_hash, (const uint8_t *)tag, strlen(tag));
    uint8_t prefix[64];
    memcpy(prefix, tag_hash, 32);
    memcpy(prefix + 32, tag_hash, 32);

    secp256k1_tagged_hash_precomputed(result_pre, prefix, msg, 32);
    ASSERT_MEM_EQ(result_str, result_pre, 32);
}

/* ==================== Public Wrapper ==================== */

static void public_sha256_wrapper(void) {
    uint8_t out1[32], out2[32];
    secp256k1c_sha256(out1, (const uint8_t *)"test", 4);
    secp256k1_sha256_hash(out2, (const uint8_t *)"test", 4);
    ASSERT_MEM_EQ(out1, out2, 32);
}

static void public_tagged_hash_wrapper(void) {
    uint8_t msg[32];
    memset(msg, 0x01, 32);
    uint8_t out1[32], out2[32];
    secp256k1c_tagged_hash(out1, "BIP0340/challenge", msg, 32);
    secp256k1_tagged_hash(out2, "BIP0340/challenge", msg, 32);
    ASSERT_MEM_EQ(out1, out2, 32);
}

/* ==================== Runner ==================== */

void run_sha256_tests(void) {
    SUITE(sha256);
    printf("Running SHA-256 tests...\n");
    TEST(sha256_empty);
    TEST(sha256_abc);
    TEST(sha256_448bits);
    TEST(sha256_incremental);
    TEST(tagged_hash_consistency);
    TEST(tagged_hash_precomputed_matches);
    TEST(public_sha256_wrapper);
    TEST(public_tagged_hash_wrapper);
    printf("  SHA-256: done\n");
}
