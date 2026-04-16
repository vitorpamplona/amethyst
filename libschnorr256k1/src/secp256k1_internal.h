/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
#ifndef SECP256K1_INTERNAL_H
#define SECP256K1_INTERNAL_H

#include <stdint.h>
#include <stddef.h>
#include <string.h>

/*
 * libschnorr256k1 internal types and platform detection.
 *
 * This header defines the internal data structures (field elements, scalars,
 * points) and platform-specific macros. It is NOT part of the public API.
 *
 * Key design choices:
 *   - 4x64-bit limbs, fully packed, little-endian
 *   - Native __int128 for 64x64->128 multiply (single MULQ/MUL instruction)
 *   - Platform-specific ASM for field multiply on ARM64 (UMULH, UMULL)
 *   - Precomputed tables at compile time (no lazy init overhead)
 *   - Batch verification with randomized linear combination
 */

/* ==================== Platform Detection ==================== */

#if defined(__SIZEOF_INT128__)
    #define HAVE_INT128 1
    typedef unsigned __int128 uint128_t;
#else
    #define HAVE_INT128 0
#endif

#if defined(__aarch64__) || defined(_M_ARM64)
    #define SECP_ARM64 1
#else
    #define SECP_ARM64 0
#endif

#if defined(__x86_64__) || defined(_M_X64)
    #define SECP_X86_64 1
#else
    #define SECP_X86_64 0
#endif

/* ==================== Field Element (4x64-bit limbs) ==================== */

/*
 * Field element modulo p = 2^256 - 2^32 - 977.
 * 4 limbs of 64 bits each, fully packed, little-endian.
 */
typedef struct {
    uint64_t d[4];
} secp256k1_fe;

/* ==================== Scalar (mod n) ==================== */

typedef struct {
    uint64_t d[4]; /* 4x64-bit limbs, little-endian */
} secp256k1_scalar;

/* ==================== Points ==================== */

/* Jacobian point: affine (X/Z^2, Y/Z^3) */
typedef struct {
    secp256k1_fe x;
    secp256k1_fe y;
    secp256k1_fe z;
    int infinity;
} secp256k1_gej;

/* Affine point */
typedef struct {
    secp256k1_fe x;
    secp256k1_fe y;
} secp256k1_ge;

/* ==================== Public API (see include/schnorr256k1.h) ==================== */

#include "schnorr256k1.h"

#endif /* SECP256K1_INTERNAL_H */
