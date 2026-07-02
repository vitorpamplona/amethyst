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
package com.vitorpamplona.quartz.nipXXPodcasting20

import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.tags.PersonTag
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.tags.SoundbiteTag
import com.vitorpamplona.quartz.nipXXPodcasting20.metadata.Podcasting20PodcastMetadata
import com.vitorpamplona.quartz.podcasts.PodcastPerson
import com.vitorpamplona.quartz.podcasts.PodcastSoundbite
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PodcastPersonSoundbiteTest {
    @Test
    fun `person tag parses all fields`() {
        val person = PersonTag.parse(arrayOf("person", "Alice", "host", "https://img/a.png", "https://alice.example"))
        assertEquals("Alice", person?.name)
        assertEquals("host", person?.role)
        assertEquals("https://img/a.png", person?.img)
        assertEquals("https://alice.example", person?.href)
    }

    @Test
    fun `person tag with only a name`() {
        val person = PersonTag.parse(arrayOf("person", "Bob"))
        assertEquals("Bob", person?.name)
        assertNull(person?.role)
        assertNull(person?.img)
        assertNull(person?.href)
    }

    @Test
    fun `person tag keeps href in slot 4 when role and img are blank`() {
        val person = PersonTag.parse(arrayOf("person", "Carol", "", "", "https://carol.example"))
        assertEquals("Carol", person?.name)
        assertNull(person?.role)
        assertNull(person?.img)
        assertEquals("https://carol.example", person?.href)
    }

    @Test
    fun `person tag round-trips through assemble`() {
        val original = PodcastPerson(name = "Dave", role = "guest", img = "https://img/d.png")
        val reparsed = PersonTag.parse(PersonTag.assemble(original))
        assertEquals("Dave", reparsed?.name)
        assertEquals("guest", reparsed?.role)
        assertEquals("https://img/d.png", reparsed?.img)
        assertNull(reparsed?.href)
    }

    @Test
    fun `nameless person tag is dropped`() {
        assertNull(PersonTag.parse(arrayOf("person")))
        assertNull(PersonTag.parse(arrayOf("person", "")))
    }

    @Test
    fun `person href resolves an npub to a pubkey`() {
        val npub = "npub1hv7k2s755n697sptva8vkh9jz40lzfzklnwj6ekewfmxp5crwdjs27007y"
        val hex = "bb3d6543d4a4f45f402b674ecb5cb2155ff12456fcdd2d66d9727660d3037365"
        assertEquals(hex, PodcastPerson(name = "Alice", href = npub).nostrPubKey())
        assertEquals(hex, PodcastPerson(name = "Alice", href = "nostr:$npub").nostrPubKey())
    }

    @Test
    fun `person href that is a plain web link has no pubkey`() {
        assertNull(PodcastPerson(name = "Alice", href = "https://alice.example").nostrPubKey())
        assertNull(PodcastPerson(name = "Alice", href = null).nostrPubKey())
    }

    @Test
    fun `soundbite tag parses times and optional title`() {
        val soundbite = SoundbiteTag.parse(arrayOf("soundbite", "73.5", "60.0", "Best moment"))
        assertEquals(73.5, soundbite?.startTimeSeconds)
        assertEquals(60.0, soundbite?.durationSeconds)
        assertEquals("Best moment", soundbite?.title)
        assertEquals(73500L, soundbite?.startMillis())
    }

    @Test
    fun `soundbite tag without title`() {
        val soundbite = SoundbiteTag.parse(arrayOf("soundbite", "0", "30"))
        assertEquals(0.0, soundbite?.startTimeSeconds)
        assertEquals(30.0, soundbite?.durationSeconds)
        assertNull(soundbite?.title)
    }

    @Test
    fun `soundbite with bad or non-positive numbers is dropped`() {
        assertNull(SoundbiteTag.parse(arrayOf("soundbite", "abc", "60")))
        assertNull(SoundbiteTag.parse(arrayOf("soundbite", "10", "0")))
        assertNull(SoundbiteTag.parse(arrayOf("soundbite", "10")))
    }

    @Test
    fun `soundbite round-trips through assemble`() {
        val reparsed = SoundbiteTag.parse(SoundbiteTag.assemble(PodcastSoundbite(12.0, 45.0, "Clip")))
        assertEquals(12.0, reparsed?.startTimeSeconds)
        assertEquals(45.0, reparsed?.durationSeconds)
        assertEquals("Clip", reparsed?.title)
    }

    @Test
    fun `show metadata JSON parses a persons array`() {
        val json =
            """{"title":"My Show","persons":[{"name":"Alice","role":"host","img":"https://img/a.png"},{"name":"Bob","role":"guest"}]}"""
        val event = AppSpecificDataEvent("id", "pk", 0, arrayOf(arrayOf("d", "podcast-metadata")), json, "sig")
        val show = Podcasting20PodcastMetadata.parse(event)
        val persons = show?.showPersons().orEmpty()
        assertEquals(2, persons.size)
        assertEquals("Alice", persons[0].name)
        assertEquals("host", persons[0].role)
        assertEquals("https://img/a.png", persons[0].img)
        assertEquals("Bob", persons[1].name)
        assertEquals("guest", persons[1].role)
    }

    @Test
    fun `show metadata without persons is empty rather than a crash`() {
        val event = AppSpecificDataEvent("id", "pk", 0, arrayOf(arrayOf("d", "podcast-metadata")), """{"title":"Bare"}""", "sig")
        val show = Podcasting20PodcastMetadata.parse(event)
        assertTrue(show?.showPersons().orEmpty().isEmpty())
    }
}
