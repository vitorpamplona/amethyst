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
package com.vitorpamplona.quartz.experimental.profileTheme.active

import com.vitorpamplona.quartz.experimental.profileTheme.definition.ThemeDefinitionEvent
import com.vitorpamplona.quartz.experimental.profileTheme.tags.BackgroundMode
import com.vitorpamplona.quartz.experimental.profileTheme.tags.BackgroundTag
import com.vitorpamplona.quartz.experimental.profileTheme.tags.ColorRole
import com.vitorpamplona.quartz.experimental.profileTheme.tags.FontRole
import com.vitorpamplona.quartz.experimental.profileTheme.tags.FontTag
import com.vitorpamplona.quartz.experimental.profileTheme.tags.RgbColor
import com.vitorpamplona.quartz.experimental.profileTheme.tags.ThemeColors
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActiveProfileThemeEventTest {
    private val author = "208ad03138eb32da4b3fb2edb79d13a8e3532842db627d76aff8168db564a0e9"
    private val creator = "50d4cd35db40e57c414111eb945852cba60d2ab0e6cbfb5b88b4d5bb73394617"
    private val font = "https://cdn.jsdelivr.net/fontsource/fonts/jetbrains-mono:vf@latest/latin-wght-normal.woff2"
    private val definitionAddress = "36767:$creator:ghost-in-the-shell-m30ig5"

    /** A live Ditto 16767 (2026-10-08 census): a theme worn from another user's 36767. */
    private fun sample(): Event =
        EventFactory.create(
            id = "5dff1697e4c39b2a01eadec121038467587c6aaaa0e21accdc8b8fc63595bea1",
            pubKey = author,
            createdAt = 1791353522L,
            kind = 16767,
            tags =
                arrayOf(
                    arrayOf("c", "#0a0f0a", "background"),
                    arrayOf("c", "#33ff33", "text"),
                    arrayOf("c", "#00e5ff", "primary"),
                    arrayOf("f", "JetBrains Mono", font, "body"),
                    arrayOf("f", "JetBrains Mono", font, "title"),
                    arrayOf("bg", "url https://blossom.ditto.pub/8ed24aaba41cdcba929f6a2b1522143440841b8e488f950fecc73c0be7a124fe.jpeg", "mode cover", "m image/jpeg"),
                    arrayOf("alt", "Active profile theme"),
                    arrayOf("title", "Ghost in the Shell"),
                    arrayOf("a", definitionAddress),
                    arrayOf("p", creator),
                    arrayOf("client", "Ditto", "31990:781a1527055f74c1f70230f10384609b34548f8ab6a0a6caa74025827f9fdae5:ditto"),
                    arrayOf("published_at", "1791353522"),
                ),
            content = "",
            sig = "774c3348c551130a9b9572736055686e9b61930b3caf7c9393ce4d0421328e4752a437f7603544d7367cc125f96e3c2138165bbdc753dfb4fd740e29415905af",
        )

    private fun theme(vararg tags: Array<String>) = ActiveProfileThemeEvent("00".repeat(32), author, 1L, arrayOf(*tags), "", "00".repeat(64))

    @Test
    fun factoryDispatch() {
        assertIs<ActiveProfileThemeEvent>(sample())
        assertTrue(EventFactory.isKnownKind(ActiveProfileThemeEvent.KIND))
        assertFalse(sample() is SearchableEvent, "an active theme is profile state, not searchable content")
    }

    @Test
    fun replaceableAddress() {
        val event = assertIs<ActiveProfileThemeEvent>(sample())
        assertEquals("", event.dTag())
        assertEquals("16767:$author:", event.addressTag())
    }

    @Test
    fun readsTheLiveSample() {
        val event = assertIs<ActiveProfileThemeEvent>(sample())
        assertEquals(ThemeColors(RgbColor(0x0a0f0a), RgbColor(0x33ff33), RgbColor(0x00e5ff)), event.colors())
        assertEquals(RgbColor(0x33ff33), event.color(ColorRole.TEXT))
        assertEquals(FontTag("JetBrains Mono", font, FontRole.BODY), event.bodyFont())
        assertEquals(FontTag("JetBrains Mono", font, FontRole.TITLE), event.titleFont())
        assertEquals(2, event.fonts().size)
        val bg = assertNotNull(event.background())
        assertEquals(BackgroundMode.COVER, bg.mode)
        assertEquals("image/jpeg", bg.mimeType)
        assertEquals("Ghost in the Shell", event.title())
        assertNull(event.description())

        assertEquals(definitionAddress, event.adoptedFrom()?.toTag())
        assertEquals(creator, event.creator()?.pubKey)
        assertEquals(creator, event.creatorPubKey())
        assertTrue(event.isWornFromAnotherUser())
    }

    @Test
    fun hintsAndLinks() {
        val event = assertIs<ActiveProfileThemeEvent>(sample())
        assertEquals(listOf(creator), event.linkedPubKeys())
        assertEquals(listOf(definitionAddress), event.linkedAddressIds())
        // The live sample carries no relay hints.
        assertEquals(emptyList(), event.pubKeyHints())
        assertEquals(emptyList(), event.addressHints())

        val relay = NormalizedRelayUrl("wss://relay.ditto.pub/")
        val hinted = theme(arrayOf("a", definitionAddress, relay.url), arrayOf("p", creator, relay.url))
        assertEquals(listOf(AddressHint(definitionAddress, relay)), hinted.addressHints())
        assertEquals(listOf(PubKeyHint(creator, relay)), hinted.pubKeyHints())
    }

    @Test
    fun toleratesMalformedTags() {
        val event =
            theme(
                arrayOf("c", "#zzzzzz", "background"),
                arrayOf("c", "#ffffff"),
                arrayOf("c", "#000000", "text"),
                arrayOf("c", "#111111", "text"),
                arrayOf("f"),
                arrayOf("f", "Inter", "https://x.com/i.woff2", "caption"),
                arrayOf("bg", "mode cover"),
                arrayOf("a", "30023:$creator:not-a-theme"),
                arrayOf("a", "36767:not-a-pubkey:theme"),
                arrayOf("a", "garbage"),
                arrayOf("p", "short"),
            )
        // Background color is missing (malformed), so the theme is incomplete.
        assertNull(event.colors())
        assertNull(event.color(ColorRole.BACKGROUND))
        // One `c` per marker: the first wins.
        assertEquals(RgbColor(0x000000), event.color(ColorRole.TEXT))
        assertEquals(emptyList(), event.fonts())
        assertNull(event.background())
        assertNull(event.adoptedFrom())
        assertNull(event.creator())
        assertNull(event.creatorPubKey())
        assertFalse(event.isWornFromAnotherUser())
        assertEquals(emptyList(), event.linkedAddressIds())
        assertEquals(emptyList(), event.linkedPubKeys())
        assertEquals(emptyList(), event.addressHints())
    }

    @Test
    fun ownThemeIsNotWornFromAnotherUser() {
        assertFalse(theme(arrayOf("p", author)).isWornFromAnotherUser())
        // An `a` alone credits the definition's author.
        assertEquals(creator, theme(arrayOf("a", definitionAddress)).creatorPubKey())
    }

    @Test
    fun buildRoundTrip() {
        val colors = ThemeColors(RgbColor(0x1a1a2e), RgbColor(0xe0e0e0), RgbColor(0x6c3ce0))
        val body = FontTag("Inter", "https://example.com/inter.woff2", FontRole.BODY)
        val title = FontTag("Playfair Display", "https://example.com/playfair.woff2", FontRole.TITLE)
        val bg = BackgroundTag("https://example.com/bg.jpg", BackgroundMode.COVER, "image/jpeg")

        // Title font given first: the builder must still write body first.
        val template = ActiveProfileThemeEvent.build(colors, listOf(title, body), bg, "MK Dark Theme", "A dark one", createdAt = 1L)
        assertEquals(ActiveProfileThemeEvent.KIND, template.kind)
        assertEquals("", template.content)

        val fontTags = template.tags.filter { it[0] == "f" }
        assertEquals(listOf("body", "title"), fontTags.map { it[3] })

        val event = theme(*template.tags)
        assertEquals(colors, event.colors())
        assertEquals(body, event.bodyFont())
        assertEquals(title, event.titleFont())
        assertContentEquals(bg.toTagArray(), event.background()?.toTagArray())
        assertEquals("MK Dark Theme", event.title())
        assertEquals("A dark one", event.description())
        assertEquals(3, template.tags.count { it[0] == "c" })
    }

    @Test
    fun adoptsADefinitionWithAttribution() {
        val definition =
            ThemeDefinitionEvent(
                "00".repeat(32),
                creator,
                1L,
                arrayOf(
                    arrayOf("d", "ghost-in-the-shell-m30ig5"),
                    arrayOf("title", "Ghost in the Shell"),
                    arrayOf("c", "#0a0f0a", "background"),
                    arrayOf("c", "#33ff33", "text"),
                    arrayOf("c", "#00e5ff", "primary"),
                    arrayOf("f", "JetBrains Mono", font, "body"),
                ),
                "",
                "00".repeat(64),
            )
        val template = assertNotNull(ActiveProfileThemeEvent.adopt(definition, createdAt = 1L))
        val worn = theme(*template.tags)
        assertEquals(definition.colors(), worn.colors())
        assertEquals("Ghost in the Shell", worn.title())
        assertEquals(definitionAddress, worn.adoptedFrom()?.toTag())
        assertEquals(creator, worn.creator()?.pubKey)
        assertTrue(worn.isWornFromAnotherUser())

        // Incomplete definitions cannot be worn.
        val incomplete = ThemeDefinitionEvent("00".repeat(32), creator, 1L, arrayOf(arrayOf("d", "x")), "", "00".repeat(64))
        assertNull(ActiveProfileThemeEvent.adopt(incomplete))
    }

    @Test
    fun adoptingAnotherUsersThemeCarriesItsCreditForward() {
        val original = assertIs<ActiveProfileThemeEvent>(sample())
        val carried = theme(*assertNotNull(ActiveProfileThemeEvent.adopt(original, createdAt = 1L)).tags)
        assertEquals(definitionAddress, carried.adoptedFrom()?.toTag())
        assertEquals(creator, carried.creator()?.pubKey)

        // A theme with no credit credits its wearer.
        val uncredited = theme(arrayOf("c", "#000000", "background"), arrayOf("c", "#ffffff", "text"), arrayOf("c", "#ff0000", "primary"))
        val copied = theme(*assertNotNull(ActiveProfileThemeEvent.adopt(uncredited, createdAt = 1L)).tags)
        assertNull(copied.adoptedFrom())
        assertEquals(author, copied.creator()?.pubKey)
    }
}
