/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Minimal test framework for libschnorr256k1.
 * No external dependencies — just compile and run.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

#include "schnorr256k1.h"

/* ==================== Test Framework ==================== */

int tests_run = 0;
int tests_failed = 0;
int current_failed = 0;
const char *current_test = NULL;
const char *current_suite = NULL;

#define TEST(name) do { \
    current_test = #name; \
    current_failed = 0; \
    tests_run++; \
    name(); \
    if (current_failed) { tests_failed++; } \
} while(0)

#define ASSERT_TRUE(cond) do { \
    if (!(cond)) { \
        printf("  FAIL: %s:%s (line %d): %s\n", current_suite, current_test, __LINE__, #cond); \
        current_failed = 1; return; \
    } \
} while(0)

#define ASSERT_FALSE(cond) ASSERT_TRUE(!(cond))

#define ASSERT_EQ_INT(a, b) do { \
    long long _a = (long long)(a), _b = (long long)(b); \
    if (_a != _b) { \
        printf("  FAIL: %s:%s (line %d): %lld != %lld\n", current_suite, current_test, __LINE__, _a, _b); \
        current_failed = 1; return; \
    } \
} while(0)

#define ASSERT_MEM_EQ(a, b, len) do { \
    if (memcmp(a, b, len) != 0) { \
        printf("  FAIL: %s:%s (line %d): memory mismatch (%zu bytes)\n", current_suite, current_test, __LINE__, (size_t)(len)); \
        current_failed = 1; return; \
    } \
} while(0)

#define ASSERT_HEX_EQ(bytes, hex_str, len) do { \
    uint8_t _expected[256]; \
    hex_to_bytes(_expected, hex_str, sizeof(_expected)); \
    if (memcmp(bytes, _expected, len) != 0) { \
        printf("  FAIL: %s:%s (line %d): hex mismatch\n    got: ", current_suite, current_test, __LINE__); \
        for (size_t _i = 0; _i < (size_t)(len); _i++) printf("%02x", ((const uint8_t*)(bytes))[_i]); \
        printf("\n    exp: %s\n", hex_str); \
        current_failed = 1; return; \
    } \
} while(0)

#define SUITE(name) current_suite = #name

/* ==================== Hex Utility ==================== */

static int hex_char(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

size_t hex_to_bytes(uint8_t *out, const char *hex, size_t max_len) {
    size_t len = strlen(hex);
    if (len % 2 != 0) return 0;
    size_t byte_len = len / 2;
    if (byte_len > max_len) return 0;
    for (size_t i = 0; i < byte_len; i++) {
        int hi = hex_char(hex[2 * i]);
        int lo = hex_char(hex[2 * i + 1]);
        if (hi < 0 || lo < 0) return 0;
        out[i] = (uint8_t)((hi << 4) | lo);
    }
    return byte_len;
}

/* ==================== External Test Suites ==================== */

extern void run_schnorr_tests(void);
extern void run_secp256k1_tests(void);
extern void run_field_tests(void);
extern void run_point_tests(void);
extern void run_key_codec_tests(void);
extern void run_sha256_tests(void);

/* ==================== Main ==================== */

int main(void) {
    printf("================================================================\n");
    printf("  libschnorr256k1 Test Suite\n");
    printf("================================================================\n\n");

    printf("Initializing...\n");
    secp256k1c_init();
    printf("Ready.\n\n");

    run_sha256_tests();
    run_field_tests();
    run_point_tests();
    run_key_codec_tests();
    run_schnorr_tests();
    run_secp256k1_tests();

    printf("\n================================================================\n");
    if (tests_failed == 0) {
        printf("  ALL %d TESTS PASSED\n", tests_run);
    } else {
        printf("  %d / %d TESTS FAILED\n", tests_failed, tests_run);
    }
    printf("================================================================\n");

    return tests_failed > 0 ? 1 : 0;
}
