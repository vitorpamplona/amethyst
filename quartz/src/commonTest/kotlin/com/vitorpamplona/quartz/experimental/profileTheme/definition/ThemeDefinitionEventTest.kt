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
package com.vitorpamplona.quartz.experimental.profileTheme.definition

import com.vitorpamplona.quartz.experimental.profileTheme.tags.BackgroundMode
import com.vitorpamplona.quartz.experimental.profileTheme.tags.BackgroundTag
import com.vitorpamplona.quartz.experimental.profileTheme.tags.FontRole
import com.vitorpamplona.quartz.experimental.profileTheme.tags.FontTag
import com.vitorpamplona.quartz.experimental.profileTheme.tags.RgbColor
import com.vitorpamplona.quartz.experimental.profileTheme.tags.ThemeColors
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.IndexableFields
import com.vitorpamplona.quartz.nip50Search.SearchFieldExtractor
import com.vitorpamplona.quartz.nip50Search.SearchableKinds
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThemeDefinitionEventTest {
    private val font = "https://cdn.jsdelivr.net/fontsource/fonts/dm-sans:vf@latest/latin-wght-normal.woff2"

    /** A live Ditto 36767 (2026-10-08 census), with the optional `description`. */
    private fun dittoSample(): Event =
        EventFactory.create(
            id = "f39e0c6e4f999acc048851df8194bf4f18bf42bdbde940553df8c32e945832b7",
            pubKey = "9b23fb05d8fd9f196dbb673a638fae9495e8aa566ed25280cef90f886a08848c",
            createdAt = 1791336511L,
            kind = 36767,
            tags =
                arrayOf(
                    arrayOf("d", "omarchy-retro-82"),
                    arrayOf("c", "#081034", "background"),
                    arrayOf("c", "#faa968", "text"),
                    arrayOf("c", "#f6dcac", "primary"),
                    arrayOf("f", "DM Sans", font, "body"),
                    arrayOf("f", "DM Sans", font, "title"),
                    arrayOf("bg", "url https://blossom.ditto.pub/7a609544b62918264b6cfd1f05ae38f9ed9a7922465a4ecc2edbb1a769f887d0.jpeg", "mode cover", "m image/jpeg"),
                    arrayOf("title", "Omarchy Retro 82"),
                    arrayOf("alt", "Custom theme: Omarchy Retro 82"),
                    arrayOf("t", "theme"),
                    arrayOf("description", "Retro 82 colors from Omarchy."),
                    arrayOf("client", "Ditto", "31990:781a1527055f74c1f70230f10384609b34548f8ab6a0a6caa74025827f9fdae5:ditto"),
                    arrayOf("published_at", "1791336511"),
                ),
            content = "",
            sig = "b671468263479c9b755414da3587db96ad4238a3e62749bb25e6077771d6e0a39cd0ea7d6dc6088c52a88855a21def7eeb74dc94689e5782dfd4b512ac7ce646",
        )

    private val armadaCreator = "41a77f532038bef79baaef941b9e182d99b75c1912d401cfc7cb538bf044c541"

    /** A live Armada 36767 (2026-10-08 census): copied from another user's definition, with `a`/`p` credit. */
    private fun armadaSample(): Event =
        EventFactory.create(
            id = "a02dd6f5fe3cf764ce1a728fd5a0b55113aadcb9e26ea3dd50f939927b0bbbe9",
            pubKey = "9c0f113385c75fcd9ffb434139a184604a31f48fd7593c99e3c9be8ba02e47a0",
            createdAt = 1791349838L,
            kind = 36767,
            tags =
                arrayOf(
                    arrayOf("d", "dark-sky-5aeex3"),
                    arrayOf("title", "Dark Sky"),
                    arrayOf("c", "#111111", "background"),
                    arrayOf("c", "#c0bcbb", "text"),
                    arrayOf("c", "#6060cc", "primary"),
                    arrayOf("bg", "url https://blossom.ditto.pub/4f28c4fd2225f603f4429a340a709589ae0256d0fbb081201969df8d48180eca.png", "mode cover", "m image/png", "dim 1080x1098"),
                    arrayOf("alt", "Custom theme: Dark Sky"),
                    arrayOf("t", "theme"),
                    arrayOf("a", "36767:$armadaCreator:dark-sky-3ioeg7"),
                    arrayOf("p", armadaCreator),
                    arrayOf("client", "Armada"),
                    arrayOf("published_at", "1791349838"),
                ),
            content = "",
            sig = "9a531a06521399e7b6bd8bec1338caecfa130b1b52d0ff6178c2034c0fb43ed66305ea128a3fdbabcef7d1d425f83fde6c1425557eb963fbbd77d0b98f73689f",
        )

    private fun definition(vararg tags: Array<String>) = ThemeDefinitionEvent("00".repeat(32), "aa".repeat(32), 1L, arrayOf(*tags), "", "00".repeat(64))

    @Test
    fun factoryDispatch() {
        assertIs<ThemeDefinitionEvent>(dittoSample())
        assertTrue(EventFactory.isKnownKind(ThemeDefinitionEvent.KIND))
        assertTrue(ThemeDefinitionEvent.KIND in SearchableKinds.ALL)
    }

    @Test
    fun readsTheDittoSample() {
        val event = assertIs<ThemeDefinitionEvent>(dittoSample())
        assertEquals("omarchy-retro-82", event.dTag())
        assertEquals("Omarchy Retro 82", event.title())
        assertEquals("Retro 82 colors from Omarchy.", event.description())
        assertEquals(ThemeColors(RgbColor(0x081034), RgbColor(0xfaa968), RgbColor(0xf6dcac)), event.colors())
        assertEquals(FontTag("DM Sans", font, FontRole.BODY), event.bodyFont())
        assertEquals(FontTag("DM Sans", font, FontRole.TITLE), event.titleFont())
        assertEquals(BackgroundMode.COVER, event.background()?.mode)
        assertNull(event.adoptedFrom())
        assertEquals(emptyList(), event.linkedPubKeys())
        assertEquals(emptyList(), event.linkedAddressIds())
    }

    @Test
    fun readsArmadaCredit() {
        val event = assertIs<ThemeDefinitionEvent>(armadaSample())
        assertEquals("Dark Sky", event.title())
        assertNull(event.description())
        assertEquals("1080x1098", event.background()?.dimension.toString())
        assertEquals(listOf("36767:$armadaCreator:dark-sky-3ioeg7"), event.linkedAddressIds())
        assertEquals(listOf(armadaCreator), event.linkedPubKeys())
        assertEquals(emptyList(), event.pubKeyHints())
        assertEquals(emptyList(), event.addressHints())
    }

    @Test
    fun toleratesMalformedTags() {
        val event =
            definition(
                arrayOf("d", "x"),
                arrayOf("title", ""),
                arrayOf("c", "#12", "background"),
                arrayOf("f", "Inter", "u", "nope"),
                arrayOf("bg"),
                arrayOf("a", "36767:bad:x"),
                arrayOf("p", "bad"),
            )
        assertNull(event.title())
        assertNull(event.colors())
        assertEquals(emptyList(), event.fonts())
        assertNull(event.background())
        assertEquals(emptyList(), event.linkedAddressIds())
        assertEquals(emptyList(), event.linkedPubKeys())
        assertEquals("", event.indexableContent())
    }

    @Test
    fun indexesTitleAndDescriptionOnly() {
        val event = assertIs<ThemeDefinitionEvent>(dittoSample())
        assertEquals("Omarchy Retro 82\nRetro 82 colors from Omarchy.", event.indexableContent())

        val fields = mutableListOf<String?>()
        event.forEachIndexableField(
            IndexableFieldVisitor {
                fields.add(it)
                true
            },
        )
        assertEquals(event.indexableContent(), fields.filterNotNull().joinToString(event.indexableSeparator()))

        // Without a description the visitor still rejoins to the same string.
        val armada = assertIs<ThemeDefinitionEvent>(armadaSample())
        val armadaFields = mutableListOf<String?>()
        armada.forEachIndexableField(
            IndexableFieldVisitor {
                armadaFields.add(it)
                true
            },
        )
        assertEquals("Dark Sky", armada.indexableContent())
        assertEquals(armada.indexableContent(), armadaFields.filterNotNull().joinToString(armada.indexableSeparator()))

        val tiers = assertIs<IndexableFields.Tiered>(SearchFieldExtractor.extract(event))
        assertEquals(listOf("Omarchy Retro 82"), tiers.primary)
        assertEquals(listOf("Retro 82 colors from Omarchy."), tiers.secondary)
        assertNull(tiers.text)
    }

    @Test
    fun buildRoundTrip() {
        val colors = ThemeColors(RgbColor(0x1a1a2e), RgbColor(0xe0e0e0), RgbColor(0x6c3ce0))
        val fonts = listOf(FontTag("Inter", "https://example.com/inter.woff2", FontRole.BODY))
        val bg = BackgroundTag("https://example.com/bg.jpg", BackgroundMode.COVER, "image/jpeg")
        val template = ThemeDefinitionEvent.build("mk-dark-theme", "MK Dark Theme", colors, fonts, bg, "Dark and purple", createdAt = 1L)

        assertEquals(ThemeDefinitionEvent.KIND, template.kind)
        assertEquals("", template.content)
        val event = definition(*template.tags)
        assertEquals("mk-dark-theme", event.dTag())
        assertEquals("MK Dark Theme", event.title())
        assertEquals("Dark and purple", event.description())
        assertEquals(colors, event.colors())
        assertEquals(fonts, event.fonts())
        assertContentEquals(bg.toTagArray(), assertNotNull(event.background()).toTagArray())
        assertTrue(template.tags.any { it[0] == "t" && it[1] == ThemeDefinitionEvent.THEME_HASHTAG })
    }
}
