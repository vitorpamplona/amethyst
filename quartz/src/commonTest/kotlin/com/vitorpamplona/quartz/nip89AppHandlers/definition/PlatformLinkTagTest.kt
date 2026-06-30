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
package com.vitorpamplona.quartz.nip89AppHandlers.definition

import com.vitorpamplona.quartz.nip89AppHandlers.definition.tags.PlatformLinkTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PlatformLinkTagTest {
    @Test
    fun parsesWebPlatformLinkWithEntityType() {
        val tag = arrayOf("web", "https://example.com/a/<bech32>", "nevent")
        val result = PlatformLinkTag.parse(tag)

        assertNotNull(result)
        assertEquals("web", result.platform)
        assertEquals("https://example.com/a/<bech32>", result.uri)
        assertEquals("nevent", result.entityType)
    }

    @Test
    fun parsesAndroidPlatformLink() {
        val tag = arrayOf("android", "amethyst://note/<bech32>", "note")
        val result = PlatformLinkTag.parse(tag)

        assertNotNull(result)
        assertEquals("android", result.platform)
        assertEquals("amethyst://note/<bech32>", result.uri)
        assertEquals("note", result.entityType)
    }

    @Test
    fun parsesIosPlatformLink() {
        val tag = arrayOf("ios", "damus://note/<bech32>", "naddr")
        val result = PlatformLinkTag.parse(tag)

        assertNotNull(result)
        assertEquals("ios", result.platform)
        assertEquals("damus://note/<bech32>", result.uri)
        assertEquals("naddr", result.entityType)
    }

    @Test
    fun rejectsTagWithOnlyOneElement() {
        val tag = arrayOf("web")
        assertNull(PlatformLinkTag.parse(tag))
    }

    @Test
    fun rejectsUnknownPlatform() {
        val tag = arrayOf("windows", "https://example.com", "note")
        assertNull(PlatformLinkTag.parse(tag))
    }

    @Test
    fun roundTripPreservesValues() {
        val original = PlatformLinkTag("web", "https://example.com/e/<bech32>", "nevent")
        val tagArray = original.toTagArray()
        val parsed = PlatformLinkTag.parse(tagArray)

        assertNotNull(parsed)
        assertEquals(original.platform, parsed.platform)
        assertEquals(original.uri, parsed.uri)
        assertEquals(original.entityType, parsed.entityType)
    }

    @Test
    fun roundTripWithoutEntityType() {
        val original = PlatformLinkTag("android", "amethyst://open/<bech32>", null)
        val tagArray = original.toTagArray()
        val parsed = PlatformLinkTag.parse(tagArray)

        // The entity type is optional per NIP-89, so a 2-element link must still parse.
        assertNotNull(parsed)
        assertEquals("android", parsed.platform)
        assertEquals("amethyst://open/<bech32>", parsed.uri)
        assertNull(parsed.entityType)
    }

    @Test
    fun parsesAndroidIntentLinkWithoutEntityType() {
        // The real-world shape published by NostrHub: a 2-element android intent link.
        val tag =
            arrayOf(
                "android",
                "intent:<bech32>#Intent;scheme=nostr;package=com.vitorpamplona.amethyst;end`;",
            )
        val result = PlatformLinkTag.parse(tag)

        assertNotNull(result)
        assertEquals("android", result.platform)
        assertEquals("intent:<bech32>#Intent;scheme=nostr;package=com.vitorpamplona.amethyst;end`;", result.uri)
        assertNull(result.entityType)
    }
}
