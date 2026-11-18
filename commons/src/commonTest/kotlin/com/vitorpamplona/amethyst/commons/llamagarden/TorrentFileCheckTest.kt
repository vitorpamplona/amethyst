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
package com.vitorpamplona.amethyst.commons.llamagarden

import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.llamagarden.torrent.ModelTorrentEvent
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.utils.EventFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TorrentFileCheckTest {
    private val torrent = "d8:announce...e".encodeToByteArray()
    private val tampered = "d8:announce-evil...e".encodeToByteArray()
    private val hash = sha256(torrent).toHexKey()
    private val infoHash = "17616834d44b4e2d1b35494d0b80ee61da3cea79"

    private fun EventTemplate<ModelTorrentEvent>.parse(): ModelTorrentEvent = EventFactory.create("11".repeat(32), "22".repeat(32), createdAt, kind, tags, content, "00".repeat(64))

    private fun listing(
        sha256: String?,
        vararg urls: String,
    ) = ModelTorrentEvent
        .build(infoHash, "model", "magnet:?xt=urn:btih:$infoHash", 1L, sha256, urls.toList())
        .parse()

    @Test
    fun skipsATamperedMirrorAndUsesTheOneThatMatches() =
        runTest {
            val event = listing(hash, "https://bad.example/a.torrent", "https://good.example/a.torrent")
            val served = mapOf("https://bad.example/a.torrent" to tampered, "https://good.example/a.torrent" to torrent)

            val check = TorrentFileCheck.check(event) { served[it] }

            assertEquals(TorrentFileCheck.Verified("https://good.example/a.torrent"), check)
            val link = TorrentFileCheck.linkToOpen(event, check)!!
            assertTrue(link.endsWith("&xs=https%3A%2F%2Fgood.example%2Fa.torrent"), link)
        }

    @Test
    fun neverPutsAnUnverifiedMirrorInTheMagnet() =
        runTest {
            val event = listing(hash, "https://bad.example/a.torrent", "https://down.example/a.torrent")
            val check = TorrentFileCheck.check(event) { if (it.contains("down")) throw IllegalStateException("offline") else tampered }

            assertEquals(TorrentFileCheck.Mismatch, check)
            assertEquals("magnet:?xt=urn:btih:$infoHash", TorrentFileCheck.linkToOpen(event, check))
        }

    @Test
    fun aListingWithoutAHashIsNotChecked() =
        runTest {
            val event = listing(null, "https://any.example/a.torrent")
            val check = TorrentFileCheck.check(event) { error("must not download") }
            assertEquals(TorrentFileCheck.Unchecked("https://any.example/a.torrent"), check)
            assertEquals("magnet:?xt=urn:btih:$infoHash", TorrentFileCheck.linkToOpen(event, check))
        }

    @Test
    fun aSubmissionOpensItsTorrentFile() =
        runTest {
            val event =
                ModelTorrentEvent
                    .buildSubmission(ModelTorrentEvent.userSubmissionDTag("22".repeat(32), hash), "https://nostr.download/x.torrent", "model")
                    .let { EventFactory.create<ModelTorrentEvent>("11".repeat(32), "22".repeat(32), it.createdAt, it.kind, it.tags, it.content, "00".repeat(64)) }
            val check = TorrentFileCheck.check(event) { torrent }
            assertEquals(TorrentFileCheck.Unchecked("https://nostr.download/x.torrent"), check)
            assertEquals("https://nostr.download/x.torrent", TorrentFileCheck.linkToOpen(event, check))
        }
}
