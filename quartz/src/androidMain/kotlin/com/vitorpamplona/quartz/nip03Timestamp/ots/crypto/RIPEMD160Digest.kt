/**
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
package com.vitorpamplona.quartz.nip03Timestamp.ots.crypto

/**
 * Implementation of RIPEMD
 *
 * @see [ripemd160](http://www.esat.kuleuven.ac.be/~bosselae/ripemd160.html)
 */
class RIPEMD160Digest : GeneralDigest {
    private var h0 = 0
    private var h1 = 0
    private var h2 = 0
    private var h3 = 0
    private var h4 = 0 // IV's

    private val xArray = IntArray(16)
    private var xOff = 0

    /**
     * Standard constructor
     */
    constructor() {
        reset()
    }

    /**
     * Copy constructor.  This will copy the state of the provided
     * message digest.
     * @param t RIPEMD160Digest
     */
    constructor(t: RIPEMD160Digest) : super(t) {
        copyIn(t)
    }

    private fun copyIn(t: RIPEMD160Digest) {
        super.copyIn(t)

        h0 = t.h0
        h1 = t.h1
        h2 = t.h2
        h3 = t.h3
        h4 = t.h4

        System.arraycopy(t.xArray, 0, xArray, 0, t.xArray.size)
        xOff = t.xOff
    }

    override fun getAlgorithmName(): String = "RIPEMD160"

    override fun getDigestSize(): Int = DIGEST_LENGTH

    override fun processWord(
        `in`: ByteArray,
        inOff: Int,
    ) {
        xArray[xOff++] = (
            (`in`[inOff].toInt() and 0xff) or ((`in`[inOff + 1].toInt() and 0xff) shl 8)
                or ((`in`[inOff + 2].toInt() and 0xff) shl 16) or ((`in`[inOff + 3].toInt() and 0xff) shl 24)
        )

        if (xOff == 16) {
            processBlock()
        }
    }

    override fun processLength(bitLength: Long) {
        if (xOff > 14) {
            processBlock()
        }

        xArray[14] = (bitLength and 0xffffffffL).toInt()
        xArray[15] = (bitLength ushr 32).toInt()
    }

    private fun unpackWord(
        word: Int,
        out: ByteArray,
        outOff: Int,
    ) {
        out[outOff] = word.toByte()
        out[outOff + 1] = (word ushr 8).toByte()
        out[outOff + 2] = (word ushr 16).toByte()
        out[outOff + 3] = (word ushr 24).toByte()
    }

    override fun doFinal(
        out: ByteArray,
        outOff: Int,
    ): Int {
        finish()

        unpackWord(h0, out, outOff)
        unpackWord(h1, out, outOff + 4)
        unpackWord(h2, out, outOff + 8)
        unpackWord(h3, out, outOff + 12)
        unpackWord(h4, out, outOff + 16)

        reset()

        return DIGEST_LENGTH
    }

    /**
     * reset the chaining variables to the IV values.
     */
    override fun reset() {
        super.reset()

        h0 = 0x67452301
        h1 = -0x10325477
        h2 = -0x67452302
        h3 = 0x10325476
        h4 = -0x3c2d1e10

        xOff = 0

        for (i in xArray.indices) {
            xArray[i] = 0
        }
    }

    /*
     * rotate int x left n bits.
     */
    private fun rotateLeft(
        x: Int,
        n: Int,
    ): Int = (x shl n) or (x ushr (32 - n))

    /*
     * f1,f2,f3,f4,f5 are the basic RIPEMD160 functions.
     * rounds 0-15
     */
    private fun f1(
        x: Int,
        y: Int,
        z: Int,
    ): Int = x xor y xor z

    /*
     * rounds 16-31
     */
    private fun f2(
        x: Int,
        y: Int,
        z: Int,
    ): Int = (x and y) or (x.inv() and z)

    /*
     * rounds 32-47
     */
    private fun f3(
        x: Int,
        y: Int,
        z: Int,
    ): Int = (x or y.inv()) xor z

    /*
     * rounds 48-63
     */
    private fun f4(
        x: Int,
        y: Int,
        z: Int,
    ): Int = (x and z) or (y and z.inv())

    /*
     * rounds 64-79
     */
    private fun f5(
        x: Int,
        y: Int,
        z: Int,
    ): Int = x xor (y or z.inv())

    override fun processBlock() {
        var a: Int
        var aa: Int
        var b: Int
        var bb: Int
        var c: Int
        var cc: Int
        var d: Int
        var dd: Int
        var e: Int
        var ee: Int

        aa = h0
        a = aa
        bb = h1
        b = bb
        cc = h2
        c = cc
        dd = h3
        d = dd
        ee = h4
        e = ee

        //
        // Rounds 1 - 16
        //
        // left
        a = rotateLeft(a + f1(b, c, d) + xArray[0], 11) + e
        c = rotateLeft(c, 10)
        e = rotateLeft(e + f1(a, b, c) + xArray[1], 14) + d
        b = rotateLeft(b, 10)
        d = rotateLeft(d + f1(e, a, b) + xArray[2], 15) + c
        a = rotateLeft(a, 10)
        c = rotateLeft(c + f1(d, e, a) + xArray[3], 12) + b
        e = rotateLeft(e, 10)
        b = rotateLeft(b + f1(c, d, e) + xArray[4], 5) + a
        d = rotateLeft(d, 10)
        a = rotateLeft(a + f1(b, c, d) + xArray[5], 8) + e
        c = rotateLeft(c, 10)
        e = rotateLeft(e + f1(a, b, c) + xArray[6], 7) + d
        b = rotateLeft(b, 10)
        d = rotateLeft(d + f1(e, a, b) + xArray[7], 9) + c
        a = rotateLeft(a, 10)
        c = rotateLeft(c + f1(d, e, a) + xArray[8], 11) + b
        e = rotateLeft(e, 10)
        b = rotateLeft(b + f1(c, d, e) + xArray[9], 13) + a
        d = rotateLeft(d, 10)
        a = rotateLeft(a + f1(b, c, d) + xArray[10], 14) + e
        c = rotateLeft(c, 10)
        e = rotateLeft(e + f1(a, b, c) + xArray[11], 15) + d
        b = rotateLeft(b, 10)
        d = rotateLeft(d + f1(e, a, b) + xArray[12], 6) + c
        a = rotateLeft(a, 10)
        c = rotateLeft(c + f1(d, e, a) + xArray[13], 7) + b
        e = rotateLeft(e, 10)
        b = rotateLeft(b + f1(c, d, e) + xArray[14], 9) + a
        d = rotateLeft(d, 10)
        a = rotateLeft(a + f1(b, c, d) + xArray[15], 8) + e
        c = rotateLeft(c, 10)

        // right
        aa = rotateLeft(aa + f5(bb, cc, dd) + xArray[5] + 0x50a28be6, 8) + ee
        cc = rotateLeft(cc, 10)
        ee = rotateLeft(ee + f5(aa, bb, cc) + xArray[14] + 0x50a28be6, 9) + dd
        bb = rotateLeft(bb, 10)
        dd = rotateLeft(dd + f5(ee, aa, bb) + xArray[7] + 0x50a28be6, 9) + cc
        aa = rotateLeft(aa, 10)
        cc = rotateLeft(cc + f5(dd, ee, aa) + xArray[0] + 0x50a28be6, 11) + bb
        ee = rotateLeft(ee, 10)
        bb = rotateLeft(bb + f5(cc, dd, ee) + xArray[9] + 0x50a28be6, 13) + aa
        dd = rotateLeft(dd, 10)
        aa = rotateLeft(aa + f5(bb, cc, dd) + xArray[2] + 0x50a28be6, 15) + ee
        cc = rotateLeft(cc, 10)
        ee = rotateLeft(ee + f5(aa, bb, cc) + xArray[11] + 0x50a28be6, 15) + dd
        bb = rotateLeft(bb, 10)
        dd = rotateLeft(dd + f5(ee, aa, bb) + xArray[4] + 0x50a28be6, 5) + cc
        aa = rotateLeft(aa, 10)
        cc = rotateLeft(cc + f5(dd, ee, aa) + xArray[13] + 0x50a28be6, 7) + bb
        ee = rotateLeft(ee, 10)
        bb = rotateLeft(bb + f5(cc, dd, ee) + xArray[6] + 0x50a28be6, 7) + aa
        dd = rotateLeft(dd, 10)
        aa = rotateLeft(aa + f5(bb, cc, dd) + xArray[15] + 0x50a28be6, 8) + ee
        cc = rotateLeft(cc, 10)
        ee = rotateLeft(ee + f5(aa, bb, cc) + xArray[8] + 0x50a28be6, 11) + dd
        bb = rotateLeft(bb, 10)
        dd = rotateLeft(dd + f5(ee, aa, bb) + xArray[1] + 0x50a28be6, 14) + cc
        aa = rotateLeft(aa, 10)
        cc = rotateLeft(cc + f5(dd, ee, aa) + xArray[10] + 0x50a28be6, 14) + bb
        ee = rotateLeft(ee, 10)
        bb = rotateLeft(bb + f5(cc, dd, ee) + xArray[3] + 0x50a28be6, 12) + aa
        dd = rotateLeft(dd, 10)
        aa = rotateLeft(aa + f5(bb, cc, dd) + xArray[12] + 0x50a28be6, 6) + ee
        cc = rotateLeft(cc, 10)

        //
        // Rounds 16-31
        //
        // left
        e = rotateLeft(e + f2(a, b, c) + xArray[7] + 0x5a827999, 7) + d
        b = rotateLeft(b, 10)
        d = rotateLeft(d + f2(e, a, b) + xArray[4] + 0x5a827999, 6) + c
        a = rotateLeft(a, 10)
        c = rotateLeft(c + f2(d, e, a) + xArray[13] + 0x5a827999, 8) + b
        e = rotateLeft(e, 10)
        b = rotateLeft(b + f2(c, d, e) + xArray[1] + 0x5a827999, 13) + a
        d = rotateLeft(d, 10)
        a = rotateLeft(a + f2(b, c, d) + xArray[10] + 0x5a827999, 11) + e
        c = rotateLeft(c, 10)
        e = rotateLeft(e + f2(a, b, c) + xArray[6] + 0x5a827999, 9) + d
        b = rotateLeft(b, 10)
        d = rotateLeft(d + f2(e, a, b) + xArray[15] + 0x5a827999, 7) + c
        a = rotateLeft(a, 10)
        c = rotateLeft(c + f2(d, e, a) + xArray[3] + 0x5a827999, 15) + b
        e = rotateLeft(e, 10)
        b = rotateLeft(b + f2(c, d, e) + xArray[12] + 0x5a827999, 7) + a
        d = rotateLeft(d, 10)
        a = rotateLeft(a + f2(b, c, d) + xArray[0] + 0x5a827999, 12) + e
        c = rotateLeft(c, 10)
        e = rotateLeft(e + f2(a, b, c) + xArray[9] + 0x5a827999, 15) + d
        b = rotateLeft(b, 10)
        d = rotateLeft(d + f2(e, a, b) + xArray[5] + 0x5a827999, 9) + c
        a = rotateLeft(a, 10)
        c = rotateLeft(c + f2(d, e, a) + xArray[2] + 0x5a827999, 11) + b
        e = rotateLeft(e, 10)
        b = rotateLeft(b + f2(c, d, e) + xArray[14] + 0x5a827999, 7) + a
        d = rotateLeft(d, 10)
        a = rotateLeft(a + f2(b, c, d) + xArray[11] + 0x5a827999, 13) + e
        c = rotateLeft(c, 10)
        e = rotateLeft(e + f2(a, b, c) + xArray[8] + 0x5a827999, 12) + d
        b = rotateLeft(b, 10)

        // right
        ee = rotateLeft(ee + f4(aa, bb, cc) + xArray[6] + 0x5c4dd124, 9) + dd
        bb = rotateLeft(bb, 10)
        dd = rotateLeft(dd + f4(ee, aa, bb) + xArray[11] + 0x5c4dd124, 13) + cc
        aa = rotateLeft(aa, 10)
        cc = rotateLeft(cc + f4(dd, ee, aa) + xArray[3] + 0x5c4dd124, 15) + bb
        ee = rotateLeft(ee, 10)
        bb = rotateLeft(bb + f4(cc, dd, ee) + xArray[7] + 0x5c4dd124, 7) + aa
        dd = rotateLeft(dd, 10)
        aa = rotateLeft(aa + f4(bb, cc, dd) + xArray[0] + 0x5c4dd124, 12) + ee
        cc = rotateLeft(cc, 10)
        ee = rotateLeft(ee + f4(aa, bb, cc) + xArray[13] + 0x5c4dd124, 8) + dd
        bb = rotateLeft(bb, 10)
        dd = rotateLeft(dd + f4(ee, aa, bb) + xArray[5] + 0x5c4dd124, 9) + cc
        aa = rotateLeft(aa, 10)
        cc = rotateLeft(cc + f4(dd, ee, aa) + xArray[10] + 0x5c4dd124, 11) + bb
        ee = rotateLeft(ee, 10)
        bb = rotateLeft(bb + f4(cc, dd, ee) + xArray[14] + 0x5c4dd124, 7) + aa
        dd = rotateLeft(dd, 10)
        aa = rotateLeft(aa + f4(bb, cc, dd) + xArray[15] + 0x5c4dd124, 7) + ee
        cc = rotateLeft(cc, 10)
        ee = rotateLeft(ee + f4(aa, bb, cc) + xArray[8] + 0x5c4dd124, 12) + dd
        bb = rotateLeft(bb, 10)
        dd = rotateLeft(dd + f4(ee, aa, bb) + xArray[12] + 0x5c4dd124, 7) + cc
        aa = rotateLeft(aa, 10)
        cc = rotateLeft(cc + f4(dd, ee, aa) + xArray[4] + 0x5c4dd124, 6) + bb
        ee = rotateLeft(ee, 10)
        bb = rotateLeft(bb + f4(cc, dd, ee) + xArray[9] + 0x5c4dd124, 15) + aa
        dd = rotateLeft(dd, 10)
        aa = rotateLeft(aa + f4(bb, cc, dd) + xArray[1] + 0x5c4dd124, 13) + ee
        cc = rotateLeft(cc, 10)
        ee = rotateLeft(ee + f4(aa, bb, cc) + xArray[2] + 0x5c4dd124, 11) + dd
        bb = rotateLeft(bb, 10)

        //
        // Rounds 32-47
        //
        // left
        d = rotateLeft(d + f3(e, a, b) + xArray[3] + 0x6ed9eba1, 11) + c
        a = rotateLeft(a, 10)
        c = rotateLeft(c + f3(d, e, a) + xArray[10] + 0x6ed9eba1, 13) + b
        e = rotateLeft(e, 10)
        b = rotateLeft(b + f3(c, d, e) + xArray[14] + 0x6ed9eba1, 6) + a
        d = rotateLeft(d, 10)
        a = rotateLeft(a + f3(b, c, d) + xArray[4] + 0x6ed9eba1, 7) + e
        c = rotateLeft(c, 10)
        e = rotateLeft(e + f3(a, b, c) + xArray[9] + 0x6ed9eba1, 14) + d
        b = rotateLeft(b, 10)
        d = rotateLeft(d + f3(e, a, b) + xArray[15] + 0x6ed9eba1, 9) + c
        a = rotateLeft(a, 10)
        c = rotateLeft(c + f3(d, e, a) + xArray[8] + 0x6ed9eba1, 13) + b
        e = rotateLeft(e, 10)
        b = rotateLeft(b + f3(c, d, e) + xArray[1] + 0x6ed9eba1, 15) + a
        d = rotateLeft(d, 10)
        a = rotateLeft(a + f3(b, c, d) + xArray[2] + 0x6ed9eba1, 14) + e
        c = rotateLeft(c, 10)
        e = rotateLeft(e + f3(a, b, c) + xArray[7] + 0x6ed9eba1, 8) + d
        b = rotateLeft(b, 10)
        d = rotateLeft(d + f3(e, a, b) + xArray[0] + 0x6ed9eba1, 13) + c
        a = rotateLeft(a, 10)
        c = rotateLeft(c + f3(d, e, a) + xArray[6] + 0x6ed9eba1, 6) + b
        e = rotateLeft(e, 10)
        b = rotateLeft(b + f3(c, d, e) + xArray[13] + 0x6ed9eba1, 5) + a
        d = rotateLeft(d, 10)
        a = rotateLeft(a + f3(b, c, d) + xArray[11] + 0x6ed9eba1, 12) + e
        c = rotateLeft(c, 10)
        e = rotateLeft(e + f3(a, b, c) + xArray[5] + 0x6ed9eba1, 7) + d
        b = rotateLeft(b, 10)
        d = rotateLeft(d + f3(e, a, b) + xArray[12] + 0x6ed9eba1, 5) + c
        a = rotateLeft(a, 10)

        // right
        dd = rotateLeft(dd + f3(ee, aa, bb) + xArray[15] + 0x6d703ef3, 9) + cc
        aa = rotateLeft(aa, 10)
        cc = rotateLeft(cc + f3(dd, ee, aa) + xArray[5] + 0x6d703ef3, 7) + bb
        ee = rotateLeft(ee, 10)
        bb = rotateLeft(bb + f3(cc, dd, ee) + xArray[1] + 0x6d703ef3, 15) + aa
        dd = rotateLeft(dd, 10)
        aa = rotateLeft(aa + f3(bb, cc, dd) + xArray[3] + 0x6d703ef3, 11) + ee
        cc = rotateLeft(cc, 10)
        ee = rotateLeft(ee + f3(aa, bb, cc) + xArray[7] + 0x6d703ef3, 8) + dd
        bb = rotateLeft(bb, 10)
        dd = rotateLeft(dd + f3(ee, aa, bb) + xArray[14] + 0x6d703ef3, 6) + cc
        aa = rotateLeft(aa, 10)
        cc = rotateLeft(cc + f3(dd, ee, aa) + xArray[6] + 0x6d703ef3, 6) + bb
        ee = rotateLeft(ee, 10)
        bb = rotateLeft(bb + f3(cc, dd, ee) + xArray[9] + 0x6d703ef3, 14) + aa
        dd = rotateLeft(dd, 10)
        aa = rotateLeft(aa + f3(bb, cc, dd) + xArray[11] + 0x6d703ef3, 12) + ee
        cc = rotateLeft(cc, 10)
        ee = rotateLeft(ee + f3(aa, bb, cc) + xArray[8] + 0x6d703ef3, 13) + dd
        bb = rotateLeft(bb, 10)
        dd = rotateLeft(dd + f3(ee, aa, bb) + xArray[12] + 0x6d703ef3, 5) + cc
        aa = rotateLeft(aa, 10)
        cc = rotateLeft(cc + f3(dd, ee, aa) + xArray[2] + 0x6d703ef3, 14) + bb
        ee = rotateLeft(ee, 10)
        bb = rotateLeft(bb + f3(cc, dd, ee) + xArray[10] + 0x6d703ef3, 13) + aa
        dd = rotateLeft(dd, 10)
        aa = rotateLeft(aa + f3(bb, cc, dd) + xArray[0] + 0x6d703ef3, 13) + ee
        cc = rotateLeft(cc, 10)
        ee = rotateLeft(ee + f3(aa, bb, cc) + xArray[4] + 0x6d703ef3, 7) + dd
        bb = rotateLeft(bb, 10)
        dd = rotateLeft(dd + f3(ee, aa, bb) + xArray[13] + 0x6d703ef3, 5) + cc
        aa = rotateLeft(aa, 10)

        //
        // Rounds 48-63
        //
        // left
        c = rotateLeft(c + f4(d, e, a) + xArray[1] + -0x70e44324, 11) + b
        e = rotateLeft(e, 10)
        b = rotateLeft(b + f4(c, d, e) + xArray[9] + -0x70e44324, 12) + a
        d = rotateLeft(d, 10)
        a = rotateLeft(a + f4(b, c, d) + xArray[11] + -0x70e44324, 14) + e
        c = rotateLeft(c, 10)
        e = rotateLeft(e + f4(a, b, c) + xArray[10] + -0x70e44324, 15) + d
        b = rotateLeft(b, 10)
        d = rotateLeft(d + f4(e, a, b) + xArray[0] + -0x70e44324, 14) + c
        a = rotateLeft(a, 10)
        c = rotateLeft(c + f4(d, e, a) + xArray[8] + -0x70e44324, 15) + b
        e = rotateLeft(e, 10)
        b = rotateLeft(b + f4(c, d, e) + xArray[12] + -0x70e44324, 9) + a
        d = rotateLeft(d, 10)
        a = rotateLeft(a + f4(b, c, d) + xArray[4] + -0x70e44324, 8) + e
        c = rotateLeft(c, 10)
        e = rotateLeft(e + f4(a, b, c) + xArray[13] + -0x70e44324, 9) + d
        b = rotateLeft(b, 10)
        d = rotateLeft(d + f4(e, a, b) + xArray[3] + -0x70e44324, 14) + c
        a = rotateLeft(a, 10)
        c = rotateLeft(c + f4(d, e, a) + xArray[7] + -0x70e44324, 5) + b
        e = rotateLeft(e, 10)
        b = rotateLeft(b + f4(c, d, e) + xArray[15] + -0x70e44324, 6) + a
        d = rotateLeft(d, 10)
        a = rotateLeft(a + f4(b, c, d) + xArray[14] + -0x70e44324, 8) + e
        c = rotateLeft(c, 10)
        e = rotateLeft(e + f4(a, b, c) + xArray[5] + -0x70e44324, 6) + d
        b = rotateLeft(b, 10)
        d = rotateLeft(d + f4(e, a, b) + xArray[6] + -0x70e44324, 5) + c
        a = rotateLeft(a, 10)
        c = rotateLeft(c + f4(d, e, a) + xArray[2] + -0x70e44324, 12) + b
        e = rotateLeft(e, 10)

        // right
        cc = rotateLeft(cc + f2(dd, ee, aa) + xArray[8] + 0x7a6d76e9, 15) + bb
        ee = rotateLeft(ee, 10)
        bb = rotateLeft(bb + f2(cc, dd, ee) + xArray[6] + 0x7a6d76e9, 5) + aa
        dd = rotateLeft(dd, 10)
        aa = rotateLeft(aa + f2(bb, cc, dd) + xArray[4] + 0x7a6d76e9, 8) + ee
        cc = rotateLeft(cc, 10)
        ee = rotateLeft(ee + f2(aa, bb, cc) + xArray[1] + 0x7a6d76e9, 11) + dd
        bb = rotateLeft(bb, 10)
        dd = rotateLeft(dd + f2(ee, aa, bb) + xArray[3] + 0x7a6d76e9, 14) + cc
        aa = rotateLeft(aa, 10)
        cc = rotateLeft(cc + f2(dd, ee, aa) + xArray[11] + 0x7a6d76e9, 14) + bb
        ee = rotateLeft(ee, 10)
        bb = rotateLeft(bb + f2(cc, dd, ee) + xArray[15] + 0x7a6d76e9, 6) + aa
        dd = rotateLeft(dd, 10)
        aa = rotateLeft(aa + f2(bb, cc, dd) + xArray[0] + 0x7a6d76e9, 14) + ee
        cc = rotateLeft(cc, 10)
        ee = rotateLeft(ee + f2(aa, bb, cc) + xArray[5] + 0x7a6d76e9, 6) + dd
        bb = rotateLeft(bb, 10)
        dd = rotateLeft(dd + f2(ee, aa, bb) + xArray[12] + 0x7a6d76e9, 9) + cc
        aa = rotateLeft(aa, 10)
        cc = rotateLeft(cc + f2(dd, ee, aa) + xArray[2] + 0x7a6d76e9, 12) + bb
        ee = rotateLeft(ee, 10)
        bb = rotateLeft(bb + f2(cc, dd, ee) + xArray[13] + 0x7a6d76e9, 9) + aa
        dd = rotateLeft(dd, 10)
        aa = rotateLeft(aa + f2(bb, cc, dd) + xArray[9] + 0x7a6d76e9, 12) + ee
        cc = rotateLeft(cc, 10)
        ee = rotateLeft(ee + f2(aa, bb, cc) + xArray[7] + 0x7a6d76e9, 5) + dd
        bb = rotateLeft(bb, 10)
        dd = rotateLeft(dd + f2(ee, aa, bb) + xArray[10] + 0x7a6d76e9, 15) + cc
        aa = rotateLeft(aa, 10)
        cc = rotateLeft(cc + f2(dd, ee, aa) + xArray[14] + 0x7a6d76e9, 8) + bb
        ee = rotateLeft(ee, 10)

        //
        // Rounds 64-79
        //
        // left
        b = rotateLeft(b + f5(c, d, e) + xArray[4] + -0x56ac02b2, 9) + a
        d = rotateLeft(d, 10)
        a = rotateLeft(a + f5(b, c, d) + xArray[0] + -0x56ac02b2, 15) + e
        c = rotateLeft(c, 10)
        e = rotateLeft(e + f5(a, b, c) + xArray[5] + -0x56ac02b2, 5) + d
        b = rotateLeft(b, 10)
        d = rotateLeft(d + f5(e, a, b) + xArray[9] + -0x56ac02b2, 11) + c
        a = rotateLeft(a, 10)
        c = rotateLeft(c + f5(d, e, a) + xArray[7] + -0x56ac02b2, 6) + b
        e = rotateLeft(e, 10)
        b = rotateLeft(b + f5(c, d, e) + xArray[12] + -0x56ac02b2, 8) + a
        d = rotateLeft(d, 10)
        a = rotateLeft(a + f5(b, c, d) + xArray[2] + -0x56ac02b2, 13) + e
        c = rotateLeft(c, 10)
        e = rotateLeft(e + f5(a, b, c) + xArray[10] + -0x56ac02b2, 12) + d
        b = rotateLeft(b, 10)
        d = rotateLeft(d + f5(e, a, b) + xArray[14] + -0x56ac02b2, 5) + c
        a = rotateLeft(a, 10)
        c = rotateLeft(c + f5(d, e, a) + xArray[1] + -0x56ac02b2, 12) + b
        e = rotateLeft(e, 10)
        b = rotateLeft(b + f5(c, d, e) + xArray[3] + -0x56ac02b2, 13) + a
        d = rotateLeft(d, 10)
        a = rotateLeft(a + f5(b, c, d) + xArray[8] + -0x56ac02b2, 14) + e
        c = rotateLeft(c, 10)
        e = rotateLeft(e + f5(a, b, c) + xArray[11] + -0x56ac02b2, 11) + d
        b = rotateLeft(b, 10)
        d = rotateLeft(d + f5(e, a, b) + xArray[6] + -0x56ac02b2, 8) + c
        a = rotateLeft(a, 10)
        c = rotateLeft(c + f5(d, e, a) + xArray[15] + -0x56ac02b2, 5) + b
        e = rotateLeft(e, 10)
        b = rotateLeft(b + f5(c, d, e) + xArray[13] + -0x56ac02b2, 6) + a
        d = rotateLeft(d, 10)

        // right
        bb = rotateLeft(bb + f1(cc, dd, ee) + xArray[12], 8) + aa
        dd = rotateLeft(dd, 10)
        aa = rotateLeft(aa + f1(bb, cc, dd) + xArray[15], 5) + ee
        cc = rotateLeft(cc, 10)
        ee = rotateLeft(ee + f1(aa, bb, cc) + xArray[10], 12) + dd
        bb = rotateLeft(bb, 10)
        dd = rotateLeft(dd + f1(ee, aa, bb) + xArray[4], 9) + cc
        aa = rotateLeft(aa, 10)
        cc = rotateLeft(cc + f1(dd, ee, aa) + xArray[1], 12) + bb
        ee = rotateLeft(ee, 10)
        bb = rotateLeft(bb + f1(cc, dd, ee) + xArray[5], 5) + aa
        dd = rotateLeft(dd, 10)
        aa = rotateLeft(aa + f1(bb, cc, dd) + xArray[8], 14) + ee
        cc = rotateLeft(cc, 10)
        ee = rotateLeft(ee + f1(aa, bb, cc) + xArray[7], 6) + dd
        bb = rotateLeft(bb, 10)
        dd = rotateLeft(dd + f1(ee, aa, bb) + xArray[6], 8) + cc
        aa = rotateLeft(aa, 10)
        cc = rotateLeft(cc + f1(dd, ee, aa) + xArray[2], 13) + bb
        ee = rotateLeft(ee, 10)
        bb = rotateLeft(bb + f1(cc, dd, ee) + xArray[13], 6) + aa
        dd = rotateLeft(dd, 10)
        aa = rotateLeft(aa + f1(bb, cc, dd) + xArray[14], 5) + ee
        cc = rotateLeft(cc, 10)
        ee = rotateLeft(ee + f1(aa, bb, cc) + xArray[0], 15) + dd
        bb = rotateLeft(bb, 10)
        dd = rotateLeft(dd + f1(ee, aa, bb) + xArray[3], 13) + cc
        aa = rotateLeft(aa, 10)
        cc = rotateLeft(cc + f1(dd, ee, aa) + xArray[9], 11) + bb
        ee = rotateLeft(ee, 10)
        bb = rotateLeft(bb + f1(cc, dd, ee) + xArray[11], 11) + aa
        dd = rotateLeft(dd, 10)

        dd += c + h1
        h1 = h2 + d + ee
        h2 = h3 + e + aa
        h3 = h4 + a + bb
        h4 = h0 + b + cc
        h0 = dd

        //
        // reset the offset and clean out the word buffer.
        //
        xOff = 0
        for (i in xArray.indices) {
            xArray[i] = 0
        }
    }

    override fun copy(): Memoable = RIPEMD160Digest(this)

    override fun reset(other: Memoable) {
        val d = other as RIPEMD160Digest

        copyIn(d)
    }

    companion object {
        private const val DIGEST_LENGTH = 20
    }
}
