/*
 * Shared test framework declarations.
 * Included by all test_*.c files.
 */
#ifndef TEST_FRAMEWORK_H
#define TEST_FRAMEWORK_H

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

/* Defined in test_main.c */
extern int tests_run;
extern int tests_failed;
extern int current_failed;
extern const char *current_test;
extern const char *current_suite;

size_t hex_to_bytes(uint8_t *out, const char *hex, size_t max_len);

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

/* Helper: convert hex to fixed-size byte array on the stack */
#define HEX32(var, hex_str) \
    uint8_t var[32]; hex_to_bytes(var, hex_str, 32)

#define HEX33(var, hex_str) \
    uint8_t var[33]; hex_to_bytes(var, hex_str, 33)

#define HEX64(var, hex_str) \
    uint8_t var[64]; hex_to_bytes(var, hex_str, 64)

#define HEX65(var, hex_str) \
    uint8_t var[65]; hex_to_bytes(var, hex_str, 65)

#endif /* TEST_FRAMEWORK_H */
