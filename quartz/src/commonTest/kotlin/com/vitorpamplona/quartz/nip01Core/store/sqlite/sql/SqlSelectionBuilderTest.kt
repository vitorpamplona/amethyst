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
package com.vitorpamplona.quartz.nip01Core.store.sqlite.sql

import kotlin.test.Test
import kotlin.test.assertEquals

class SqlSelectionBuilderTest {
    @Test
    fun equalsWithNullProducesIsNull() {
        val clause = where { equals("col", null) }
        assertEquals("col IS NULL", clause.conditions)
        assertEquals(emptyList(), clause.args)
    }

    @Test
    fun notEqualsWithNullProducesIsNotNull() {
        val clause = where { notEquals("col", null) }
        assertEquals("col IS NOT NULL", clause.conditions)
        assertEquals(emptyList(), clause.args)
    }

    @Test
    fun notEqualsWithValueProducesPlaceholder() {
        val clause = where { notEquals("col", "x") }
        assertEquals("col != ?", clause.conditions)
        assertEquals(listOf("x"), clause.args)
    }

    @Test
    fun emptyInBecomesAlwaysFalse() {
        val clause = where { isIn("col", emptyList()) }
        assertEquals("1 = 0", clause.conditions)
        assertEquals(emptyList(), clause.args)
    }

    @Test
    fun equalsOrInPicksEqualsForSingleton() {
        val clause = where { equalsOrIn("col", listOf("x")) }
        assertEquals("col = ?", clause.conditions)
        assertEquals(listOf("x"), clause.args)
    }

    @Test
    fun equalsOrInPicksInForMultiple() {
        val clause = where { equalsOrIn("col", listOf("a", "b", "c")) }
        assertEquals("col IN (?, ?, ?)", clause.conditions)
        assertEquals(listOf("a", "b", "c"), clause.args)
    }
}
