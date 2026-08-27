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
package com.vitorpamplona.amethyst.ui.actions.mediaServers

import com.vitorpamplona.amethyst.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.service.uploads.forServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerTypeCompressionTest {
    @Test
    fun originlessDoesNotUseClientMediaCompression() {
        // Originless stores ipfs://CID of the pinned bytes. We don't show Media
        // Quality / transcode on the client; compaction is opt-in POST /media.
        assertFalse(ServerType.Originless.usesClientMediaCompression)
        assertTrue(ServerType.NIP96.usesClientMediaCompression)
        assertTrue(ServerType.Blossom.usesClientMediaCompression)
        assertTrue(ServerType.NIP95.usesClientMediaCompression)
    }

    @Test
    fun originlessForcesUncompressedRegardlessOfRequestedQuality() {
        assertEquals(
            CompressorQuality.UNCOMPRESSED,
            CompressorQuality.LOW.forServer(ServerType.Originless),
        )
        assertEquals(
            CompressorQuality.UNCOMPRESSED,
            CompressorQuality.MEDIUM.forServer(ServerType.Originless),
        )
        assertEquals(
            CompressorQuality.UNCOMPRESSED,
            CompressorQuality.HIGH.forServer(ServerType.Originless),
        )
    }

    @Test
    fun otherServersKeepRequestedQuality() {
        assertEquals(CompressorQuality.LOW, CompressorQuality.LOW.forServer(ServerType.NIP96))
        assertEquals(CompressorQuality.MEDIUM, CompressorQuality.MEDIUM.forServer(ServerType.Blossom))
        assertEquals(CompressorQuality.HIGH, CompressorQuality.HIGH.forServer(ServerType.NIP95))
        assertEquals(
            CompressorQuality.UNCOMPRESSED,
            CompressorQuality.UNCOMPRESSED.forServer(ServerType.NIP96),
        )
    }
}
