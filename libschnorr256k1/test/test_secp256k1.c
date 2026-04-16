/*
 * Copyright (c) 2025 Vitor Pamplona
 * Integration tests for public API: key ops, ECDH, batch verify.
 * Ported from Secp256k1Test.kt
 */
#include "test_framework.h"
#include "schnorr256k1.h"

/* ==================== secKeyVerify ==================== */

static void verify_valid_privkey(void) {
    HEX32(key, "67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530");
    ASSERT_TRUE(secp256k1c_seckey_verify(key));
}

static void verify_invalid_privkey_curve_order(void) {
    HEX32(key, "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141");
    ASSERT_FALSE(secp256k1c_seckey_verify(key));
}

static void verify_invalid_privkey_zero(void) {
    HEX32(key, "0000000000000000000000000000000000000000000000000000000000000000");
    ASSERT_FALSE(secp256k1c_seckey_verify(key));
}

/* ==================== pubkeyCreate ==================== */

static void create_valid_pubkey(void) {
    HEX32(sec, "67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530");
    uint8_t pub[65];
    ASSERT_TRUE(secp256k1c_pubkey_create(pub, sec));
    ASSERT_HEX_EQ(pub,
        "04c591a8ff19ac9c4e4e5793673b83123437e975285e7b442f4ee2654dffca5e2d"
        "2103ed494718c697ac9aebcfd19612e224db46661011863ed2fc54e71861e2a6", 65);
}

static void create_pubkey_from_key_one(void) {
    HEX32(sec, "0000000000000000000000000000000000000000000000000000000000000001");
    uint8_t pub[65];
    ASSERT_TRUE(secp256k1c_pubkey_create(pub, sec));
    /* G itself */
    ASSERT_HEX_EQ(pub,
        "0479be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
        "483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8", 65);
}

static void create_pubkey_from_key_three(void) {
    HEX32(sec, "0000000000000000000000000000000000000000000000000000000000000003");
    uint8_t pub[65], comp[33];
    ASSERT_TRUE(secp256k1c_pubkey_create(pub, sec));
    ASSERT_TRUE(secp256k1c_pubkey_compress(comp, pub));
    /* BIP-340 test vector 0 public key */
    ASSERT_HEX_EQ(comp, "02f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9", 33);
}

/* ==================== pubKeyCompress ==================== */

static void compress_pubkey(void) {
    HEX65(pub, "04C591A8FF19AC9C4E4E5793673B83123437E975285E7B442F4EE2654DFFCA5E2D"
               "2103ED494718C697AC9AEBCFD19612E224DB46661011863ED2FC54E71861E2A6");
    uint8_t comp[33];
    ASSERT_TRUE(secp256k1c_pubkey_compress(comp, pub));
    ASSERT_HEX_EQ(comp, "02c591a8ff19ac9c4e4e5793673b83123437e975285e7b442f4ee2654dffca5e2d", 33);
}

static void compress_pubkey_odd_y(void) {
    HEX32(sec, "65f039136f8da8d3e87b4818746b53318d5481e24b2673f162815144223a0b5a");
    uint8_t pub[65], comp[33];
    ASSERT_TRUE(secp256k1c_pubkey_create(pub, sec));
    ASSERT_TRUE(secp256k1c_pubkey_compress(comp, pub));
    ASSERT_HEX_EQ(comp, "033dcef7585efbdb68747d919152bd481e21f5e952aaaef5a19604fbd096a93dd5", 33);
}

static void compress_pubkey_even_y(void) {
    HEX32(sec, "e6159851715b4aa6190c22b899b0c792847de0a4435ac5b678f35738351c43b0");
    uint8_t pub[65], comp[33];
    ASSERT_TRUE(secp256k1c_pubkey_create(pub, sec));
    ASSERT_TRUE(secp256k1c_pubkey_compress(comp, pub));
    ASSERT_HEX_EQ(comp, "029fa4ce8c87ca546b196e6518db80a6780e1bd5552b61f9f17bafee5d4e34e09b", 33);
}

/* ==================== privKeyTweakAdd ==================== */

static void privkey_tweak_add(void) {
    HEX32(sec, "67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530");
    HEX32(tweak, "3982F19BEF1615BCCFBB05E321C10E1D4CBA3DF0E841C2E41EEB6016347653C3");
    uint8_t result[32];
    ASSERT_TRUE(secp256k1c_privkey_tweak_add(result, sec, tweak));
    ASSERT_HEX_EQ(result, "a168571e189e6f9a7e2d657a4b53ae99b909f7e712d1c23ced28093cd57c88f3", 32);
}

/* ==================== pubKeyTweakMul ==================== */

static void pubkey_tweak_mul(void) {
    HEX65(pub, "040A629506E1B65CD9D2E0BA9C75DF9C4FED0DB16DC9625ED14397F0AFC836FAE5"
               "95DC53F8B0EFE61E703075BD9B143BAC75EC0E19F82A2208CAEB32BE53414C40");
    HEX32(tweak, "3982F19BEF1615BCCFBB05E321C10E1D4CBA3DF0E841C2E41EEB6016347653C3");
    uint8_t result[65];
    ASSERT_TRUE(secp256k1c_pubkey_tweak_mul(result, 65, pub, 65, tweak));
    ASSERT_HEX_EQ(result,
        "04e0fe6fe55ebca626b98a807f6caf654139e14e5e3698f01a9a658e21dc1d2791"
        "ec060d4f412a794d5370f672bc94b722640b5f76914151cfca6e712ca48cc589", 65);
}

static void pubkey_tweak_mul_compressed(void) {
    HEX32(xonly, "c2f9d9948dc8c7c38321e4b85c8558872eafa0641cd269db76848a6073e69133");
    HEX32(sec, "315e59ff51cb9209768cf7da80791ddcaae56ac9775eb25b6dee1234bc5d2268");
    uint8_t pub33[33];
    pub33[0] = 0x02;
    memcpy(pub33 + 1, xonly, 32);
    uint8_t result[33];
    ASSERT_TRUE(secp256k1c_pubkey_tweak_mul(result, 33, pub33, 33, sec));
    ASSERT_EQ_INT(33, 33); /* 33-byte compressed output */
}

/* ==================== ECDH ==================== */

static void ecdh_symmetry(void) {
    HEX32(secA, "315e59ff51cb9209768cf7da80791ddcaae56ac9775eb25b6dee1234bc5d2268");
    HEX32(secB, "a1e37752c9fdc1273be53f68c5f74be7c8905728e8de75800b94262f9497c86e");

    uint8_t pubA[65], pubB[65], compA[33], compB[33];
    ASSERT_TRUE(secp256k1c_pubkey_create(pubA, secA));
    ASSERT_TRUE(secp256k1c_pubkey_create(pubB, secB));
    ASSERT_TRUE(secp256k1c_pubkey_compress(compA, pubA));
    ASSERT_TRUE(secp256k1c_pubkey_compress(compB, pubB));

    uint8_t secretAB[65], secretBA[65];
    ASSERT_TRUE(secp256k1c_pubkey_tweak_mul(secretAB, 33, compB, 33, secA));
    ASSERT_TRUE(secp256k1c_pubkey_tweak_mul(secretBA, 33, compA, 33, secB));
    ASSERT_MEM_EQ(secretAB, secretBA, 33);
}

static void ecdh_xonly_symmetric(void) {
    HEX32(secA, "67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530");
    HEX32(secB, "3982F19BEF1615BCCFBB05E321C10E1D4CBA3DF0E841C2E41EEB6016347653C3");

    uint8_t pubA[65], pubB[65], compA[33], compB[33];
    ASSERT_TRUE(secp256k1c_pubkey_create(pubA, secA));
    ASSERT_TRUE(secp256k1c_pubkey_create(pubB, secB));
    ASSERT_TRUE(secp256k1c_pubkey_compress(compA, pubA));
    ASSERT_TRUE(secp256k1c_pubkey_compress(compB, pubB));

    uint8_t resultAB[32], resultBA[32];
    ASSERT_TRUE(secp256k1c_ecdh_xonly(resultAB, compB + 1, secA));
    ASSERT_TRUE(secp256k1c_ecdh_xonly(resultBA, compA + 1, secB));
    ASSERT_MEM_EQ(resultAB, resultBA, 32);
}

/* ==================== Schnorr Edge Cases ==================== */

static void verify_wrong_message(void) {
    HEX32(sec, "f410f88bcec6cbfda04d6a273c7b1dd8bba144cd45b71e87109cfa11dd7ed561");
    uint8_t msg[32]; memset(msg, 0x42, 32);
    uint8_t sig[64];
    ASSERT_TRUE(secp256k1c_schnorr_sign(sig, msg, 32, sec, NULL));

    uint8_t pub65[65], pub33[33];
    ASSERT_TRUE(secp256k1c_pubkey_create(pub65, sec));
    ASSERT_TRUE(secp256k1c_pubkey_compress(pub33, pub65));

    ASSERT_TRUE(secp256k1c_schnorr_verify(sig, msg, 32, pub33 + 1));
    uint8_t wrong[32]; memset(wrong, 0x43, 32);
    ASSERT_FALSE(secp256k1c_schnorr_verify(sig, wrong, 32, pub33 + 1));
}

static void verify_corrupted_sig(void) {
    HEX32(sec, "f410f88bcec6cbfda04d6a273c7b1dd8bba144cd45b71e87109cfa11dd7ed561");
    uint8_t msg[32]; memset(msg, 0x42, 32);
    uint8_t sig[64];
    ASSERT_TRUE(secp256k1c_schnorr_sign(sig, msg, 32, sec, NULL));

    uint8_t pub65[65], pub33[33];
    ASSERT_TRUE(secp256k1c_pubkey_create(pub65, sec));
    ASSERT_TRUE(secp256k1c_pubkey_compress(pub33, pub65));

    uint8_t corrupt[64];
    memcpy(corrupt, sig, 64);
    corrupt[0] ^= 1;
    ASSERT_FALSE(secp256k1c_schnorr_verify(corrupt, msg, 32, pub33 + 1));
}

static void sign_deterministic(void) {
    HEX32(sec, "f410f88bcec6cbfda04d6a273c7b1dd8bba144cd45b71e87109cfa11dd7ed561");
    uint8_t msg[32]; memset(msg, 0x42, 32);
    uint8_t sig1[64], sig2[64];
    ASSERT_TRUE(secp256k1c_schnorr_sign(sig1, msg, 32, sec, NULL));
    ASSERT_TRUE(secp256k1c_schnorr_sign(sig2, msg, 32, sec, NULL));
    ASSERT_MEM_EQ(sig1, sig2, 64);
}

/* ==================== Batch Verification ==================== */

static void batch_all_valid(void) {
    HEX32(sec, "67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530");
    uint8_t pub65[65], pub33[33];
    ASSERT_TRUE(secp256k1c_pubkey_create(pub65, sec));
    ASSERT_TRUE(secp256k1c_pubkey_compress(pub33, pub65));

    uint8_t sigs[10][64], msgs[10][32];
    const uint8_t *sig_ptrs[10], *msg_ptrs[10];
    size_t lens[10];

    for (int i = 0; i < 10; i++) {
        for (int j = 0; j < 32; j++) msgs[i][j] = (uint8_t)(i * 7 + j);
        ASSERT_TRUE(secp256k1c_schnorr_sign(sigs[i], msgs[i], 32, sec, NULL));
        ASSERT_TRUE(secp256k1c_schnorr_verify(sigs[i], msgs[i], 32, pub33 + 1));
        sig_ptrs[i] = sigs[i];
        msg_ptrs[i] = msgs[i];
        lens[i] = 32;
    }
    ASSERT_TRUE(secp256k1c_schnorr_verify_batch(pub33 + 1, sig_ptrs, msg_ptrs, lens, 10));
}

static void batch_with_invalid(void) {
    HEX32(sec, "67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530");
    uint8_t pub65[65], pub33[33];
    ASSERT_TRUE(secp256k1c_pubkey_create(pub65, sec));
    ASSERT_TRUE(secp256k1c_pubkey_compress(pub33, pub65));

    uint8_t msg1[32]; memset(msg1, 0x01, 32);
    uint8_t msg2[32]; memset(msg2, 0x02, 32);
    uint8_t sig1[64], sig2[64];
    ASSERT_TRUE(secp256k1c_schnorr_sign(sig1, msg1, 32, sec, NULL));
    ASSERT_TRUE(secp256k1c_schnorr_sign(sig2, msg2, 32, sec, NULL));
    sig2[63] ^= 0x01; /* corrupt */

    const uint8_t *sigs[] = {sig1, sig2};
    const uint8_t *msgs[] = {msg1, msg2};
    size_t lens[] = {32, 32};
    ASSERT_FALSE(secp256k1c_schnorr_verify_batch(pub33 + 1, sigs, msgs, lens, 2));
}

static void batch_empty(void) {
    HEX32(pub, "67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530");
    ASSERT_TRUE(secp256k1c_schnorr_verify_batch(pub, NULL, NULL, NULL, 0));
}

static void batch_single(void) {
    HEX32(sec, "67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530");
    uint8_t pub65[65], pub33[33];
    ASSERT_TRUE(secp256k1c_pubkey_create(pub65, sec));
    ASSERT_TRUE(secp256k1c_pubkey_compress(pub33, pub65));

    uint8_t msg[32]; memset(msg, 0x42, 32);
    uint8_t sig[64];
    ASSERT_TRUE(secp256k1c_schnorr_sign(sig, msg, 32, sec, NULL));

    const uint8_t *sigs[] = {sig};
    const uint8_t *msgs[] = {msg};
    size_t lens[] = {32};
    ASSERT_TRUE(secp256k1c_schnorr_verify_batch(pub33 + 1, sigs, msgs, lens, 1));
}

static void batch_large(void) {
    HEX32(sec, "3982F19BEF1615BCCFBB05E321C10E1D4CBA3DF0E841C2E41EEB6016347653C3");
    uint8_t pub65[65], pub33[33];
    ASSERT_TRUE(secp256k1c_pubkey_create(pub65, sec));
    ASSERT_TRUE(secp256k1c_pubkey_compress(pub33, pub65));

    uint8_t sigs[32][64], msgs[32][64];
    const uint8_t *sig_ptrs[32], *msg_ptrs[32];
    size_t lens[32];

    for (int i = 0; i < 32; i++) {
        for (int j = 0; j < 64; j++) msgs[i][j] = (uint8_t)(i * 13 + j);
        ASSERT_TRUE(secp256k1c_schnorr_sign(sigs[i], msgs[i], 64, sec, NULL));
        sig_ptrs[i] = sigs[i];
        msg_ptrs[i] = msgs[i];
        lens[i] = 64;
    }
    ASSERT_TRUE(secp256k1c_schnorr_verify_batch(pub33 + 1, sig_ptrs, msg_ptrs, lens, 32));
}

/* ==================== Tagged Hash ==================== */

static void tagged_hash_consistency(void) {
    uint8_t msg[32]; memset(msg, 0x42, 32);
    uint8_t result[32];
    secp256k1c_tagged_hash(result, "BIP0340/challenge", msg, 32);

    /* Manual: SHA256(SHA256(tag) || SHA256(tag) || msg) */
    uint8_t tag_hash[32];
    secp256k1c_sha256(tag_hash, (const uint8_t *)"BIP0340/challenge", 17);
    uint8_t preimage[96];
    memcpy(preimage, tag_hash, 32);
    memcpy(preimage + 32, tag_hash, 32);
    memcpy(preimage + 64, msg, 32);
    uint8_t expected[32];
    secp256k1c_sha256(expected, preimage, 96);
    ASSERT_MEM_EQ(result, expected, 32);
}

/* ==================== Runner ==================== */

void run_secp256k1_tests(void) {
    SUITE(secp256k1);
    printf("Running secp256k1 integration tests...\n");
    /* Key validation */
    TEST(verify_valid_privkey);
    TEST(verify_invalid_privkey_curve_order);
    TEST(verify_invalid_privkey_zero);
    /* Key creation */
    TEST(create_valid_pubkey);
    TEST(create_pubkey_from_key_one);
    TEST(create_pubkey_from_key_three);
    /* Compression */
    TEST(compress_pubkey);
    TEST(compress_pubkey_odd_y);
    TEST(compress_pubkey_even_y);
    /* Tweaking */
    TEST(privkey_tweak_add);
    TEST(pubkey_tweak_mul);
    TEST(pubkey_tweak_mul_compressed);
    /* ECDH */
    TEST(ecdh_symmetry);
    TEST(ecdh_xonly_symmetric);
    /* Schnorr edge cases */
    TEST(verify_wrong_message);
    TEST(verify_corrupted_sig);
    TEST(sign_deterministic);
    /* Batch */
    TEST(batch_all_valid);
    TEST(batch_with_invalid);
    TEST(batch_empty);
    TEST(batch_single);
    TEST(batch_large);
    /* Tagged hash */
    TEST(tagged_hash_consistency);
    printf("  secp256k1: done\n");
}
