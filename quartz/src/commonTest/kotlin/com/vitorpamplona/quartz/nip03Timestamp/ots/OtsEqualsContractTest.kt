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

import com.vitorpamplona.quartz.nip03Timestamp.ots.op.Op
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpAppend
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpKECCAK256
import com.vitorpamplona.quartz.nip03Timestamp.ots.op.OpPrepend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Equals/hashCode contract tests for the types keying [Timestamp.ops]
 * (a MutableMap<Op, Timestamp>): equal ops MUST hash equally or map
 * lookups produce duplicate branches and failed upgrades.
 */
class OtsEqualsContractTest {
    // --- OpKECCAK256: equals existed without hashCode; equal instances hashed by identity ---

    @Test
    fun keccakInstancesAreEqualAndHashEqually() {
        val a = OpKECCAK256()
        val b = OpKECCAK256()

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun keccakWorksAsMapKey() {
        val map = mutableMapOf<Op, String>(OpKECCAK256() to "branch")

        assertEquals("branch", map[OpKECCAK256()])
    }

    // --- OpBinary: equals is defined once on the superclass, keyed on tag() + arg content ---

    @Test
    fun binaryOpsWithSameArgAndClassAreEqual() {
        val arg = byteArrayOf(1, 2, 3)

        assertEquals(OpAppend(arg), OpAppend(byteArrayOf(1, 2, 3)))
        assertEquals(OpAppend(arg).hashCode(), OpAppend(byteArrayOf(1, 2, 3)).hashCode())
    }

    @Test
    fun binaryOpsWithDifferentArgAreNotEqual() {
        assertNotEquals(OpAppend(byteArrayOf(1, 2, 3)), OpAppend(byteArrayOf(9)))
    }

    @Test
    fun appendAndPrependWithSameArgAreNotEqual() {
        // Same arg content, different tag — the superclass equals must not conflate them.
        val arg = byteArrayOf(1, 2, 3)

        assertNotEquals<Op>(OpAppend(arg), OpPrepend(arg))
    }

    @Test
    fun binaryOpsWorkAsMapKeys() {
        val map = mutableMapOf<Op, String>()
        map[OpAppend(byteArrayOf(1, 2, 3))] = "append"
        map[OpPrepend(byteArrayOf(1, 2, 3))] = "prepend"

        assertEquals(2, map.size)
        assertEquals("append", map[OpAppend(byteArrayOf(1, 2, 3))])
        assertEquals("prepend", map[OpPrepend(byteArrayOf(1, 2, 3))])
    }

    // --- VerifyResult: equals used to throw on null/foreign types; hashCode NPEd on null timestamp ---

    @Test
    fun verifyResultEqualsNullIsFalse() {
        assertFalse(VerifyResult(1234L, 100).equals(null))
    }

    @Test
    fun verifyResultEqualsForeignTypeIsFalse() {
        assertFalse(VerifyResult(1234L, 100).equals("not a VerifyResult"))
    }

    @Test
    fun verifyResultWithNullTimestampHashesWithoutThrowing() {
        val result = VerifyResult(null, 100)

        result.hashCode()
        assertTrue(result == VerifyResult(null, 100))
    }
}
