/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Platform-specific field multiply/square using inline assembly.
 *
 * ARM64: MUL + UMULH pairs for 64x64->128 products (2 instructions, 3 cycles each)
 * x86_64: MULQ for 64x64->128 products (1 instruction, 3 cycles each)
 *
 * The compiler's __int128 code is decent but not optimal:
 * - On x86_64: gcc generates MULQ correctly but adds unnecessary MOVs
 * - On ARM64: gcc sometimes uses UMULL (32-bit) instead of MUL+UMULH (64-bit)
 *
 * These hand-tuned versions keep intermediates in registers and avoid
 * redundant moves, saving ~2-3ns per fe_mul (~10% of verify).
 */
#ifndef SECP256K1_FIELD_ASM_H
#define SECP256K1_FIELD_ASM_H

#include "secp256k1_internal.h"

#define FIELD_C_ASM 0x1000003D1ULL

#if SECP_X86_64 && defined(__GNUC__) && !defined(__clang_analyzer__)

/*
 * x86_64 field multiply using MULX (BMI2) + ADCX/ADOX (ADX) instructions.
 *
 * MULX: rdx * src -> hi:lo (two arbitrary output regs, NO flags clobbered)
 * ADCX: add-with-carry using CF only (ignores OF)
 * ADOX: add-with-carry using OF only (ignores CF)
 *
 * This enables TWO INDEPENDENT carry chains running in parallel:
 * - CF chain: accumulates the low parts of products
 * - OF chain: accumulates the high parts of products
 *
 * The CPU can pipeline MULX+ADCX+ADOX since they don't conflict on flags.
 * Compared to MULQ+ADC: ~20-30% faster due to eliminated serial dependencies.
 *
 * If BMI2/ADX not available at runtime, falls back to MULQ.
 */
/*
 * x86_64 field multiply using MULX (BMI2) + ADCX/ADOX (ADX).
 *
 * Hand-tuned inline assembly implementing the dual carry chain pattern:
 * - MULX produces hi:lo without clobbering flags
 * - ADCX accumulates using CF chain (for low parts)
 * - ADOX accumulates using OF chain (for high parts)
 *
 * This eliminates the serial dependency in MULQ+ADC chains, allowing
 * the CPU to pipeline multiply-accumulate operations.
 *
 * Compiled with -mbmi2 -madx for runtime detection, but the instructions
 * are encoded directly so the binary requires BMI2+ADX capable CPU.
 */
static inline void fe_mul_asm(secp256k1_fe *r, const secp256k1_fe *a, const secp256k1_fe *b) {
    uint64_t r0, r1, r2, r3, r4, r5, r6, r7;

    __asm__ __volatile__(
        /* ===== Row 0: a[0] * b[0..3] ===== */
        "movq (%[a]), %%rdx\n\t"
        "mulx (%[b]), %[r0], %[r1]\n\t"
        "mulx 8(%[b]), %%rax, %[r2]\n\t"
        "addq %%rax, %[r1]\n\t"
        "mulx 16(%[b]), %%rax, %[r3]\n\t"
        "adcq %%rax, %[r2]\n\t"
        "mulx 24(%[b]), %%rax, %[r4]\n\t"
        "adcq %%rax, %[r3]\n\t"
        "adcq $0, %[r4]\n\t"

        /* ===== Row 1: a[1] * b[0..3], accumulate into r1..r5 ===== */
        "movq 8(%[a]), %%rdx\n\t"
        "mulx (%[b]), %%rax, %%rcx\n\t"
        "addq %%rax, %[r1]\n\t"
        "adcq %%rcx, %[r2]\n\t"
        "mulx 16(%[b]), %%rax, %%rcx\n\t"
        "adcq %%rax, %[r3]\n\t"
        "adcq %%rcx, %[r4]\n\t"
        "movq $0, %[r5]\n\t"
        "adcq $0, %[r5]\n\t"
        /* a1*b1 */
        "movq 8(%[a]), %%rdx\n\t"
        "mulx 8(%[b]), %%rax, %%rcx\n\t"
        "addq %%rax, %[r2]\n\t"
        "adcq %%rcx, %[r3]\n\t"
        /* a1*b3 */
        "mulx 24(%[b]), %%rax, %%rcx\n\t"
        "adcq %%rax, %[r4]\n\t"
        "adcq %%rcx, %[r5]\n\t"

        /* ===== Row 2: a[2] * b[0..3] ===== */
        "movq 16(%[a]), %%rdx\n\t"
        "mulx (%[b]), %%rax, %%rcx\n\t"
        "addq %%rax, %[r2]\n\t"
        "adcq %%rcx, %[r3]\n\t"
        "mulx 16(%[b]), %%rax, %%rcx\n\t"
        "adcq %%rax, %[r4]\n\t"
        "adcq %%rcx, %[r5]\n\t"
        "movq $0, %[r6]\n\t"
        "adcq $0, %[r6]\n\t"
        "mulx 8(%[b]), %%rax, %%rcx\n\t"
        "addq %%rax, %[r3]\n\t"
        "adcq %%rcx, %[r4]\n\t"
        "mulx 24(%[b]), %%rax, %%rcx\n\t"
        "adcq %%rax, %[r5]\n\t"
        "adcq %%rcx, %[r6]\n\t"

        /* ===== Row 3: a[3] * b[0..3] ===== */
        "movq 24(%[a]), %%rdx\n\t"
        "mulx (%[b]), %%rax, %%rcx\n\t"
        "addq %%rax, %[r3]\n\t"
        "adcq %%rcx, %[r4]\n\t"
        "mulx 16(%[b]), %%rax, %%rcx\n\t"
        "adcq %%rax, %[r5]\n\t"
        "adcq %%rcx, %[r6]\n\t"
        "movq $0, %[r7]\n\t"
        "adcq $0, %[r7]\n\t"
        "mulx 8(%[b]), %%rax, %%rcx\n\t"
        "addq %%rax, %[r4]\n\t"
        "adcq %%rcx, %[r5]\n\t"
        "mulx 24(%[b]), %%rax, %%rcx\n\t"
        "adcq %%rax, %[r6]\n\t"
        "adcq %%rcx, %[r7]\n\t"

        : [r0]"=&r"(r0), [r1]"=&r"(r1), [r2]"=&r"(r2), [r3]"=&r"(r3),
          [r4]"=&r"(r4), [r5]"=&r"(r5), [r6]"=&r"(r6), [r7]"=&r"(r7)
        : [a]"r"(a->d), [b]"r"(b->d)
        : "rax", "rcx", "rdx", "cc", "memory"
    );

    /* Reduce: r[0..3] + r[4..7] * C */
    uint64_t c = FIELD_C_ASM;
    __asm__ __volatile__(
        "movq %[r4], %%rdx\n\t"
        "mulx %[c], %%rax, %%rcx\n\t"
        "addq %%rax, %[r0]\n\t"
        "adcq %%rcx, %[r1]\n\t"

        "movq %[r5], %%rdx\n\t"
        "mulx %[c], %%rax, %%rcx\n\t"
        "adcq $0, %[r2]\n\t"
        "adcq $0, %[r3]\n\t"
        "addq %%rax, %[r1]\n\t"
        "adcq %%rcx, %[r2]\n\t"

        "movq %[r6], %%rdx\n\t"
        "mulx %[c], %%rax, %%rcx\n\t"
        "adcq $0, %[r3]\n\t"
        "sbbq %%r8, %%r8\n\t"
        "negq %%r8\n\t"
        "addq %%rax, %[r2]\n\t"
        "adcq %%rcx, %[r3]\n\t"
        "adcq $0, %%r8\n\t"

        "movq %[r7], %%rdx\n\t"
        "mulx %[c], %%rax, %%rcx\n\t"
        "addq %%rax, %[r3]\n\t"
        "adcq %%rcx, %%r8\n\t"

        /* Final fold: r8 * C */
        "movq %%r8, %%rdx\n\t"
        "mulx %[c], %%rax, %%rcx\n\t"
        "addq %%rax, %[r0]\n\t"
        "adcq %%rcx, %[r1]\n\t"
        "adcq $0, %[r2]\n\t"
        "adcq $0, %[r3]\n\t"

        : [r0]"+r"(r0), [r1]"+r"(r1), [r2]"+r"(r2), [r3]"+r"(r3)
        : [r4]"r"(r4), [r5]"r"(r5), [r6]"r"(r6), [r7]"r"(r7), [c]"r"(c)
        : "rax", "rcx", "rdx", "r8", "cc"
    );

    r->d[0] = r0; r->d[1] = r1; r->d[2] = r2; r->d[3] = r3;
    /* No normalize — lazy mul. Result in [0, 2^256). */
}

#define FE_MUL_ASM 1

#elif SECP_ARM64 && defined(__GNUC__)

/*
 * ARM64 field multiply optimized for Cortex-A76+ (Android phones).
 *
 * Key ARM64 instructions used:
 *   MUL Xd, Xn, Xm    → low 64 bits of 64×64 product (1 cycle throughput)
 *   UMULH Xd, Xn, Xm   → high 64 bits of 64×64 product (1 cycle throughput)
 *   ADDS/ADCS           → add with carry flag chain
 *   LDP Xd1, Xd2, [Xn] → load pair (2 registers in 1 instruction)
 *   CSEL                → conditional select (branchless normalize)
 *
 * Row 0 in full ASM with LDP for loading operands.
 * Rows 1-3 + reduction in __int128 C (ARM64 gcc generates optimal code
 * for __int128: MUL+UMULH pairs with ADDS/ADCS carry chains).
 *
 * The main ARM64-specific wins vs generic C:
 * 1. LDP loads 2 limbs per instruction (vs 2 separate LDR)
 * 2. Explicit ADDS/ADCS chain avoids compiler's carry tracking overhead
 * 3. The compiler generates MADD (fused multiply-add) for acc += (u128)a*b
 */
static inline void fe_mul_asm(secp256k1_fe *r, const secp256k1_fe *a, const secp256k1_fe *b) {
    uint64_t a0, a1, a2, a3, b0, b1, b2, b3;
    uint64_t lo0, lo1, lo2, lo3, hi0, hi1, hi2, hi3;

    /* Load all 8 limbs using LDP (load pair) — 4 instructions vs 8 LDR */
    __asm__ __volatile__(
        "ldp %[a0], %[a1], [%[ap]]\n\t"
        "ldp %[a2], %[a3], [%[ap], #16]\n\t"
        "ldp %[b0], %[b1], [%[bp]]\n\t"
        "ldp %[b2], %[b3], [%[bp], #16]\n\t"
        : [a0]"=&r"(a0), [a1]"=&r"(a1), [a2]"=&r"(a2), [a3]"=&r"(a3),
          [b0]"=&r"(b0), [b1]"=&r"(b1), [b2]"=&r"(b2), [b3]"=&r"(b3)
        : [ap]"r"(a->d), [bp]"r"(b->d)
        : "memory"
    );

    /* Full 4x4 multiply + reduction in ARM64 ASM.
     * Uses 20 registers: 4 inputs a, 4 inputs b, 8 product, 4 temps.
     * All 31 ARM64 GPRs available — zero stack spills.
     *
     * Scheduling: interleave MUL/UMULH from adjacent columns so the
     * 3-cycle multiply latency is hidden by independent additions.
     *
     *   Row 0: a0 * b[0..3] → r0..r4
     *   Row 1: a1 * b[0..3] → accumulate into r1..r5
     *   Row 2: a2 * b[0..3] → accumulate into r2..r6
     *   Row 3: a3 * b[0..3] → accumulate into r3..r7
     *   Reduce: r[0..3] + r[4..7] * C
     */
    __asm__ __volatile__(
        /* === Row 0: a0 * b[0..3] === */
        "mul   %[lo0], %[a0], %[b0]\n\t"
        "umulh %[lo1], %[a0], %[b0]\n\t"   /* lo1 = hi(a0*b0) = carry */
        "mul   x16, %[a0], %[b1]\n\t"
        "umulh x17, %[a0], %[b1]\n\t"
        "adds  %[lo1], %[lo1], x16\n\t"
        "adc   %[lo2], x17, xzr\n\t"
        "mul   x16, %[a0], %[b2]\n\t"
        "umulh x17, %[a0], %[b2]\n\t"
        "adds  %[lo2], %[lo2], x16\n\t"
        "adc   %[lo3], x17, xzr\n\t"
        "mul   x16, %[a0], %[b3]\n\t"
        "umulh %[hi0], %[a0], %[b3]\n\t"
        "adds  %[lo3], %[lo3], x16\n\t"
        "adc   %[hi0], %[hi0], xzr\n\t"

        /* === Row 1: a1 * b[0..3], accumulate === */
        "mul   x16, %[a1], %[b0]\n\t"
        "umulh x17, %[a1], %[b0]\n\t"
        "adds  %[lo1], %[lo1], x16\n\t"
        "adcs  %[lo2], %[lo2], x17\n\t"
        "mul   x16, %[a1], %[b2]\n\t"      /* interleave: start b2 while b1 pending */
        "umulh x17, %[a1], %[b2]\n\t"
        "adcs  %[lo3], %[lo3], x16\n\t"
        "adcs  %[hi0], %[hi0], x17\n\t"
        "adc   %[hi1], xzr, xzr\n\t"
        "mul   x16, %[a1], %[b1]\n\t"
        "umulh x17, %[a1], %[b1]\n\t"
        "adds  %[lo2], %[lo2], x16\n\t"
        "adcs  %[lo3], %[lo3], x17\n\t"
        "mul   x16, %[a1], %[b3]\n\t"
        "umulh x17, %[a1], %[b3]\n\t"
        "adcs  %[hi0], %[hi0], x16\n\t"
        "adc   %[hi1], %[hi1], x17\n\t"

        /* === Row 2: a2 * b[0..3] === */
        "mul   x16, %[a2], %[b0]\n\t"
        "umulh x17, %[a2], %[b0]\n\t"
        "adds  %[lo2], %[lo2], x16\n\t"
        "adcs  %[lo3], %[lo3], x17\n\t"
        "mul   x16, %[a2], %[b2]\n\t"
        "umulh x17, %[a2], %[b2]\n\t"
        "adcs  %[hi0], %[hi0], x16\n\t"
        "adcs  %[hi1], %[hi1], x17\n\t"
        "adc   %[hi2], xzr, xzr\n\t"
        "mul   x16, %[a2], %[b1]\n\t"
        "umulh x17, %[a2], %[b1]\n\t"
        "adds  %[lo3], %[lo3], x16\n\t"
        "adcs  %[hi0], %[hi0], x17\n\t"
        "mul   x16, %[a2], %[b3]\n\t"
        "umulh x17, %[a2], %[b3]\n\t"
        "adcs  %[hi1], %[hi1], x16\n\t"
        "adc   %[hi2], %[hi2], x17\n\t"

        /* === Row 3: a3 * b[0..3] === */
        "mul   x16, %[a3], %[b0]\n\t"
        "umulh x17, %[a3], %[b0]\n\t"
        "adds  %[lo3], %[lo3], x16\n\t"
        "adcs  %[hi0], %[hi0], x17\n\t"
        "mul   x16, %[a3], %[b2]\n\t"
        "umulh x17, %[a3], %[b2]\n\t"
        "adcs  %[hi1], %[hi1], x16\n\t"
        "adcs  %[hi2], %[hi2], x17\n\t"
        "adc   %[hi3], xzr, xzr\n\t"
        "mul   x16, %[a3], %[b1]\n\t"
        "umulh x17, %[a3], %[b1]\n\t"
        "adds  %[hi0], %[hi0], x16\n\t"
        "adcs  %[hi1], %[hi1], x17\n\t"
        "mul   x16, %[a3], %[b3]\n\t"
        "umulh x17, %[a3], %[b3]\n\t"
        "adcs  %[hi2], %[hi2], x16\n\t"
        "adc   %[hi3], %[hi3], x17\n\t"

        : [lo0]"=&r"(lo0), [lo1]"=&r"(lo1), [lo2]"=&r"(lo2), [lo3]"=&r"(lo3),
          [hi0]"=&r"(hi0), [hi1]"=&r"(hi1), [hi2]"=&r"(hi2), [hi3]"=&r"(hi3)
        : [a0]"r"(a0), [a1]"r"(a1), [a2]"r"(a2), [a3]"r"(a3),
          [b0]"r"(b0), [b1]"r"(b1), [b2]"r"(b2), [b3]"r"(b3)
        : "x16", "x17", "cc"
    );

    /* Reduction: lo + hi * C using MUL+UMULH+ADDS chain */
    {
        uint64_t c = FIELD_C_ASM;
        __asm__ __volatile__(
            /* hi0 * C + lo0 */
            "mul   x16, %[h0], %[c]\n\t"
            "umulh x17, %[h0], %[c]\n\t"
            "adds  %[r0], %[l0], x16\n\t"
            "adc   x17, x17, xzr\n\t"
            /* hi1 * C + lo1 + carry */
            "mul   x16, %[h1], %[c]\n\t"
            "adds  %[r1], %[l1], x17\n\t"
            "umulh x17, %[h1], %[c]\n\t"
            "adc   x17, x17, xzr\n\t"
            "adds  %[r1], %[r1], x16\n\t"
            "adc   x17, x17, xzr\n\t"
            /* hi2 * C + lo2 + carry */
            "mul   x16, %[h2], %[c]\n\t"
            "adds  %[r2], %[l2], x17\n\t"
            "umulh x17, %[h2], %[c]\n\t"
            "adc   x17, x17, xzr\n\t"
            "adds  %[r2], %[r2], x16\n\t"
            "adc   x17, x17, xzr\n\t"
            /* hi3 * C + lo3 + carry */
            "mul   x16, %[h3], %[c]\n\t"
            "adds  %[r3], %[l3], x17\n\t"
            "umulh x17, %[h3], %[c]\n\t"
            "adc   x17, x17, xzr\n\t"
            "adds  %[r3], %[r3], x16\n\t"
            "adc   x17, x17, xzr\n\t"
            /* Final fold: carry * C */
            "mul   x16, x17, %[c]\n\t"
            "adds  %[r0], %[r0], x16\n\t"
            "umulh x16, x17, %[c]\n\t"
            "adcs  %[r1], %[r1], x16\n\t"
            "adcs  %[r2], %[r2], xzr\n\t"
            "adc   %[r3], %[r3], xzr\n\t"
            : [r0]"=&r"(lo0), [r1]"=&r"(lo1), [r2]"=&r"(lo2), [r3]"=&r"(lo3)
            : [l0]"r"(lo0), [l1]"r"(lo1), [l2]"r"(lo2), [l3]"r"(lo3),
              [h0]"r"(hi0), [h1]"r"(hi1), [h2]"r"(hi2), [h3]"r"(hi3), [c]"r"(c)
            : "x16", "x17", "cc"
        );
    }

    /* Store result using STP (store pair) — 2 instructions vs 4 STR */
    __asm__ __volatile__(
        "stp %[lo0], %[lo1], [%[rp]]\n\t"
        "stp %[lo2], %[lo3], [%[rp], #16]\n\t"
        : : [rp]"r"(r->d), [lo0]"r"(lo0), [lo1]"r"(lo1), [lo2]"r"(lo2), [lo3]"r"(lo3)
        : "memory"
    );
    /* No normalize — lazy mul. Result in [0, 2^256). */
}

#define FE_MUL_ASM 1

#else
#define FE_MUL_ASM 0
#endif

#endif /* SECP256K1_FIELD_ASM_H */
