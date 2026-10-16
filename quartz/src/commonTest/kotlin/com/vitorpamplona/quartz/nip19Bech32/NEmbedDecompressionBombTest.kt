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
package com.vitorpamplona.quartz.nip19Bech32

import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.utils.GZip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * `nembed1…` carries a gzip'd event, and the parser runs on ingest for every note that cites one
 * (`BaseNoteEvent.citedNIP19`). Whitespace gzips at ~1000:1, so a note of a few dozen KB used to
 * inflate into tens of MB before anything looked at it.
 */
class NEmbedDecompressionBombTest {
    private val event =
        """{"id":"82c19ee9db7644829b4420f40b72ce2d32e5b7962744b261d0d53e770432dac3",""" +
            """"pubkey":"bb3d6543d4a4f45f402b674ecb5cb2155ff12456fcdd2d66d9727660d3037365",""" +
            """"created_at":1700000000,"kind":1,"tags":[],"content":"hi","sig":"cafebabe"}"""

    // Leading whitespace is valid JSON, so the unbounded decoder DID turn this bomb into an event.
    private fun bomb(inflatedSize: Int): ByteArray = GZip.compress(" ".repeat(inflatedSize) + event)

    @Test
    fun small_embed_still_parses() {
        val parsed = NEmbed.parse(GZip.compress(event))
        assertNotNull(parsed)
        assertEquals("hi", parsed.event.content)
    }

    @Test
    fun rejects_gzip_bomb() {
        assertNull(NEmbed.parse(bomb(16 * 1024 * 1024)))
    }

    @Test
    fun rejects_gzip_bomb_through_the_uri_parser() {
        val uri = "nostr:" + bomb(16 * 1024 * 1024).toNEmbed()
        assertNull(Nip19Parser.uriToRoute(uri))
    }
}
