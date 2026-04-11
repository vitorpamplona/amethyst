/*
 * Native C-to-C benchmark: Our secp256k1 vs ACINQ's libsecp256k1
 * No JNI, no JVM — pure native comparison on the same machine.
 *
 * Build:
 *   cd quartz/src/main/c/secp256k1/build
 *   gcc -O2 -march=x86-64-v2 -mbmi2 -I.. bench_vs_acinq.c \
 *       -L. -lsecp256k1_amethyst \
 *       -L/tmp/acinq_secp/fr/acinq/secp256k1/jni/native/linux-x86_64 \
 *       -l:libsecp256k1-jni.so \
 *       -Wl,-rpath,/tmp/acinq_secp/fr/acinq/secp256k1/jni/native/linux-x86_64 \
 *       -o bench_vs_acinq -lm
 */
#include "secp256k1_c.h"
#include "sha256.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

/* ==================== ACINQ libsecp256k1 API declarations ==================== */

typedef struct secp256k1_context_struct secp256k1_context;
typedef struct { unsigned char data[64]; } secp256k1_pubkey;
typedef struct { unsigned char data[64]; } secp256k1_xonly_pubkey;
typedef struct { unsigned char data[96]; } secp256k1_keypair;

#define SECP256K1_CONTEXT_NONE 0
#define SECP256K1_CONTEXT_SIGN 0x201
#define SECP256K1_CONTEXT_VERIFY 0x101

extern secp256k1_context *secp256k1_context_create(unsigned int flags);
extern void secp256k1_context_destroy(secp256k1_context *ctx);
extern int secp256k1_ec_pubkey_create(const secp256k1_context *ctx,
    secp256k1_pubkey *pubkey, const unsigned char *seckey);
extern int secp256k1_ec_pubkey_serialize(const secp256k1_context *ctx,
    unsigned char *output, size_t *outputlen, const secp256k1_pubkey *pubkey,
    unsigned int flags);
extern int secp256k1_keypair_create(const secp256k1_context *ctx,
    secp256k1_keypair *keypair, const unsigned char *seckey);
extern int secp256k1_keypair_xonly_pub(const secp256k1_context *ctx,
    secp256k1_xonly_pubkey *pubkey, int *pk_parity, const secp256k1_keypair *keypair);
extern int secp256k1_xonly_pubkey_serialize(const secp256k1_context *ctx,
    unsigned char *output32, const secp256k1_xonly_pubkey *pubkey);
extern int secp256k1_xonly_pubkey_parse(const secp256k1_context *ctx,
    secp256k1_xonly_pubkey *pubkey, const unsigned char *input32);
extern int secp256k1_schnorrsig_sign32(const secp256k1_context *ctx,
    unsigned char *sig64, const unsigned char *msg32,
    const secp256k1_keypair *keypair, const unsigned char *aux_rand32);
extern int secp256k1_schnorrsig_verify(const secp256k1_context *ctx,
    const unsigned char *sig64, const unsigned char *msg,
    size_t msglen, const secp256k1_xonly_pubkey *pubkey);
extern int secp256k1_ec_pubkey_tweak_mul(const secp256k1_context *ctx,
    secp256k1_pubkey *pubkey, const unsigned char *tweak32);
extern int secp256k1_ec_pubkey_parse(const secp256k1_context *ctx,
    secp256k1_pubkey *pubkey, const unsigned char *input, size_t inputlen);

#define SECP256K1_EC_COMPRESSED 258

/* ==================== Timing ==================== */

static double now_us(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1e6 + ts.tv_nsec / 1e3;
}

/* ==================== Test data ==================== */

static const uint8_t PRIVKEY[32] = {
    0xd2,0x17,0xc1,0xfd,0x12,0x40,0xad,0x3e,0xe6,0x8f,0x38,0xd4,0xab,0x4e,0x6e,0x95,
    0xf2,0x0f,0x3e,0x09,0xdd,0x51,0x42,0x90,0x00,0xab,0xc2,0xb4,0xda,0x5b,0xe3,0xa3
};

int main(void) {
    printf("================================================================\n");
    printf("  Native C-to-C Benchmark: Ours vs ACINQ libsecp256k1\n");
    printf("  No JNI, no JVM — pure native performance comparison\n");
    printf("================================================================\n\n");

    /* ===== Init ===== */
    secp256k1c_init();
    secp256k1_context *acinq_ctx = secp256k1_context_create(
        SECP256K1_CONTEXT_SIGN | SECP256K1_CONTEXT_VERIFY);

    /* ===== Setup: create keypairs and signatures for both ===== */

    /* ACINQ setup */
    secp256k1_keypair acinq_kp;
    secp256k1_keypair_create(acinq_ctx, &acinq_kp, PRIVKEY);
    secp256k1_xonly_pubkey acinq_xonly;
    secp256k1_keypair_xonly_pub(acinq_ctx, &acinq_xonly, NULL, &acinq_kp);
    uint8_t acinq_xonly_bytes[32];
    secp256k1_xonly_pubkey_serialize(acinq_ctx, acinq_xonly_bytes, &acinq_xonly);

    /* Our setup */
    uint8_t our_pub65[65];
    secp256k1c_pubkey_create(our_pub65, PRIVKEY);
    uint8_t our_xonly[32];
    memcpy(our_xonly, our_pub65 + 1, 32);

    /* Messages */
    uint8_t msg[32];
    secp256k1_sha256_hash(msg, (const uint8_t *)"benchmark msg", 13);

    /* Sign with both */
    uint8_t acinq_sig[64], our_sig[64];
    secp256k1_schnorrsig_sign32(acinq_ctx, acinq_sig, msg, &acinq_kp, NULL);
    secp256k1c_schnorr_sign(our_sig, msg, 32, PRIVKEY, NULL);

    /* Cross-verify: make sure both produce valid signatures */
    int acinq_verify_ours = secp256k1_schnorrsig_verify(acinq_ctx, our_sig, msg, 32, &acinq_xonly);
    int our_verify_acinq = secp256k1c_schnorr_verify_fast(acinq_sig, msg, 32, our_xonly);
    printf("Cross-verification: ACINQ verifies ours=%d, We verify ACINQ=%d\n\n",
           acinq_verify_ours, our_verify_acinq);

    if (!acinq_verify_ours || !our_verify_acinq) {
        printf("ERROR: Cross-verification failed!\n");
        /* Continue anyway to get timing data */
    }

    /* ===== Benchmarks ===== */

    int N;
    double t0, t1;

    printf("%-30s %12s %12s %10s\n", "Operation", "ACINQ (µs)", "Ours (µs)", "Speedup");
    printf("─────────────────────────────────────────────────────────────────\n");

    /* --- pubkeyCreate --- */
    N = 5000;
    t0 = now_us();
    for (int i = 0; i < N; i++) {
        secp256k1_pubkey pk;
        secp256k1_ec_pubkey_create(acinq_ctx, &pk, PRIVKEY);
    }
    double acinq_pubkey = (now_us() - t0) / N;

    t0 = now_us();
    for (int i = 0; i < N; i++) {
        uint8_t p65[65];
        secp256k1c_pubkey_create(p65, PRIVKEY);
    }
    double our_pubkey = (now_us() - t0) / N;
    printf("%-30s %10.1f   %10.1f   %8.2fx\n", "pubkeyCreate", acinq_pubkey, our_pubkey, acinq_pubkey/our_pubkey);

    /* --- signSchnorr --- */
    N = 5000;
    t0 = now_us();
    for (int i = 0; i < N; i++) {
        uint8_t s[64];
        secp256k1_schnorrsig_sign32(acinq_ctx, s, msg, &acinq_kp, NULL);
    }
    double acinq_sign = (now_us() - t0) / N;

    t0 = now_us();
    for (int i = 0; i < N; i++) {
        uint8_t s[64];
        secp256k1c_schnorr_sign(s, msg, 32, PRIVKEY, NULL);
    }
    double our_sign = (now_us() - t0) / N;
    printf("%-30s %10.1f   %10.1f   %8.2fx\n", "signSchnorr", acinq_sign, our_sign, acinq_sign/our_sign);

    /* --- verifySchnorr (ACINQ = always full BIP-340) --- */
    N = 5000;
    /* Warmup */
    for (int i = 0; i < 1000; i++) {
        secp256k1_schnorrsig_verify(acinq_ctx, acinq_sig, msg, 32, &acinq_xonly);
        secp256k1c_schnorr_verify_fast(our_sig, msg, 32, our_xonly);
    }

    t0 = now_us();
    for (int i = 0; i < N; i++) {
        secp256k1_schnorrsig_verify(acinq_ctx, acinq_sig, msg, 32, &acinq_xonly);
    }
    double acinq_verify = (now_us() - t0) / N;

    t0 = now_us();
    for (int i = 0; i < N; i++) {
        secp256k1c_schnorr_verify_fast(our_sig, msg, 32, our_xonly);
    }
    double our_verify_fast = (now_us() - t0) / N;

    t0 = now_us();
    for (int i = 0; i < N; i++) {
        secp256k1c_schnorr_verify(our_sig, msg, 32, our_xonly);
    }
    double our_verify_full = (now_us() - t0) / N;
    printf("%-30s %10.1f   %10.1f   %8.2fx\n", "verify (ACINQ=BIP340)", acinq_verify, our_verify_full, acinq_verify/our_verify_full);
    printf("%-30s %10.1f   %10.1f   %8.2fx\n", "verifyFast (Nostr safe)", acinq_verify, our_verify_fast, acinq_verify/our_verify_fast);

    /* --- ECDH (pubKeyTweakMul) --- */
    N = 3000;
    /* ACINQ: parse pubkey, tweak_mul, serialize */
    uint8_t compressed_pub[33];
    compressed_pub[0] = 0x02;
    memcpy(compressed_pub + 1, our_xonly, 32);

    t0 = now_us();
    for (int i = 0; i < N; i++) {
        secp256k1_pubkey pk;
        secp256k1_ec_pubkey_parse(acinq_ctx, &pk, compressed_pub, 33);
        secp256k1_ec_pubkey_tweak_mul(acinq_ctx, &pk, PRIVKEY);
        uint8_t out[33]; size_t outlen = 33;
        secp256k1_ec_pubkey_serialize(acinq_ctx, out, &outlen, &pk, SECP256K1_EC_COMPRESSED);
    }
    double acinq_ecdh = (now_us() - t0) / N;

    t0 = now_us();
    for (int i = 0; i < N; i++) {
        uint8_t result[32];
        secp256k1c_ecdh_xonly(result, our_xonly, PRIVKEY);
    }
    double our_ecdh = (now_us() - t0) / N;
    printf("%-30s %10.1f   %10.1f   %8.2fx\n", "ECDH (cached pubkey)", acinq_ecdh, our_ecdh, acinq_ecdh/our_ecdh);

    /* --- Batch verify (ours only — ACINQ has no batch API) --- */
    printf("\n%-30s %12s %12s %10s\n", "Batch Verify", "ACINQ indiv", "Ours batch", "Speedup");
    printf("─────────────────────────────────────────────────────────────────\n");

    for (int batch_size = 4; batch_size <= 200; batch_size = (batch_size < 64) ? batch_size * 2 : 200) {
        uint8_t **sigs = malloc(batch_size * sizeof(uint8_t*));
        uint8_t **msgs_arr = malloc(batch_size * sizeof(uint8_t*));
        size_t *lens = malloc(batch_size * sizeof(size_t));
        for (int i = 0; i < batch_size; i++) {
            sigs[i] = malloc(64);
            msgs_arr[i] = malloc(32);
            lens[i] = 32;
            uint8_t seed[4] = {(uint8_t)i, (uint8_t)(i>>8), 0, 0};
            secp256k1_sha256_hash(msgs_arr[i], seed, 4);
            secp256k1c_schnorr_sign(sigs[i], msgs_arr[i], 32, PRIVKEY, NULL);
        }

        /* Parse pubkey for ACINQ individual verify */
        secp256k1_xonly_pubkey acinq_xpk;
        secp256k1_xonly_pubkey_parse(acinq_ctx, &acinq_xpk, our_xonly);

        int iters = (batch_size <= 16) ? 1000 : (batch_size <= 64) ? 300 : 100;

        /* ACINQ individual */
        t0 = now_us();
        for (int it = 0; it < iters; it++) {
            for (int i = 0; i < batch_size; i++) {
                secp256k1_schnorrsig_verify(acinq_ctx, sigs[i], msgs_arr[i], 32, &acinq_xpk);
            }
        }
        double acinq_indiv = (now_us() - t0) / (iters * batch_size);

        /* Ours batch */
        t0 = now_us();
        for (int it = 0; it < iters; it++) {
            secp256k1c_schnorr_verify_batch(our_xonly,
                (const uint8_t *const *)sigs, (const uint8_t *const *)msgs_arr, lens, batch_size);
        }
        double our_batch = (now_us() - t0) / (iters * batch_size);

        printf("  batch(%3d)                  %10.1f   %10.1f   %8.1fx\n",
            batch_size, acinq_indiv, our_batch, acinq_indiv / our_batch);

        for (int i = 0; i < batch_size; i++) { free(sigs[i]); free(msgs_arr[i]); }
        free(sigs); free(msgs_arr); free(lens);
        if (batch_size == 200) break;
        if (batch_size == 64) batch_size = 100; /* next iteration will be 200 */
    }

    printf("\n================================================================\n");
    secp256k1_context_destroy(acinq_ctx);
    return 0;
}
