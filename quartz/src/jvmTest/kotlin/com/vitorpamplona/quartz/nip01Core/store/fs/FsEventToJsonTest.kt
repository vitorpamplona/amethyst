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
package com.vitorpamplona.quartz.nip01Core.store.fs

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FsEventToJsonTest {
    private val signer = NostrSignerSync()
    private lateinit var root: Path

    @BeforeTest
    fun setup() {
        Secp256k1Instance
        root = Files.createTempDirectory("fs-fmt-")
    }

    @AfterTest
    fun tearDown() {
        if (root.exists()) {
            Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach { p -> Files.deleteIfExists(p) } }
        }
    }

    @Test
    fun `default formatter writes compact JSON one line`() {
        val store = FsEventStore(root)
        try {
            val n =
                signer.sign<TextNoteEvent>(
                    TextNoteEvent.build("hello", createdAt = 100),
                )
            store.insert(n)
            val canonical =
                root
                    .resolve("events")
                    .resolve(n.id.substring(0, 2))
                    .resolve(n.id.substring(2, 4))
                    .resolve("${n.id}.json")
            val raw = canonical.readText()
            assertEquals(raw.trim(), raw, "compact form has no trailing whitespace")
            assertTrue(!raw.contains('\n'), "compact form is single-line")
        } finally {
            store.close()
        }
    }

    @Test
    fun `pretty formatter writes multi-line indented JSON and round-trips`() {
        val store =
            FsEventStore(
                root,
                eventToJson = JacksonMapper::toJsonPretty,
            )
        try {
            val n =
                signer.sign<TextNoteEvent>(
                    TextNoteEvent.build("hello", createdAt = 100),
                )
            store.insert(n)
            val canonical =
                root
                    .resolve("events")
                    .resolve(n.id.substring(0, 2))
                    .resolve(n.id.substring(2, 4))
                    .resolve("${n.id}.json")
            val raw = canonical.readText()
            assertTrue(raw.contains('\n'), "pretty form is multi-line")
            assertTrue(raw.contains("\"id\""), "field labels survive pretty print")

            // Round-trip: parsing pretty output back must produce the same event.
            val reparsed = Event.fromJson(raw)
            assertEquals(n.id, reparsed.id)
            assertEquals(n.content, reparsed.content)
            assertEquals(n.sig, reparsed.sig)

            // And the store can read it back through its own API.
            val got = store.query<TextNoteEvent>(Filter(ids = listOf(n.id)))
            assertEquals(1, got.size)
            assertEquals(n.id, got[0].id)
        } finally {
            store.close()
        }
    }
}
