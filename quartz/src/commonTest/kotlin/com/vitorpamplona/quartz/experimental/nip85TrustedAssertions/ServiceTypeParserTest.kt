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
package com.vitorpamplona.quartz.experimental.nip85TrustedAssertions

import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServiceTypeParserTest {
    @Test
    fun parseValid() {
        assertEquals(ServiceType(30382, "rank"), ServiceType.parse("30382:rank"))
    }

    @Test
    fun parseKeepsColonsInTheType() {
        assertEquals(ServiceType(30382, "rank:extra"), ServiceType.parse("30382:rank:extra"))
    }

    @Test
    fun parseWithoutSeparator() {
        // a `["client", "nostria"]` tag ends up here. Must not crash.
        assertNull(ServiceType.parse("client"))
    }

    @Test
    fun parseEmpty() {
        assertNull(ServiceType.parse(""))
    }

    @Test
    fun parseWithoutKind() {
        assertNull(ServiceType.parse(":rank"))
    }

    @Test
    fun parseWithoutType() {
        assertNull(ServiceType.parse("30382:"))
    }

    @Test
    fun parseNonNumericKind() {
        assertNull(ServiceType.parse("client:nostria"))
    }

    @Test
    fun isOfKindMatches() {
        assertTrue(ServiceType.isOfKind("30382:rank", "30382"))
    }

    @Test
    fun isOfKindOnPrefixWithoutSeparator() {
        // "30382" starts with "30382" but has no `:` at that position. Must not crash.
        assertFalse(ServiceType.isOfKind("30382", "30382"))
        assertFalse(ServiceType.isOfKind("", ""))
        assertFalse(ServiceType.isOfKind("303820:rank", "30382"))
        assertFalse(ServiceType.isOfKind("30383:rank", "30382"))
    }
}
