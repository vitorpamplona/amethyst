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
package com.vitorpamplona.quartz.utils.ciphers.aes

/*
 * Ported from BearSSL's aes_ct64 (src/symcipher/aes_ct64.c, aes_ct64_enc.c,
 * aes_ct64_dec.c): Copyright (c) 2016 Thomas Pornin <pornin@bolet.org>, released
 * under the same MIT terms as the notice above.
 */

/**
 * Constant-time AES (FIPS 197) for the targets without a platform AES Quartz calls
 * directly; JVM and Android use JCA instead.
 *
 * The state is bitsliced: eight 64-bit words hold bit i of every byte of four blocks,
 * and SubBytes is the Boyar-Peralta boolean circuit, so no table is ever indexed by
 * key or data and timing does not depend on either. Up to four blocks go through
 * at once, which is how CTR (GCM) and CBC decryption run; CBC encryption is serial
 * and wastes three slots.
 *
 * An instance holds only the expanded key, which never changes, so one can be shared
 * across threads; each caller brings its own [Scratch].
 */
internal class AesBitsliced(
    key: ByteArray,
) {
    private val rounds =
        when (key.size) {
            16 -> 10
            24 -> 12
            32 -> 14
            else -> throw IllegalArgumentException("AES key must be 16, 24 or 32 bytes, was ${key.size}")
        }

    private val roundKeys = expandKey(key, rounds)

    /** Working state for one caller: reuse it across calls, never share it between threads. */
    class Scratch {
        internal val q = LongArray(8)
        internal val w = IntArray(16)
    }

    /** Encrypts [count] (1 to 4) consecutive blocks of [src] into [dst]. They may overlap exactly. */
    fun encryptBlocks(
        src: ByteArray,
        srcOffset: Int,
        dst: ByteArray,
        dstOffset: Int,
        count: Int,
        scratch: Scratch,
    ) {
        val q = scratch.q
        load(scratch, src, srcOffset, count)
        addRoundKey(q, roundKeys, 0)
        for (u in 1 until rounds) {
            sbox(q)
            shiftRows(q)
            mixColumns(q)
            addRoundKey(q, roundKeys, u shl 3)
        }
        sbox(q)
        shiftRows(q)
        addRoundKey(q, roundKeys, rounds shl 3)
        store(scratch, dst, dstOffset, count)
    }

    /** Decrypts [count] (1 to 4) consecutive blocks of [src] into [dst]. They may overlap exactly. */
    fun decryptBlocks(
        src: ByteArray,
        srcOffset: Int,
        dst: ByteArray,
        dstOffset: Int,
        count: Int,
        scratch: Scratch,
    ) {
        val q = scratch.q
        load(scratch, src, srcOffset, count)
        addRoundKey(q, roundKeys, rounds shl 3)
        for (u in rounds - 1 downTo 1) {
            invShiftRows(q)
            invSbox(q)
            addRoundKey(q, roundKeys, u shl 3)
            invMixColumns(q)
        }
        invShiftRows(q)
        invSbox(q)
        addRoundKey(q, roundKeys, 0)
        store(scratch, dst, dstOffset, count)
    }

    private fun load(
        scratch: Scratch,
        src: ByteArray,
        offset: Int,
        count: Int,
    ) {
        require(count in 1..4) { "1 to 4 blocks at a time, was $count" }
        val q = scratch.q
        val w = scratch.w
        w.fill(0)
        for (i in 0 until count * 4) {
            w[i] = littleEndianInt(src, offset + 4 * i)
        }
        for (i in 0 until 4) {
            interleaveIn(q, i, i + 4, w, i shl 2)
        }
        ortho(q)
    }

    private fun store(
        scratch: Scratch,
        dst: ByteArray,
        offset: Int,
        count: Int,
    ) {
        val q = scratch.q
        val w = scratch.w
        ortho(q)
        for (i in 0 until 4) {
            interleaveOut(w, i shl 2, q[i], q[i + 4])
        }
        for (i in 0 until count * 4) {
            val word = w[i]
            val o = offset + 4 * i
            dst[o] = word.toByte()
            dst[o + 1] = (word ushr 8).toByte()
            dst[o + 2] = (word ushr 16).toByte()
            dst[o + 3] = (word ushr 24).toByte()
        }
    }
}

private const val M5 = 0x5555_5555_5555_5555L
private const val MA = -0x5555_5555_5555_5556L // 0xAAAA_AAAA_AAAA_AAAA
private const val M3 = 0x3333_3333_3333_3333L
private const val MC = -0x3333_3333_3333_3334L // 0xCCCC_CCCC_CCCC_CCCC
private const val M0F = 0x0F0F_0F0F_0F0F_0F0FL
private const val MF0 = -0x0F0F_0F0F_0F0F_0F10L // 0xF0F0_F0F0_F0F0_F0F0
private const val M00FF = 0x00FF_00FF_00FF_00FFL
private const val M0000FFFF = 0x0000_FFFF_0000_FFFFL
private const val M1 = 0x1111_1111_1111_1111L
private const val M2 = 0x2222_2222_2222_2222L
private const val M4 = 0x4444_4444_4444_4444L
private const val M8 = -0x7777_7777_7777_7778L // 0x8888_8888_8888_8888
private const val TOP_NIBBLE = -0x1000_0000_0000_0000L // 0xF000_0000_0000_0000
private const val TOP_12_BITS = -0x0010_0000_0000_0000L // 0xFFF0_0000_0000_0000

private val RCON = intArrayOf(0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x1B, 0x36)

private fun littleEndianInt(
    b: ByteArray,
    o: Int,
): Int =
    (b[o].toInt() and 0xFF) or
        ((b[o + 1].toInt() and 0xFF) shl 8) or
        ((b[o + 2].toInt() and 0xFF) shl 16) or
        ((b[o + 3].toInt() and 0xFF) shl 24)

private fun addRoundKey(
    q: LongArray,
    sk: LongArray,
    offset: Int,
) {
    for (i in 0 until 8) {
        q[i] = q[i] xor sk[offset + i]
    }
}

private fun swap(
    q: LongArray,
    x: Int,
    y: Int,
    cl: Long,
    ch: Long,
    s: Int,
) {
    val a = q[x]
    val b = q[y]
    q[x] = (a and cl) or ((b and cl) shl s)
    q[y] = ((a and ch) ushr s) or (b and ch)
}

/** Transposes the eight words between byte-per-lane and bit-per-word layouts (its own inverse). */
private fun ortho(q: LongArray) {
    swap(q, 0, 1, M5, MA, 1)
    swap(q, 2, 3, M5, MA, 1)
    swap(q, 4, 5, M5, MA, 1)
    swap(q, 6, 7, M5, MA, 1)

    swap(q, 0, 2, M3, MC, 2)
    swap(q, 1, 3, M3, MC, 2)
    swap(q, 4, 6, M3, MC, 2)
    swap(q, 5, 7, M3, MC, 2)

    swap(q, 0, 4, M0F, MF0, 4)
    swap(q, 1, 5, M0F, MF0, 4)
    swap(q, 2, 6, M0F, MF0, 4)
    swap(q, 3, 7, M0F, MF0, 4)
}

private fun interleaveIn(
    q: LongArray,
    i0: Int,
    i1: Int,
    w: IntArray,
    offset: Int,
) {
    var x0 = w[offset].toLong() and 0xFFFF_FFFFL
    var x1 = w[offset + 1].toLong() and 0xFFFF_FFFFL
    var x2 = w[offset + 2].toLong() and 0xFFFF_FFFFL
    var x3 = w[offset + 3].toLong() and 0xFFFF_FFFFL
    x0 = (x0 or (x0 shl 16)) and M0000FFFF
    x1 = (x1 or (x1 shl 16)) and M0000FFFF
    x2 = (x2 or (x2 shl 16)) and M0000FFFF
    x3 = (x3 or (x3 shl 16)) and M0000FFFF
    x0 = (x0 or (x0 shl 8)) and M00FF
    x1 = (x1 or (x1 shl 8)) and M00FF
    x2 = (x2 or (x2 shl 8)) and M00FF
    x3 = (x3 or (x3 shl 8)) and M00FF
    q[i0] = x0 or (x2 shl 8)
    q[i1] = x1 or (x3 shl 8)
}

private fun interleaveOut(
    w: IntArray,
    offset: Int,
    q0: Long,
    q1: Long,
) {
    var x0 = q0 and M00FF
    var x1 = q1 and M00FF
    var x2 = (q0 ushr 8) and M00FF
    var x3 = (q1 ushr 8) and M00FF
    x0 = (x0 or (x0 ushr 8)) and M0000FFFF
    x1 = (x1 or (x1 ushr 8)) and M0000FFFF
    x2 = (x2 or (x2 ushr 8)) and M0000FFFF
    x3 = (x3 or (x3 ushr 8)) and M0000FFFF
    w[offset] = (x0 or (x0 ushr 16)).toInt()
    w[offset + 1] = (x1 or (x1 ushr 16)).toInt()
    w[offset + 2] = (x2 or (x2 ushr 16)).toInt()
    w[offset + 3] = (x3 or (x3 ushr 16)).toInt()
}

/**
 * SubBytes on all 32 bytes at once: the Boyar-Peralta circuit ("A new combinational
 * logic minimization technique with applications to cryptology",
 * https://eprint.iacr.org/2009/191). x0 is the high bit, x7 the low bit.
 */
private fun sbox(q: LongArray) {
    val x0 = q[7]
    val x1 = q[6]
    val x2 = q[5]
    val x3 = q[4]
    val x4 = q[3]
    val x5 = q[2]
    val x6 = q[1]
    val x7 = q[0]

    // Top linear transformation.
    val y14 = x3 xor x5
    val y13 = x0 xor x6
    val y9 = x0 xor x3
    val y8 = x0 xor x5
    val t0 = x1 xor x2
    val y1 = t0 xor x7
    val y4 = y1 xor x3
    val y12 = y13 xor y14
    val y2 = y1 xor x0
    val y5 = y1 xor x6
    val y3 = y5 xor y8
    val t1 = x4 xor y12
    val y15 = t1 xor x5
    val y20 = t1 xor x1
    val y6 = y15 xor x7
    val y10 = y15 xor t0
    val y11 = y20 xor y9
    val y7 = x7 xor y11
    val y17 = y10 xor y11
    val y19 = y10 xor y8
    val y16 = t0 xor y11
    val y21 = y13 xor y16
    val y18 = x0 xor y16

    // Non-linear section.
    val t2 = y12 and y15
    val t3 = y3 and y6
    val t4 = t3 xor t2
    val t5 = y4 and x7
    val t6 = t5 xor t2
    val t7 = y13 and y16
    val t8 = y5 and y1
    val t9 = t8 xor t7
    val t10 = y2 and y7
    val t11 = t10 xor t7
    val t12 = y9 and y11
    val t13 = y14 and y17
    val t14 = t13 xor t12
    val t15 = y8 and y10
    val t16 = t15 xor t12
    val t17 = t4 xor t14
    val t18 = t6 xor t16
    val t19 = t9 xor t14
    val t20 = t11 xor t16
    val t21 = t17 xor y20
    val t22 = t18 xor y19
    val t23 = t19 xor y21
    val t24 = t20 xor y18

    val t25 = t21 xor t22
    val t26 = t21 and t23
    val t27 = t24 xor t26
    val t28 = t25 and t27
    val t29 = t28 xor t22
    val t30 = t23 xor t24
    val t31 = t22 xor t26
    val t32 = t31 and t30
    val t33 = t32 xor t24
    val t34 = t23 xor t33
    val t35 = t27 xor t33
    val t36 = t24 and t35
    val t37 = t36 xor t34
    val t38 = t27 xor t36
    val t39 = t29 and t38
    val t40 = t25 xor t39

    val t41 = t40 xor t37
    val t42 = t29 xor t33
    val t43 = t29 xor t40
    val t44 = t33 xor t37
    val t45 = t42 xor t41
    val z0 = t44 and y15
    val z1 = t37 and y6
    val z2 = t33 and x7
    val z3 = t43 and y16
    val z4 = t40 and y1
    val z5 = t29 and y7
    val z6 = t42 and y11
    val z7 = t45 and y17
    val z8 = t41 and y10
    val z9 = t44 and y12
    val z10 = t37 and y3
    val z11 = t33 and y4
    val z12 = t43 and y13
    val z13 = t40 and y5
    val z14 = t29 and y2
    val z15 = t42 and y9
    val z16 = t45 and y14
    val z17 = t41 and y8

    // Bottom linear transformation.
    val t46 = z15 xor z16
    val t47 = z10 xor z11
    val t48 = z5 xor z13
    val t49 = z9 xor z10
    val t50 = z2 xor z12
    val t51 = z2 xor z5
    val t52 = z7 xor z8
    val t53 = z0 xor z3
    val t54 = z6 xor z7
    val t55 = z16 xor z17
    val t56 = z12 xor t48
    val t57 = t50 xor t53
    val t58 = z4 xor t46
    val t59 = z3 xor t54
    val t60 = t46 xor t57
    val t61 = z14 xor t57
    val t62 = t52 xor t58
    val t63 = t49 xor t58
    val t64 = z4 xor t59
    val t65 = t61 xor t62
    val t66 = z1 xor t63
    val s0 = t59 xor t63
    val s6 = t56 xor t62.inv()
    val s7 = t48 xor t60.inv()
    val t67 = t64 xor t65
    val s3 = t53 xor t66
    val s4 = t51 xor t66
    val s5 = t47 xor t65
    val s1 = t64 xor s3.inv()
    val s2 = t55 xor t67.inv()

    q[7] = s0
    q[6] = s1
    q[5] = s2
    q[4] = s3
    q[3] = s4
    q[2] = s5
    q[1] = s6
    q[0] = s7
}

/** The affine map that turns [sbox] into its inverse when applied before and after it. */
private fun invAffine(q: LongArray) {
    val q0 = q[0].inv()
    val q1 = q[1].inv()
    val q2 = q[2]
    val q3 = q[3]
    val q4 = q[4]
    val q5 = q[5].inv()
    val q6 = q[6].inv()
    val q7 = q[7]
    q[7] = q1 xor q4 xor q6
    q[6] = q0 xor q3 xor q5
    q[5] = q7 xor q2 xor q4
    q[4] = q6 xor q1 xor q3
    q[3] = q5 xor q0 xor q2
    q[2] = q4 xor q7 xor q1
    q[1] = q3 xor q6 xor q0
    q[0] = q2 xor q5 xor q7
}

private fun invSbox(q: LongArray) {
    invAffine(q)
    sbox(q)
    invAffine(q)
}

private fun shiftRows(q: LongArray) {
    for (i in 0 until 8) {
        val x = q[i]
        q[i] = (x and 0x0000_0000_0000_FFFFL) or
            ((x and 0x0000_0000_FFF0_0000L) ushr 4) or
            ((x and 0x0000_0000_000F_0000L) shl 12) or
            ((x and 0x0000_FF00_0000_0000L) ushr 8) or
            ((x and 0x0000_00FF_0000_0000L) shl 8) or
            ((x and TOP_NIBBLE) ushr 12) or
            ((x and 0x0FFF_0000_0000_0000L) shl 4)
    }
}

private fun invShiftRows(q: LongArray) {
    for (i in 0 until 8) {
        val x = q[i]
        q[i] = (x and 0x0000_0000_0000_FFFFL) or
            ((x and 0x0000_0000_0FFF_0000L) shl 4) or
            ((x and 0x0000_0000_F000_0000L) ushr 12) or
            ((x and 0x0000_00FF_0000_0000L) shl 8) or
            ((x and 0x0000_FF00_0000_0000L) ushr 8) or
            ((x and 0x000F_0000_0000_0000L) shl 12) or
            ((x and TOP_12_BITS) ushr 4)
    }
}

private fun rotr32(x: Long) = (x shl 32) or (x ushr 32)

private fun rotr16(x: Long) = (x ushr 16) or (x shl 48)

private fun mixColumns(q: LongArray) {
    val q0 = q[0]
    val q1 = q[1]
    val q2 = q[2]
    val q3 = q[3]
    val q4 = q[4]
    val q5 = q[5]
    val q6 = q[6]
    val q7 = q[7]
    val r0 = rotr16(q0)
    val r1 = rotr16(q1)
    val r2 = rotr16(q2)
    val r3 = rotr16(q3)
    val r4 = rotr16(q4)
    val r5 = rotr16(q5)
    val r6 = rotr16(q6)
    val r7 = rotr16(q7)

    q[0] = q7 xor r7 xor r0 xor rotr32(q0 xor r0)
    q[1] = q0 xor r0 xor q7 xor r7 xor r1 xor rotr32(q1 xor r1)
    q[2] = q1 xor r1 xor r2 xor rotr32(q2 xor r2)
    q[3] = q2 xor r2 xor q7 xor r7 xor r3 xor rotr32(q3 xor r3)
    q[4] = q3 xor r3 xor q7 xor r7 xor r4 xor rotr32(q4 xor r4)
    q[5] = q4 xor r4 xor r5 xor rotr32(q5 xor r5)
    q[6] = q5 xor r5 xor r6 xor rotr32(q6 xor r6)
    q[7] = q6 xor r6 xor r7 xor rotr32(q7 xor r7)
}

private fun invMixColumns(q: LongArray) {
    val q0 = q[0]
    val q1 = q[1]
    val q2 = q[2]
    val q3 = q[3]
    val q4 = q[4]
    val q5 = q[5]
    val q6 = q[6]
    val q7 = q[7]
    val r0 = rotr16(q0)
    val r1 = rotr16(q1)
    val r2 = rotr16(q2)
    val r3 = rotr16(q3)
    val r4 = rotr16(q4)
    val r5 = rotr16(q5)
    val r6 = rotr16(q6)
    val r7 = rotr16(q7)

    q[0] = q5 xor q6 xor q7 xor r0 xor r5 xor r7 xor rotr32(q0 xor q5 xor q6 xor r0 xor r5)
    q[1] = q0 xor q5 xor r0 xor r1 xor r5 xor r6 xor r7 xor rotr32(q1 xor q5 xor q7 xor r1 xor r5 xor r6)
    q[2] = q0 xor q1 xor q6 xor r1 xor r2 xor r6 xor r7 xor rotr32(q0 xor q2 xor q6 xor r2 xor r6 xor r7)
    q[3] = q0 xor q1 xor q2 xor q5 xor q6 xor r0 xor r2 xor r3 xor r5 xor
        rotr32(q0 xor q1 xor q3 xor q5 xor q6 xor q7 xor r0 xor r3 xor r5 xor r7)
    q[4] = q1 xor q2 xor q3 xor q5 xor r1 xor r3 xor r4 xor r5 xor r6 xor r7 xor
        rotr32(q1 xor q2 xor q4 xor q5 xor q7 xor r1 xor r4 xor r5 xor r6)
    q[5] = q2 xor q3 xor q4 xor q6 xor r2 xor r4 xor r5 xor r6 xor r7 xor
        rotr32(q2 xor q3 xor q5 xor q6 xor r2 xor r5 xor r6 xor r7)
    q[6] = q3 xor q4 xor q5 xor q7 xor r3 xor r5 xor r6 xor r7 xor rotr32(q3 xor q4 xor q6 xor q7 xor r3 xor r6 xor r7)
    q[7] = q4 xor q5 xor q6 xor r4 xor r6 xor r7 xor rotr32(q4 xor q5 xor q7 xor r4 xor r7)
}

/** SubWord for the key schedule, through the same bitsliced S-box, in the caller's [q]. */
private fun subWord(
    x: Int,
    q: LongArray,
): Int {
    q.fill(0)
    q[0] = x.toLong() and 0xFFFF_FFFFL
    ortho(q)
    sbox(q)
    ortho(q)
    return q[0].toInt()
}

/** FIPS 197 §5.2 key expansion, returned in the bitsliced layout the rounds use. */
private fun expandKey(
    key: ByteArray,
    rounds: Int,
): LongArray {
    val nk = key.size shr 2
    val nkf = (rounds + 1) shl 2
    val words = IntArray(nkf)
    for (i in 0 until nk) {
        words[i] = littleEndianInt(key, 4 * i)
    }
    val q = LongArray(8)
    var tmp = words[nk - 1]
    var j = 0
    var k = 0
    for (i in nk until nkf) {
        if (j == 0) {
            tmp = (tmp shl 24) or (tmp ushr 8)
            tmp = subWord(tmp, q) xor RCON[k]
        } else if (nk > 6 && j == 4) {
            tmp = subWord(tmp, q)
        }
        tmp = tmp xor words[i - nk]
        words[i] = tmp
        if (++j == nk) {
            j = 0
            k++
        }
    }

    // Each round key packed into two words, then spread to the eight the rounds xor in.
    val compressed = LongArray((rounds + 1) shl 1)
    for (i in 0 until nkf step 4) {
        interleaveIn(q, 0, 4, words, i)
        q[1] = q[0]
        q[2] = q[0]
        q[3] = q[0]
        q[5] = q[4]
        q[6] = q[4]
        q[7] = q[4]
        ortho(q)
        val c = i shr 1
        compressed[c] = (q[0] and M1) or (q[1] and M2) or (q[2] and M4) or (q[3] and M8)
        compressed[c + 1] = (q[4] and M1) or (q[5] and M2) or (q[6] and M4) or (q[7] and M8)
    }
    words.fill(0)

    val expanded = LongArray((rounds + 1) shl 3)
    for (u in compressed.indices) {
        val v = u shl 2
        val x = compressed[u]
        val x0 = x and M1
        val x1 = (x and M2) ushr 1
        val x2 = (x and M4) ushr 2
        val x3 = (x and M8) ushr 3
        expanded[v] = (x0 shl 4) - x0
        expanded[v + 1] = (x1 shl 4) - x1
        expanded[v + 2] = (x2 shl 4) - x2
        expanded[v + 3] = (x3 shl 4) - x3
    }
    compressed.fill(0)
    return expanded
}
