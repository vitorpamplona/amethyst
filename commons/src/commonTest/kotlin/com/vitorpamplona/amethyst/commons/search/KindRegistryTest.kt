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
package com.vitorpamplona.amethyst.commons.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KindRegistryTest {
    @Test
    fun resolveNote() {
        assertEquals(listOf(1), KindRegistry.resolve("note"))
    }

    @Test
    fun resolveArticle() {
        assertEquals(listOf(30023), KindRegistry.resolve("article"))
    }

    @Test
    fun resolveChannel() {
        val kinds = KindRegistry.resolve("channel")!!
        assertTrue(40 in kinds)
        assertTrue(41 in kinds)
    }

    @Test
    fun resolveCaseInsensitive() {
        assertEquals(KindRegistry.resolve("NOTE"), KindRegistry.resolve("note"))
    }

    @Test
    fun resolveUnknown() {
        assertNull(KindRegistry.resolve("unknown"))
    }

    @Test
    fun isPseudoKindReply() {
        assertTrue(KindRegistry.isPseudoKind("reply"))
        assertTrue(KindRegistry.isPseudoKind("Reply"))
    }

    @Test
    fun isPseudoKindMedia() {
        assertTrue(KindRegistry.isPseudoKind("media"))
    }

    @Test
    fun isNotPseudoKind() {
        assertFalse(KindRegistry.isPseudoKind("note"))
        assertFalse(KindRegistry.isPseudoKind("article"))
    }

    @Test
    fun nameForKind1() {
        assertEquals("note", KindRegistry.nameFor(1))
    }

    @Test
    fun nameForKind30023() {
        assertEquals("article", KindRegistry.nameFor(30023))
    }

    @Test
    fun nameForUnknownKind() {
        assertNull(KindRegistry.nameFor(99999))
    }

    @Test
    fun allAliasesResolve() {
        KindRegistry.aliases.forEach { (alias, kinds) ->
            assertEquals(kinds, KindRegistry.resolve(alias))
        }
    }

    @Test
    fun presetsContainExpectedEntries() {
        assertTrue(KindRegistry.presets.containsKey("Notes"))
        assertTrue(KindRegistry.presets.containsKey("Articles"))
        assertTrue(KindRegistry.presets.containsKey("Media"))
        assertTrue(KindRegistry.presets.containsKey("Channels"))
    }
}
