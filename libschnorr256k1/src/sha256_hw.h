/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Hardware-accelerated SHA-256 using platform crypto extensions.
 *
 * x86_64: SHA-NI (Intel Goldmont+, AMD Zen+)
 *   SHA256RNDS2, SHA256MSG1, SHA256MSG2 — 4 rounds per instruction
 *
 * ARM64: Crypto Extensions (all ARMv8.0-A Android phones)
 *   SHA256H, SHA256H2, SHA256SU0, SHA256SU1 — 4 rounds per instruction
 *
 * Both achieve ~100-150ns per 64-byte block vs ~800ns in software.
 * For BIP-340 tagged hashes (96-160 bytes), this saves ~0.5-1µs per hash.
 */
#ifndef SECP256K1_SHA256_HW_H
#define SECP256K1_SHA256_HW_H

#include <stdint.h>
#include <string.h>

/* ==================== x86_64 SHA-NI ==================== */

#if defined(__x86_64__) && defined(__SHA__)

#include <immintrin.h>

static inline void sha256_transform_hw(uint32_t state[8], const uint8_t block[64]) {
    const __m128i MASK = _mm_set_epi64x(0x0c0d0e0f08090a0bULL, 0x0405060700010203ULL);

    /* Load state */
    __m128i STATE0 = _mm_loadu_si128((const __m128i*)&state[0]);
    __m128i STATE1 = _mm_loadu_si128((const __m128i*)&state[4]);

    /* Shuffle for SHA-NI format: STATE0=[A,B,E,F], STATE1=[C,D,G,H] */
    __m128i TMP = _mm_shuffle_epi32(STATE0, 0xB1); /* [B,A,F,E] */
    STATE1 = _mm_shuffle_epi32(STATE1, 0x1B);       /* [H,G,D,C] */
    STATE0 = _mm_alignr_epi8(TMP, STATE1, 8);       /* [A,B,E,F] */
    STATE1 = _mm_blend_epi16(STATE1, TMP, 0xF0);    /* [C,D,G,H] */

    __m128i ABEF_SAVE = STATE0;
    __m128i CDGH_SAVE = STATE1;

    /* Load message */
    __m128i MSG0 = _mm_shuffle_epi8(_mm_loadu_si128((const __m128i*)(block +  0)), MASK);
    __m128i MSG1 = _mm_shuffle_epi8(_mm_loadu_si128((const __m128i*)(block + 16)), MASK);
    __m128i MSG2 = _mm_shuffle_epi8(_mm_loadu_si128((const __m128i*)(block + 32)), MASK);
    __m128i MSG3 = _mm_shuffle_epi8(_mm_loadu_si128((const __m128i*)(block + 48)), MASK);

    static const uint32_t K[64] __attribute__((aligned(16))) = {
        0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
        0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
        0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
        0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
        0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
        0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
        0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
        0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
    };

    __m128i MSG;

    /* Rounds 0-3 */
    MSG = _mm_add_epi32(MSG0, _mm_load_si128((const __m128i*)&K[0]));
    STATE1 = _mm_sha256rnds2_epu32(STATE1, STATE0, MSG);
    MSG = _mm_shuffle_epi32(MSG, 0x0E);
    STATE0 = _mm_sha256rnds2_epu32(STATE0, STATE1, MSG);

    /* Rounds 4-7 */
    MSG = _mm_add_epi32(MSG1, _mm_load_si128((const __m128i*)&K[4]));
    STATE1 = _mm_sha256rnds2_epu32(STATE1, STATE0, MSG);
    MSG = _mm_shuffle_epi32(MSG, 0x0E);
    STATE0 = _mm_sha256rnds2_epu32(STATE0, STATE1, MSG);
    MSG0 = _mm_sha256msg1_epu32(MSG0, MSG1);

    /* Rounds 8-11 */
    MSG = _mm_add_epi32(MSG2, _mm_load_si128((const __m128i*)&K[8]));
    STATE1 = _mm_sha256rnds2_epu32(STATE1, STATE0, MSG);
    MSG = _mm_shuffle_epi32(MSG, 0x0E);
    STATE0 = _mm_sha256rnds2_epu32(STATE0, STATE1, MSG);
    MSG1 = _mm_sha256msg1_epu32(MSG1, MSG2);

    /* Rounds 12-15 */
    MSG = _mm_add_epi32(MSG3, _mm_load_si128((const __m128i*)&K[12]));
    STATE1 = _mm_sha256rnds2_epu32(STATE1, STATE0, MSG);
    __m128i TMP2 = _mm_alignr_epi8(MSG3, MSG2, 4);
    MSG0 = _mm_add_epi32(MSG0, TMP2);
    MSG0 = _mm_sha256msg2_epu32(MSG0, MSG3);
    MSG = _mm_shuffle_epi32(MSG, 0x0E);
    STATE0 = _mm_sha256rnds2_epu32(STATE0, STATE1, MSG);
    MSG2 = _mm_sha256msg1_epu32(MSG2, MSG3);

    /* Rounds 16-19 through 60-63 (unrolled loop) */
    #define SHA_ROUND(i, m0, m1, m2, m3) do { \
        MSG = _mm_add_epi32(m0, _mm_load_si128((const __m128i*)&K[i])); \
        STATE1 = _mm_sha256rnds2_epu32(STATE1, STATE0, MSG); \
        TMP2 = _mm_alignr_epi8(m0, m3, 4); \
        m1 = _mm_add_epi32(m1, TMP2); \
        m1 = _mm_sha256msg2_epu32(m1, m0); \
        MSG = _mm_shuffle_epi32(MSG, 0x0E); \
        STATE0 = _mm_sha256rnds2_epu32(STATE0, STATE1, MSG); \
        m3 = _mm_sha256msg1_epu32(m3, m0); \
    } while(0)

    SHA_ROUND(16, MSG0, MSG1, MSG2, MSG3);
    SHA_ROUND(20, MSG1, MSG2, MSG3, MSG0);
    SHA_ROUND(24, MSG2, MSG3, MSG0, MSG1);
    SHA_ROUND(28, MSG3, MSG0, MSG1, MSG2);
    SHA_ROUND(32, MSG0, MSG1, MSG2, MSG3);
    SHA_ROUND(36, MSG1, MSG2, MSG3, MSG0);
    SHA_ROUND(40, MSG2, MSG3, MSG0, MSG1);
    SHA_ROUND(44, MSG3, MSG0, MSG1, MSG2);
    SHA_ROUND(48, MSG0, MSG1, MSG2, MSG3);
    SHA_ROUND(52, MSG1, MSG2, MSG3, MSG0);

    #undef SHA_ROUND

    /* Rounds 56-59 */
    MSG = _mm_add_epi32(MSG2, _mm_load_si128((const __m128i*)&K[56]));
    STATE1 = _mm_sha256rnds2_epu32(STATE1, STATE0, MSG);
    TMP2 = _mm_alignr_epi8(MSG2, MSG1, 4);
    MSG3 = _mm_add_epi32(MSG3, TMP2);
    MSG3 = _mm_sha256msg2_epu32(MSG3, MSG2);
    MSG = _mm_shuffle_epi32(MSG, 0x0E);
    STATE0 = _mm_sha256rnds2_epu32(STATE0, STATE1, MSG);

    /* Rounds 60-63 */
    MSG = _mm_add_epi32(MSG3, _mm_load_si128((const __m128i*)&K[60]));
    STATE1 = _mm_sha256rnds2_epu32(STATE1, STATE0, MSG);
    MSG = _mm_shuffle_epi32(MSG, 0x0E);
    STATE0 = _mm_sha256rnds2_epu32(STATE0, STATE1, MSG);

    /* Add saved state */
    STATE0 = _mm_add_epi32(STATE0, ABEF_SAVE);
    STATE1 = _mm_add_epi32(STATE1, CDGH_SAVE);

    /* Unshuffle */
    TMP = _mm_shuffle_epi32(STATE0, 0x1B);          /* [F,E,B,A] */
    STATE1 = _mm_shuffle_epi32(STATE1, 0xB1);       /* [D,C,H,G] */
    STATE0 = _mm_blend_epi16(TMP, STATE1, 0xF0);    /* [A,B,C,D] */
    STATE1 = _mm_alignr_epi8(STATE1, TMP, 8);       /* [E,F,G,H] */

    _mm_storeu_si128((__m128i*)&state[0], STATE0);
    _mm_storeu_si128((__m128i*)&state[4], STATE1);
}

#define SHA256_HW_AVAILABLE 1

#elif defined(__aarch64__) && defined(__ARM_FEATURE_CRYPTO)

#include <arm_neon.h>

static inline void sha256_transform_hw(uint32_t state[8], const uint8_t block[64]) {
    static const uint32_t K[64] = {
        0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
        0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
        0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
        0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
        0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
        0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
        0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
        0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
    };

    /* Load state: ABCD and EFGH */
    uint32x4_t STATE0 = vld1q_u32(&state[0]);
    uint32x4_t STATE1 = vld1q_u32(&state[4]);
    uint32x4_t ABCD_SAVE = STATE0;
    uint32x4_t EFGH_SAVE = STATE1;

    /* Load message with big-endian byte swap */
    uint32x4_t MSG0 = vreinterpretq_u32_u8(vrev32q_u8(vld1q_u8(block +  0)));
    uint32x4_t MSG1 = vreinterpretq_u32_u8(vrev32q_u8(vld1q_u8(block + 16)));
    uint32x4_t MSG2 = vreinterpretq_u32_u8(vrev32q_u8(vld1q_u8(block + 32)));
    uint32x4_t MSG3 = vreinterpretq_u32_u8(vrev32q_u8(vld1q_u8(block + 48)));

    uint32x4_t TMP0, TMP1, TMP2;

    /* Rounds 0-3 */
    TMP0 = vaddq_u32(MSG0, vld1q_u32(&K[0]));
    TMP2 = STATE0;
    STATE0 = vsha256hq_u32(STATE0, STATE1, TMP0);
    STATE1 = vsha256h2q_u32(STATE1, TMP2, TMP0);
    MSG0 = vsha256su0q_u32(MSG0, MSG1);

    /* Rounds 4-7 */
    TMP0 = vaddq_u32(MSG1, vld1q_u32(&K[4]));
    TMP2 = STATE0;
    STATE0 = vsha256hq_u32(STATE0, STATE1, TMP0);
    STATE1 = vsha256h2q_u32(STATE1, TMP2, TMP0);
    MSG0 = vsha256su1q_u32(MSG0, MSG2, MSG3);
    MSG1 = vsha256su0q_u32(MSG1, MSG2);

    #define ARM_SHA_ROUND(i, m0, m1, m2, m3) do { \
        TMP0 = vaddq_u32(m2, vld1q_u32(&K[i])); \
        TMP2 = STATE0; \
        STATE0 = vsha256hq_u32(STATE0, STATE1, TMP0); \
        STATE1 = vsha256h2q_u32(STATE1, TMP2, TMP0); \
        m1 = vsha256su1q_u32(m1, m3, m0); \
        m2 = vsha256su0q_u32(m2, m3); \
    } while(0)

    ARM_SHA_ROUND( 8, MSG0, MSG1, MSG2, MSG3);
    ARM_SHA_ROUND(12, MSG1, MSG2, MSG3, MSG0);
    ARM_SHA_ROUND(16, MSG2, MSG3, MSG0, MSG1);
    ARM_SHA_ROUND(20, MSG3, MSG0, MSG1, MSG2);
    ARM_SHA_ROUND(24, MSG0, MSG1, MSG2, MSG3);
    ARM_SHA_ROUND(28, MSG1, MSG2, MSG3, MSG0);
    ARM_SHA_ROUND(32, MSG2, MSG3, MSG0, MSG1);
    ARM_SHA_ROUND(36, MSG3, MSG0, MSG1, MSG2);
    ARM_SHA_ROUND(40, MSG0, MSG1, MSG2, MSG3);
    ARM_SHA_ROUND(44, MSG1, MSG2, MSG3, MSG0);
    ARM_SHA_ROUND(48, MSG2, MSG3, MSG0, MSG1);

    #undef ARM_SHA_ROUND

    /* Rounds 52-55 */
    TMP0 = vaddq_u32(MSG3, vld1q_u32(&K[52]));
    TMP2 = STATE0;
    STATE0 = vsha256hq_u32(STATE0, STATE1, TMP0);
    STATE1 = vsha256h2q_u32(STATE1, TMP2, TMP0);
    MSG0 = vsha256su1q_u32(MSG0, MSG2, MSG3);

    /* Rounds 56-59 */
    TMP0 = vaddq_u32(MSG0, vld1q_u32(&K[56]));
    TMP2 = STATE0;
    STATE0 = vsha256hq_u32(STATE0, STATE1, TMP0);
    STATE1 = vsha256h2q_u32(STATE1, TMP2, TMP0);

    /* Rounds 60-63 */
    TMP0 = vaddq_u32(MSG1, vld1q_u32(&K[60]));
    TMP2 = STATE0;
    STATE0 = vsha256hq_u32(STATE0, STATE1, TMP0);
    STATE1 = vsha256h2q_u32(STATE1, TMP2, TMP0);

    /* Add saved state */
    STATE0 = vaddq_u32(STATE0, ABCD_SAVE);
    STATE1 = vaddq_u32(STATE1, EFGH_SAVE);

    vst1q_u32(&state[0], STATE0);
    vst1q_u32(&state[4], STATE1);
}

#define SHA256_HW_AVAILABLE 1

#else
#define SHA256_HW_AVAILABLE 0
#endif

#endif /* SECP256K1_SHA256_HW_H */
