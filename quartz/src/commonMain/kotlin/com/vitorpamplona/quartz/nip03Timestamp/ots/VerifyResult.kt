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
package com.vitorpamplona.quartz.nip03Timestamp.ots

/**
 * Class that lets us compare, sort, store and print timestamps.
 */
class VerifyResult(
    val timestamp: Long?,
    val height: Int,
) : Comparable<VerifyResult> {
    enum class Chains { BITCOIN }

    /**
     * Returns, if existing, a string representation describing the existence of a block attest
     */
    override fun toString(): String {
        if (height == 0 || timestamp == null) {
            return ""
        }

        return "block $height attests data existed as of unix timestamp of $timestamp"
    }

    override fun compareTo(other: VerifyResult): Int = this.height - other.height

    override fun equals(other: Any?): Boolean {
        val vr = other as VerifyResult
        return this.timestamp == vr.timestamp && this.height == vr.height
    }

    override fun hashCode(): Int = (((this.timestamp) as Long).toInt()) xor this.height
}
