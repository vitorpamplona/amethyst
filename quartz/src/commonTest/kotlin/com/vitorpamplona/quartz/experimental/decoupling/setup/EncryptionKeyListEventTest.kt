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
package com.vitorpamplona.quartz.experimental.decoupling.setup

import com.vitorpamplona.quartz.experimental.decoupling.setup.tags.KeyTag
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class EncryptionKeyListEventTest {
    // The `n` value of a kind 10044 seen on relays (2026-10): every live list uses this two-element
    // draft form, which the original three-element parser rejected, so keys() came back empty.
    private val liveKey = "e255c46bca83c71043b170884e68a9ece4f7f35d05415e46fd7b86b8607b3eb4"
    private val author = "a".repeat(64)

    private fun list(vararg tags: Array<String>) = EventFactory.create<EncryptionKeyListEvent>("0".repeat(64), author, 1_790_000_000L, EncryptionKeyListEvent.KIND, arrayOf(*tags), "", "0".repeat(128))

    @Test
    fun readsTheDraftForm() {
        val event = list(arrayOf("n", liveKey))
        assertIs<EncryptionKeyListEvent>(event)
        assertEquals(liveKey, event.encryptionKey()?.pubkey)
        assertNull(event.encryptionKey()?.nonce)
        assertEquals(listOf(liveKey), event.keys().map { it.pubkey })
    }

    @Test
    fun stillReadsTheLegacyNonceForm() {
        val event = list(arrayOf("n", liveKey, "abcd"))
        assertEquals(liveKey, event.encryptionKey()?.pubkey)
        assertEquals("abcd", event.encryptionKey()?.nonce)
    }

    @Test
    fun theFirstWellFormedKeyIsTheEncryptionKey() {
        val second = "b".repeat(64)
        val event = list(arrayOf("n", "not-a-key"), arrayOf("n", liveKey), arrayOf("n", second))
        assertEquals(liveKey, event.encryptionKey()?.pubkey)
        assertEquals(listOf(liveKey, second), event.keys().map { it.pubkey })
    }

    @Test
    fun malformedOrMissingKeysReadAsNoKey() {
        val event = list(arrayOf("n"), arrayOf("n", ""), arrayOf("n", "zz".repeat(32)), arrayOf("p", liveKey))
        assertNull(event.encryptionKey())
        assertEquals(emptyList(), event.keys())
    }

    @Test
    fun buildWritesTheDraftForm() {
        val template = EncryptionKeyListEvent.build(liveKey.uppercase(), createdAt = 1L)
        assertContentEquals(arrayOf("n", liveKey), template.tags.single())
        assertContentEquals(arrayOf("n", liveKey, "ff"), KeyTag.assemble(liveKey, "ff"))
    }

    @Test
    fun removingAKeyReadFromAnUppercaseTagRemovesThatTag() {
        // parse() lowercases the key, so the removal must not compare case-sensitively.
        val event = list(arrayOf("n", liveKey.uppercase()))
        val key = event.keys().single()
        val template = EncryptionKeyListEvent.remove(key, event, createdAt = 1L)
        assertEquals(emptyList(), template.tags.filter { it[0] == "n" })
    }
}
