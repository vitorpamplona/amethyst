/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Standalone C benchmark for secp256k1 operations.
 * Mirrors the Kotlin benchmarks for direct comparison.
 *
 * Build: mkdir build && cd build && cmake .. && make
 * Run:   ./secp256k1_bench
 */
#include "schnorr256k1.h"
#include "sha256.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

/* ==================== Timing ==================== */

static double now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1000.0 + ts.tv_nsec / 1.0e6;
}

/* ==================== Test Data (matches Kotlin benchmarks) ==================== */

static const uint8_t TEST_PRIVKEY[32] = {
    0xd2, 0x17, 0xc1, 0xfd, 0x12, 0x40, 0xad, 0x3e,
    0xe6, 0x8f, 0x38, 0xd4, 0xab, 0x4e, 0x6e, 0x95,
    0xf2, 0x0f, 0x3e, 0x09, 0xdd, 0x51, 0x42, 0x90,
    0x00, 0xab, 0xc2, 0xb4, 0xda, 0x5b, 0xe3, 0xa3
};

static const uint8_t TEST_AUXRAND[32] = {
    0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
    0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10,
    0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18,
    0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f, 0x20
};

typedef struct {
    const char *name;
    double total_ms;
    int iterations;
    double ops_per_sec;
} bench_result;

#define MAX_RESULTS 32
static bench_result results[MAX_RESULTS];
static int result_count = 0;

static void record_result(const char *name, double total_ms, int iters) {
    if (result_count >= MAX_RESULTS) return;
    bench_result *r = &results[result_count++];
    r->name = name;
    r->total_ms = total_ms;
    r->iterations = iters;
    r->ops_per_sec = iters / (total_ms / 1000.0);
}

/* ==================== Benchmarks ==================== */

static void bench_pubkey_create(int iters) {
    uint8_t pub65[65];
    double start = now_ms();
    for (int i = 0; i < iters; i++) {
        secp256k1c_pubkey_create(pub65, TEST_PRIVKEY);
    }
    record_result("pubkeyCreate", now_ms() - start, iters);
}

static void bench_sign(int iters) {
    uint8_t msg[32] = {0};
    uint8_t sig[64];
    secp256k1_sha256_hash(msg, (const uint8_t *)"test message", 12);

    double start = now_ms();
    for (int i = 0; i < iters; i++) {
        secp256k1c_schnorr_sign(sig, msg, 32, TEST_PRIVKEY, TEST_AUXRAND);
    }
    record_result("signSchnorr", now_ms() - start, iters);
}

static void bench_sign_xonly(int iters) {
    /* Pre-compute x-only pubkey */
    uint8_t pub65[65];
    secp256k1c_pubkey_create(pub65, TEST_PRIVKEY);
    uint8_t xonly[32];
    memcpy(xonly, pub65 + 1, 32);

    uint8_t msg[32] = {0};
    uint8_t sig[64];
    secp256k1_sha256_hash(msg, (const uint8_t *)"test message", 12);

    double start = now_ms();
    for (int i = 0; i < iters; i++) {
        secp256k1c_schnorr_sign_xonly(sig, msg, 32, TEST_PRIVKEY, xonly, TEST_AUXRAND);
    }
    record_result("signSchnorrXOnly (cached pubkey)", now_ms() - start, iters);
}

static void bench_verify(int iters) {
    /* Create a valid signature */
    uint8_t pub65[65];
    secp256k1c_pubkey_create(pub65, TEST_PRIVKEY);
    uint8_t xonly[32];
    memcpy(xonly, pub65 + 1, 32);

    uint8_t msg[32];
    secp256k1_sha256_hash(msg, (const uint8_t *)"test message for verify", 23);

    uint8_t sig[64];
    secp256k1c_schnorr_sign(sig, msg, 32, TEST_PRIVKEY, TEST_AUXRAND);

    /* Verify it first */
    if (!secp256k1c_schnorr_verify(sig, msg, 32, xonly)) {
        printf("ERROR: Self-verification failed!\n");
        return;
    }

    double start = now_ms();
    for (int i = 0; i < iters; i++) {
        secp256k1c_schnorr_verify(sig, msg, 32, xonly);
    }
    record_result("verifySchnorr", now_ms() - start, iters);
}

static void bench_verify_fast(int iters) {
    uint8_t pub65[65];
    secp256k1c_pubkey_create(pub65, TEST_PRIVKEY);
    uint8_t xonly[32];
    memcpy(xonly, pub65 + 1, 32);

    uint8_t msg[32];
    secp256k1_sha256_hash(msg, (const uint8_t *)"test message for verify fast", 27);

    uint8_t sig[64];
    secp256k1c_schnorr_sign_xonly(sig, msg, 32, TEST_PRIVKEY, xonly, TEST_AUXRAND);

    double start = now_ms();
    for (int i = 0; i < iters; i++) {
        secp256k1c_schnorr_verify_fast(sig, msg, 32, xonly);
    }
    record_result("verifySchnorrFast", now_ms() - start, iters);
}

static void bench_verify_batch(int batch_size, int iters) {
    uint8_t pub65[65];
    secp256k1c_pubkey_create(pub65, TEST_PRIVKEY);
    uint8_t xonly[32];
    memcpy(xonly, pub65 + 1, 32);

    /* Create batch_size different messages and signatures */
    uint8_t **sigs = (uint8_t **)malloc((size_t)batch_size * sizeof(uint8_t *));
    uint8_t **msgs = (uint8_t **)malloc((size_t)batch_size * sizeof(uint8_t *));
    size_t *lens = (size_t *)malloc((size_t)batch_size * sizeof(size_t));

    for (int i = 0; i < batch_size; i++) {
        msgs[i] = (uint8_t *)malloc(32);
        sigs[i] = (uint8_t *)malloc(64);
        lens[i] = 32;

        uint8_t seed[4] = {(uint8_t)i, (uint8_t)(i>>8), (uint8_t)(i>>16), (uint8_t)(i>>24)};
        secp256k1_sha256_hash(msgs[i], seed, 4);
        secp256k1c_schnorr_sign(sigs[i], msgs[i], 32, TEST_PRIVKEY, TEST_AUXRAND);
    }

    const char *name =
        batch_size == 4   ? "verifyBatch(4)" :
        batch_size == 8   ? "verifyBatch(8)" :
        batch_size == 16  ? "verifyBatch(16)" :
        batch_size == 32  ? "verifyBatch(32)" :
        batch_size == 64  ? "verifyBatch(64)" :
        batch_size == 200 ? "verifyBatch(200)" : "verifyBatch(?)";

    double start = now_ms();
    for (int i = 0; i < iters; i++) {
        secp256k1c_schnorr_verify_batch(xonly,
            (const uint8_t *const *)sigs,
            (const uint8_t *const *)msgs,
            lens, (size_t)batch_size);
    }
    record_result(name, now_ms() - start, iters);

    for (int i = 0; i < batch_size; i++) {
        free(msgs[i]);
        free(sigs[i]);
    }
    free(sigs);
    free(msgs);
    free(lens);
}

static void bench_ecdh(int iters) {
    uint8_t pub65[65];
    secp256k1c_pubkey_create(pub65, TEST_PRIVKEY);
    uint8_t xonly[32];
    memcpy(xonly, pub65 + 1, 32);

    /* Use a different key as scalar */
    uint8_t scalar[32];
    secp256k1_sha256_hash(scalar, TEST_PRIVKEY, 32);
    /* Ensure it's a valid scalar */
    scalar[0] &= 0x7F; /* keep below n */

    uint8_t result[32];
    double start = now_ms();
    for (int i = 0; i < iters; i++) {
        secp256k1c_ecdh_xonly(result, xonly, scalar);
    }
    record_result("ecdhXOnly", now_ms() - start, iters);
}

static void bench_seckey_verify(int iters) {
    double start = now_ms();
    for (int i = 0; i < iters; i++) {
        secp256k1c_seckey_verify(TEST_PRIVKEY);
    }
    record_result("secKeyVerify", now_ms() - start, iters);
}

/* ==================== Main ==================== */

int main(void) {
    printf("================================================================\n");
    printf("  Amethyst secp256k1 C Implementation Benchmark\n");
    printf("================================================================\n");
    printf("Initializing precomputed tables...\n");
    double init_start = now_ms();
    secp256k1c_init();
    printf("Initialization: %.1f ms\n\n", now_ms() - init_start);

    /* Warmup */
    printf("Warming up...\n");
    bench_pubkey_create(100);
    bench_sign(100);
    bench_verify(100);
    result_count = 0; /* Reset */

    printf("Running benchmarks...\n\n");

    int N = 5000;

    bench_seckey_verify(N * 100);
    bench_pubkey_create(N);
    bench_sign(N);
    bench_sign_xonly(N);
    bench_verify(N);
    bench_verify_fast(N);
    bench_verify_batch(4, N / 2);
    bench_verify_batch(8, N / 4);
    bench_verify_batch(16, N / 8);
    bench_verify_batch(32, N / 16);
    bench_verify_batch(64, N / 32);
    bench_verify_batch(200, N / 50);
    bench_ecdh(N);

    /* Print results */
    printf("\n%-40s %10s %10s %12s\n", "Operation", "Iters", "Time(ms)", "Ops/sec");
    printf("%-40s %10s %10s %12s\n", "─────────", "─────", "───────", "───────");
    for (int i = 0; i < result_count; i++) {
        bench_result *r = &results[i];
        printf("%-40s %10d %10.1f %12.0f\n",
               r->name, r->iterations, r->total_ms, r->ops_per_sec);
    }

    printf("\n================================================================\n");
    printf("  Per-operation costs (microseconds):\n");
    printf("================================================================\n");
    for (int i = 0; i < result_count; i++) {
        bench_result *r = &results[i];
        double us_per_op = (r->total_ms * 1000.0) / r->iterations;
        printf("  %-38s %8.1f µs\n", r->name, us_per_op);
    }

    return 0;
}
