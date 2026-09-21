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
package com.vitorpamplona.quartz.nip01Core.kotlinSerialization

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The readers behind the NIP-47 and CLINK parsers, where every byte comes from
 * somebody else's wallet. Their contract is that a field of the wrong shape reads
 * as null and the rest of the message still parses — never a wrong value.
 */
class LenientJsonTest {
    private fun obj(json: String) = Json.parseToJsonElement(json) as JsonObject

    @Test
    fun intOutOfRangeReadsAsNullRatherThanWrapping() {
        // 2^32 truncates to 0 and 3_000_000_000 to a negative int if the Long is
        // narrowed with toInt(). A limit of 0 or a negative offset is worse than
        // no value at all: it is a plausible-looking wrong answer.
        assertNull(obj("""{"limit":4294967296}""").intOrNull("limit"))
        assertNull(obj("""{"offset":3000000000}""").intOrNull("offset"))
        assertNull(obj("""{"n":-3000000000}""").intOrNull("n"))
    }

    @Test
    fun intInRangeStillReads() {
        assertEquals(42, obj("""{"n":42}""").intOrNull("n"))
        assertEquals(Int.MAX_VALUE, obj("""{"n":2147483647}""").intOrNull("n"))
        assertEquals(Int.MIN_VALUE, obj("""{"n":-2147483648}""").intOrNull("n"))
        // the same string/float tolerance longOrNull has
        assertEquals(12, obj("""{"n":"12"}""").intOrNull("n"))
        assertEquals(12, obj("""{"n":12.0}""").intOrNull("n"))
    }

    @Test
    fun longAcceptsTheThreeShapesWalletsSend() {
        assertEquals(12L, obj("""{"n":12}""").longOrNull("n"))
        assertEquals(12L, obj("""{"n":"12"}""").longOrNull("n"))
        assertEquals(12L, obj("""{"n":12.0}""").longOrNull("n"))
        assertNull(obj("""{"n":{"nested":1}}""").longOrNull("n"))
        assertNull(obj("""{"n":null}""").longOrNull("n"))
        assertNull(obj("""{}""").longOrNull("n"))
    }

    @Test
    fun doubleRejectsNonFiniteText() {
        // "NaN" and "Infinity" parse as Doubles in Kotlin but are not values any
        // caller can do arithmetic with.
        assertNull(obj("""{"n":"NaN"}""").doubleOrNull("n"))
        assertNull(obj("""{"n":"Infinity"}""").doubleOrNull("n"))
        assertEquals(1.5, obj("""{"n":1.5}""").doubleOrNull("n"))
    }
}
