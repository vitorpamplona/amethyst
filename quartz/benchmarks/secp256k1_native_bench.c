/*
 * Standalone C benchmark for libsecp256k1 — no JVM, no JNI, no ART.
 * Links against the ACINQ secp256k1-kmp-jni .so to benchmark raw C performance.
 * Uses the same test vectors as the Kotlin benchmarks for direct comparison.
 *
 * Build:
 *   # Extract the native .so from the ACINQ JAR:
 *   jar xf ~/.gradle/caches/modules-2/files-2.1/fr.acinq.secp256k1/\
 *     secp256k1-kmp-jni-jvm-linux/0.23.0/*/secp256k1-kmp-jni-jvm-linux-0.23.0.jar
 *   cp fr/acinq/secp256k1/jni/native/linux-x86_64/libsecp256k1-jni.so .
 *
 *   # Compile and run:
 *   gcc -O2 -o bench secp256k1_native_bench.c -L. -lsecp256k1-jni -Wl,-rpath,.
 *   ./bench
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <stdint.h>

typedef struct { unsigned char data[64]; } secp256k1_pubkey;
typedef struct { unsigned char data[64]; } secp256k1_xonly_pubkey;
typedef struct { unsigned char data[96]; } secp256k1_keypair;
typedef struct secp256k1_context_struct secp256k1_context;

/* flags: SIGN=0x201, VERIFY=0x101 */
#define SECP256K1_FLAGS (0x201 | 0x101)
#define SECP256K1_EC_COMPRESSED 258

extern secp256k1_context *secp256k1_context_create(unsigned int flags);
extern void secp256k1_context_destroy(secp256k1_context *ctx);
extern int secp256k1_ec_seckey_verify(const secp256k1_context *ctx, const unsigned char *seckey);
extern int secp256k1_ec_pubkey_create(const secp256k1_context *ctx, secp256k1_pubkey *pubkey, const unsigned char *seckey);
extern int secp256k1_ec_pubkey_serialize(const secp256k1_context *ctx, unsigned char *output, size_t *outputlen, const secp256k1_pubkey *pubkey, unsigned int flags);
extern int secp256k1_schnorrsig_sign32(const secp256k1_context *ctx, unsigned char *sig64, const unsigned char *msg32, const secp256k1_keypair *keypair, const unsigned char *aux_rand32);
extern int secp256k1_schnorrsig_verify(const secp256k1_context *ctx, const unsigned char *sig64, const unsigned char *msg, size_t msglen, const secp256k1_xonly_pubkey *pubkey);
extern int secp256k1_keypair_create(const secp256k1_context *ctx, secp256k1_keypair *keypair, const unsigned char *seckey);
extern int secp256k1_keypair_xonly_pub(const secp256k1_context *ctx, secp256k1_xonly_pubkey *pubkey, int *pk_parity, const secp256k1_keypair *keypair);
extern int secp256k1_ec_seckey_tweak_add(const secp256k1_context *ctx, unsigned char *seckey, const unsigned char *tweak);
extern int secp256k1_ec_pubkey_tweak_mul(const secp256k1_context *ctx, secp256k1_pubkey *pubkey, const unsigned char *tweak32);

static void hex2bin(const char *hex, unsigned char *out, size_t len) {
    for (size_t i = 0; i < len; i++) { unsigned v; sscanf(hex+2*i, "%2x", &v); out[i] = v; }
}

static uint64_t now_ns(void) {
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000000000ULL + ts.tv_nsec;
}

#define BENCH(name, warmup, iters, body) do { \
    for (int _w = 0; _w < (warmup); _w++) { body; } \
    uint64_t _start = now_ns(); \
    for (int _i = 0; _i < (iters); _i++) { body; } \
    uint64_t _el = now_ns() - _start; \
    printf("  %-24s %8llu ns/op  %8llu ops/s\n", name, \
        (unsigned long long)(_el / (iters)), \
        (unsigned long long)((uint64_t)(iters) * 1000000000ULL / _el)); \
} while(0)

int main(void) {
    secp256k1_context *ctx = secp256k1_context_create(SECP256K1_FLAGS);
    unsigned char priv[32], msg[32], aux[32], priv2[32];
    hex2bin("67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530", priv, 32);
    hex2bin("243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89", msg, 32);
    hex2bin("0000000000000000000000000000000000000000000000000000000000000001", aux, 32);
    hex2bin("3982F19BEF1615BCCFBB05E321C10E1D4CBA3DF0E841C2E41EEB6016347653C3", priv2, 32);

    secp256k1_keypair kp; secp256k1_keypair_create(ctx, &kp, priv);
    secp256k1_xonly_pubkey xpub; secp256k1_keypair_xonly_pub(ctx, &xpub, NULL, &kp);
    secp256k1_pubkey pub; secp256k1_ec_pubkey_create(ctx, &pub, priv);
    unsigned char sig[64]; secp256k1_schnorrsig_sign32(ctx, sig, msg, &kp, aux);

    if (!secp256k1_schnorrsig_verify(ctx, sig, msg, 32, &xpub)) {
        fprintf(stderr, "verify failed!\n"); return 1;
    }

    volatile int r;
    printf("================================================================================\n");
    printf("secp256k1 Benchmark: C libsecp256k1 (direct, no JNI/JVM) on x86_64\n");
    printf("================================================================================\n");

    BENCH("verifySchnorr", 2000, 5000,
        r = secp256k1_schnorrsig_verify(ctx, sig, msg, 32, &xpub));

    BENCH("signSchnorr", 1000, 3000,
        secp256k1_schnorrsig_sign32(ctx, sig, msg, &kp, aux));

    { unsigned char c[33]; size_t cl;
    BENCH("compressedPubKeyFor", 1000, 5000, {
        secp256k1_ec_pubkey_create(ctx, &pub, priv);
        cl = 33; secp256k1_ec_pubkey_serialize(ctx, c, &cl, &pub, SECP256K1_EC_COMPRESSED);
    }); }

    BENCH("secKeyVerify", 5000, 200000,
        r = secp256k1_ec_seckey_verify(ctx, priv));

    { unsigned char tw[32];
    BENCH("privKeyTweakAdd", 1000, 50000, {
        memcpy(tw, priv, 32);
        secp256k1_ec_seckey_tweak_add(ctx, tw, priv2);
    }); }

    { secp256k1_pubkey p2; secp256k1_ec_pubkey_create(ctx, &p2, priv2);
    BENCH("ecPubKeyTweakMul", 1000, 3000,
        secp256k1_ec_pubkey_tweak_mul(ctx, &p2, priv));
    }

    printf("================================================================================\n");
    secp256k1_context_destroy(ctx);
    return 0;
}
