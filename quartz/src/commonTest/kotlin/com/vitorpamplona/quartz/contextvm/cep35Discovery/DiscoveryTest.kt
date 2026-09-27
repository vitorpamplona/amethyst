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
package com.vitorpamplona.quartz.contextvm.cep35Discovery

import com.vitorpamplona.quartz.contextvm.cep06Announcements.DiscoverySurface
import com.vitorpamplona.quartz.contextvm.cep06Announcements.ServerAnnouncement
import com.vitorpamplona.quartz.contextvm.cep17RelayList.ServerRelay
import com.vitorpamplona.quartz.contextvm.cep24Reviews.ServerReview
import com.vitorpamplona.quartz.contextvm.core.CvmKinds
import com.vitorpamplona.quartz.contextvm.core.CvmTags
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.core.Tag
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiscoveryTest {
    private val serverPubKey = "a".repeat(64)

    private fun event(
        kind: Kind,
        tags: Array<Tag>,
        content: String = "",
        createdAt: Long = 1_700_000_000L,
        pubKey: String = serverPubKey,
    ) = Event(
        id = "c".repeat(64),
        pubKey = pubKey,
        createdAt = createdAt,
        kind = kind,
        tags = tags,
        content = content,
        sig = "e".repeat(128),
    )

    // --- CEP-6 / CEP-35 discovery surface ---

    @Test
    fun `CVM-6-01 parses the announcement discovery tags`() {
        val surface =
            DiscoverySurface.parse(
                arrayOf(
                    arrayOf("name", "Example Server"),
                    arrayOf("about", "Public MCP provider"),
                    arrayOf("picture", "https://example.com/a.png"),
                    arrayOf("website", "https://example.com"),
                    CvmTags.flag(CvmTags.SUPPORT_ENCRYPTION),
                    CvmTags.flag(CvmTags.SUPPORT_OPEN_STREAM),
                ),
            )
        assertEquals("Example Server", surface.name)
        assertEquals("https://example.com", surface.website)
        assertTrue(surface.supportsEncryption)
        assertTrue(surface.supportsOpenStream)
        assertFalse(surface.supportsOversizedTransfer)
    }

    @Test
    fun `CVM-35-01 preserves unknown discovery tags and excludes routing`() {
        val surface =
            DiscoverySurface.parse(
                arrayOf(
                    arrayOf("p", serverPubKey),
                    arrayOf("e", "b".repeat(64)),
                    arrayOf("some_future_capability", "v2"),
                    CvmTags.flag(CvmTags.SUPPORT_ENCRYPTION),
                ),
            )
        assertEquals(1, surface.unknownTags.size, "only the unrecognised tag is retained")
        assertContentEquals(arrayOf("some_future_capability", "v2"), surface.rawTag("some_future_capability"))
        assertNull(surface.rawTag("p"), "routing tags are not discovery")
    }

    @Test
    fun `CVM-35-02 the first peer message establishes the baseline`() {
        val session = SessionDiscovery()
        assertFalse(session.hasLearned)

        session.observe(arrayOf(CvmTags.flag(CvmTags.SUPPORT_ENCRYPTION), arrayOf("name", "First")))
        assertTrue(session.hasLearned)
        assertEquals("First", session.peer?.name)
        assertTrue(session.peer!!.supportsEncryption)
    }

    @Test
    fun `CVM-35-03 a later message is interpreted locally without mutating the baseline`() {
        val session = SessionDiscovery()
        session.observe(arrayOf(arrayOf("name", "First"), CvmTags.flag(CvmTags.SUPPORT_ENCRYPTION)))

        val local = session.observe(arrayOf(arrayOf("cap", "tool:x", "100", "sats")))
        assertEquals(1, local.unknownTags.size, "the later tags are returned for local use")
        assertEquals("First", session.peer?.name, "the baseline is unchanged")
        assertTrue(session.peer!!.supportsEncryption)
    }

    @Test
    fun `CVM-35-04 a feature CEP can replace the baseline explicitly`() {
        val session = SessionDiscovery()
        session.observe(arrayOf(arrayOf("name", "First")))
        session.replaceBaseline(arrayOf(arrayOf("name", "Renamed")))
        assertEquals("Renamed", session.peer?.name)
    }

    // --- CEP-6 announcements ---

    @Test
    fun `CVM-6-02 parses each announcement kind and rejects others`() {
        CvmKinds.ANNOUNCEMENTS.forEach { kind ->
            assertNotNull(ServerAnnouncement.parseOrNull(event(kind, emptyArray(), """{"tools":[]}""")))
        }
        assertNull(ServerAnnouncement.parseOrNull(event(1, emptyArray())))
    }

    @Test
    fun `CVM-6-03 keeps the newest announcement per kind`() {
        // Replaceable kinds: a stale event from a lagging relay must not
        // overwrite a newer one already held.
        val older = event(CvmKinds.SERVER_ANNOUNCEMENT, arrayOf(arrayOf("name", "old")), createdAt = 100)
        val newer = event(CvmKinds.SERVER_ANNOUNCEMENT, arrayOf(arrayOf("name", "new")), createdAt = 200)

        val latest = ServerAnnouncement.latestPerKind(listOf(newer, older))
        assertEquals("new", latest[CvmKinds.SERVER_ANNOUNCEMENT]?.discovery?.name)
    }

    @Test
    fun `CVM-6-04 leaves the announcement content as text for the caller to decode`() {
        val announcement =
            ServerAnnouncement.parseOrNull(
                event(CvmKinds.TOOLS_LIST, emptyArray(), """{"tools":[{"name":"x"}]}"""),
            )!!
        assertEquals("""{"tools":[{"name":"x"}]}""", announcement.content)
    }

    // --- CEP-17 relay list ---

    @Test
    fun `CVM-17-01 treats an unmarked relay as both read and write`() {
        val relays = ServerRelay.parseAll(event(CvmKinds.RELAY_LIST, arrayOf(arrayOf("r", "wss://a"))))
        assertEquals(listOf(ServerRelay("wss://a", read = true, write = true)), relays)
    }

    @Test
    fun `CVM-17-02 honours read and write markers when present`() {
        val relays =
            ServerRelay.parseAll(
                event(
                    CvmKinds.RELAY_LIST,
                    arrayOf(arrayOf("r", "wss://r", "read"), arrayOf("r", "wss://w", "write")),
                ),
            )
        assertEquals(ServerRelay("wss://r", read = true, write = false), relays[0])
        assertEquals(ServerRelay("wss://w", read = false, write = true), relays[1])
    }

    @Test
    fun `CVM-17-03 only a bidirectional relay can carry a full exchange`() {
        // Kind 25910 is ephemeral, so a response missed on a write-only relay is
        // simply gone -- both halves must share a relay.
        val relays =
            listOf(
                ServerRelay("wss://both"),
                ServerRelay("wss://read", read = true, write = false),
            )
        assertEquals(listOf(ServerRelay("wss://both")), ServerRelay.operational(relays))
    }

    @Test
    fun `CVM-17-04 ignores a blank relay url and a non-relay-list event`() {
        assertTrue(ServerRelay.parseAll(event(CvmKinds.RELAY_LIST, arrayOf(arrayOf("r", "")))).isEmpty())
        assertTrue(ServerRelay.parseAll(event(1, arrayOf(arrayOf("r", "wss://a")))).isEmpty())
    }

    // --- CEP-24 reviews ---

    @Test
    fun `CVM-24-01 a top-level review tags the announcement as both root and parent`() {
        val tags = ServerReview.topLevelTags(serverPubKey, relayHint = "wss://r")
        val names = tags.map { it[0] }
        assertTrue(names.containsAll(listOf("A", "K", "P", "a", "k", "p")))
        assertEquals("11316:$serverPubKey:", tags.first { it[0] == "A" }[1])
        assertEquals("11316", tags.first { it[0] == "k" }[1])
    }

    @Test
    fun `CVM-24-02 a reply keeps the root uppercase but moves the parent to the comment`() {
        val parent = "b".repeat(64)
        val author = "d".repeat(64)
        val tags = ServerReview.replyTags(serverPubKey, parent, author)

        // Root stays on the announcement...
        assertEquals("11316:$serverPubKey:", tags.first { it[0] == "A" }[1])
        assertEquals("11316", tags.first { it[0] == "K" }[1])
        // ...while the lowercase parent is the comment being answered.
        assertEquals(parent, tags.first { it[0] == "e" }[1])
        assertEquals("1111", tags.first { it[0] == "k" }[1])
        assertEquals(author, tags.first { it[0] == "p" }[1])
    }

    @Test
    fun `CVM-24-03 builds the addressable coordinate`() {
        assertEquals("11316:$serverPubKey:", ServerReview.coordinate(serverPubKey))
    }
}
