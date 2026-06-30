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

import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppDefinitionEventTest {
    // A trimmed-down copy of the real NostrHub-published Amethyst handler, keeping at
    // least one of every tag kind we now parse.
    private val event =
        AppDefinitionEvent(
            id = "00",
            pubKey = "aa",
            createdAt = 1782775172,
            content = """{"name":"Amethyst","about":"Nostr client for Android","website":"https://amethyst.social/"}""",
            sig = "00",
            tags =
                arrayOf(
                    arrayOf("d", "1685802317447"),
                    arrayOf("alt", "NIP-89 handler: Amethyst"),
                    arrayOf("k", "0"),
                    arrayOf("k", "1"),
                    arrayOf("android", "intent:<bech32>#Intent;scheme=nostr;package=com.vitorpamplona.amethyst;end`;"),
                    arrayOf("t", "social"),
                    arrayOf("t", "video"),
                    arrayOf("i", "https://github.com/nostr-protocol/nips/blob/master/01.md"),
                    arrayOf("i", "https://github.com/nostr-protocol/nips/blob/master/5A.md"),
                    arrayOf("a", "30617:460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c:amethyst", "wss://relay.ngit.dev/"),
                    arrayOf("client", "NostrHub"),
                ),
        )

    @Test
    fun parsesMetadataAndKinds() {
        assertEquals("Amethyst", event.appMetaData()?.name)
        assertEquals(setOf(0, 1), event.supportedKinds().toSet())
    }

    @Test
    fun parsesAndroidPlatformLinkWithoutEntityType() {
        // The fix for has(2) -> has(1): a 2-element android tag must now be picked up.
        val links = event.platformLinks()
        assertEquals(1, links.size)
        assertEquals("android", links[0].platform)
    }

    @Test
    fun parsesCategories() {
        assertEquals(listOf("social", "video"), event.categories())
    }

    @Test
    fun parsesSupportedNips() {
        assertEquals(listOf("01", "5A"), event.supportedNips().map { it.nip })
    }

    @Test
    fun parsesRelatedAddresses() {
        val addresses = event.relatedAddresses()
        assertEquals(1, addresses.size)
        assertEquals(30617, addresses[0].kind)
        assertEquals("amethyst", addresses[0].dTag)
    }

    @Test
    fun parsesClient() {
        assertEquals("NostrHub", event.client()?.name)
    }

    @Test
    fun buildWritesAllTags() {
        val template =
            AppDefinitionEvent.build(
                details = AppMetadata().apply { name = "Amethyst" },
                supportedKinds = setOf(1, 30023),
                links = emptyList(),
                categories = listOf("social"),
                supportedNips = listOf("https://github.com/nostr-protocol/nips/blob/master/01.md"),
                relatedAddresses = listOf(ATag(30617, "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c", "amethyst")),
                client = "Amethyst",
                dTag = "test",
            )

        assertEquals(AppDefinitionEvent.KIND, template.kind)
        assertTrue(template.tags.any { it[0] == "t" && it[1] == "social" })
        assertTrue(template.tags.any { it[0] == "i" && it[1].endsWith("01.md") })
        assertTrue(template.tags.any { it[0] == "a" && it[1].startsWith("30617:") })

        val client = template.tags.firstOrNull { it[0] == "client" }
        assertNotNull(client)
        assertEquals("Amethyst", client[1])
    }
}
