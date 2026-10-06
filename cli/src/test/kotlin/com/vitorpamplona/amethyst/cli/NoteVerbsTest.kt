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
package com.vitorpamplona.amethyst.cli

import com.vitorpamplona.amethyst.cli.commands.NoteSupport
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The offline half of the `notes show|thread|reply|quote|react|repost`,
 * `delete` and `notifications` contract: argument validation exits 2 before any
 * account or relay is touched, and every accepted EVENT spelling resolves to the
 * right filter + hints. The relay half lives in `cli/tests/notes/`.
 */
class NoteVerbsTest {
    private val id = "ab47ce8e660e1bcd5a31bc0b6ec31fa20337bc2288c935a03af62f0b66b36c22"
    private val author = "ec33d3644b9010db51edef5e62261c9cb9c8664d1696564f5a8c897ad9e74025"
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!

    @Test
    fun parsesEveryEventSpelling() {
        assertEquals(listOf(id), NoteSupport.parseRef(id).filter.ids)
        assertEquals(listOf(id), NoteSupport.parseRef(id.uppercase()).filter.ids)
        assertEquals(listOf(id), NoteSupport.parseRef(NNote.create(id)).filter.ids)

        val nevent = NoteSupport.parseRef("nostr:" + NEvent.create(id, author, 1, relay))
        assertEquals(listOf(id), nevent.filter.ids)
        assertEquals(author, nevent.author)
        assertEquals(setOf(relay), nevent.hints)

        val naddr = NoteSupport.parseRef(NAddress.create(30023, author, "slug", relay))
        assertEquals(listOf(30023), naddr.filter.kinds)
        assertEquals(listOf(author), naddr.filter.authors)
        assertEquals(mapOf("d" to listOf("slug")), naddr.filter.tags)
    }

    @Test
    fun rejectsNonEventReferences() {
        assertFailsWith<IllegalArgumentException> { NoteSupport.parseRef("nope") }
        assertFailsWith<IllegalArgumentException> { NoteSupport.parseRef("npub1aseaxeztjqgdk50daa0xyfsunjuusejdz6t9vn663jyh4k08gqjsnft2g2") }
    }

    @Test
    fun missingArgumentsExit2() {
        for (argv in listOf(
            arrayOf("notes", "show"),
            arrayOf("notes", "thread"),
            arrayOf("notes", "reply", id),
            arrayOf("notes", "reply", id, "  "),
            arrayOf("notes", "react"),
            arrayOf("notes", "repost"),
            arrayOf("notes", "quote"),
            arrayOf("delete"),
            arrayOf("notes", "show", "not-an-event"),
            arrayOf("notes", "thread", id, "--limit", "0"),
            arrayOf("notifications", "--type", "likes"),
            arrayOf("notes", "feed", "--following", "--hashtag", "nostr"),
        )) {
            val r = amy(*argv)
            assertEquals(2, r.exit, "amy ${argv.joinToString(" ")} → ${r.stderr}")
            assertTrue(r.stderr.contains("bad_args"), r.stderr)
        }
    }

    @Test
    fun notesHelpListsTheNewVerbs() {
        val r = amy("notes", "--help")
        assertEquals(0, r.exit)
        for (verb in listOf("show", "thread", "reply", "quote", "react", "repost", "delete")) {
            assertTrue(r.stderr.contains("notes $verb"), "help should mention notes $verb")
        }
    }
}
