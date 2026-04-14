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
package com.vitorpamplona.amethyst.service.uploads.hls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HlsPlaylistRewriterTest {
    @Test
    fun rewritesSegmentReferencesInMediaPlaylist() {
        val playlist =
            """
            #EXTM3U
            #EXT-X-VERSION:7
            #EXT-X-TARGETDURATION:4
            #EXT-X-MEDIA-SEQUENCE:0
            #EXTINF:4.000,
            segment_000.m4s
            #EXTINF:4.000,
            segment_001.m4s
            #EXT-X-ENDLIST
            """.trimIndent()

        val urlMap =
            mapOf(
                "segment_000.m4s" to "https://cdn.example.com/abc.m4s",
                "segment_001.m4s" to "https://cdn.example.com/def.m4s",
            )

        val rewritten = HlsPlaylistRewriter.rewrite(playlist, urlMap)

        val expected =
            """
            #EXTM3U
            #EXT-X-VERSION:7
            #EXT-X-TARGETDURATION:4
            #EXT-X-MEDIA-SEQUENCE:0
            #EXTINF:4.000,
            https://cdn.example.com/abc.m4s
            #EXTINF:4.000,
            https://cdn.example.com/def.m4s
            #EXT-X-ENDLIST
            """.trimIndent()

        assertEquals(expected, rewritten)
    }

    @Test
    fun preservesExtInfLinesExactly() {
        val playlist =
            """
            #EXTINF:3.9836,
            segment_000.m4s
            """.trimIndent()

        val rewritten =
            HlsPlaylistRewriter.rewrite(
                playlist,
                mapOf("segment_000.m4s" to "https://cdn/x.m4s"),
            )

        assertEquals(
            "#EXTINF:3.9836,\nhttps://cdn/x.m4s",
            rewritten,
        )
    }

    @Test
    fun rewritesExtXMapUri() {
        val playlist =
            """
            #EXTM3U
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:4.000,
            segment_000.m4s
            """.trimIndent()

        val urlMap =
            mapOf(
                "init.mp4" to "https://cdn/init-abc.mp4",
                "segment_000.m4s" to "https://cdn/seg-def.m4s",
            )

        val rewritten = HlsPlaylistRewriter.rewrite(playlist, urlMap)

        val expected =
            """
            #EXTM3U
            #EXT-X-MAP:URI="https://cdn/init-abc.mp4"
            #EXTINF:4.000,
            https://cdn/seg-def.m4s
            """.trimIndent()

        assertEquals(expected, rewritten)
    }

    @Test
    fun extXMapPreservesAdditionalAttributes() {
        val playlist = """#EXT-X-MAP:URI="init.mp4",BYTERANGE="718@0""""

        val rewritten =
            HlsPlaylistRewriter.rewrite(
                playlist,
                mapOf("init.mp4" to "https://cdn/abc.mp4"),
            )

        assertEquals(
            """#EXT-X-MAP:URI="https://cdn/abc.mp4",BYTERANGE="718@0"""",
            rewritten,
        )
    }

    @Test
    fun rewritesVariantsInMasterPlaylist() {
        val playlist =
            """
            #EXTM3U
            #EXT-X-VERSION:7
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.64001e,mp4a.40.2"
            360p/media.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2400000,RESOLUTION=1280x720,CODECS="avc1.64001f,mp4a.40.2"
            720p/media.m3u8
            """.trimIndent()

        val urlMap =
            mapOf(
                "360p/media.m3u8" to "https://cdn/360.m3u8",
                "720p/media.m3u8" to "https://cdn/720.m3u8",
            )

        val rewritten = HlsPlaylistRewriter.rewrite(playlist, urlMap)

        val expected =
            """
            #EXTM3U
            #EXT-X-VERSION:7
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.64001e,mp4a.40.2"
            https://cdn/360.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2400000,RESOLUTION=1280x720,CODECS="avc1.64001f,mp4a.40.2"
            https://cdn/720.m3u8
            """.trimIndent()

        assertEquals(expected, rewritten)
    }

    @Test
    fun preservesExtXStreamInfLinesExactly() {
        val playlist =
            """
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.64001e,mp4a.40.2"
            360p/media.m3u8
            """.trimIndent()

        val rewritten =
            HlsPlaylistRewriter.rewrite(
                playlist,
                mapOf("360p/media.m3u8" to "https://cdn/360.m3u8"),
            )

        val expected =
            """
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.64001e,mp4a.40.2"
            https://cdn/360.m3u8
            """.trimIndent()

        assertEquals(expected, rewritten)
    }

    @Test
    fun leavesBlankLinesAndCommentsUnchanged() {
        val playlist =
            """
            #EXTM3U
            # this is a comment

            #EXT-X-VERSION:7
            #EXTINF:4.000,
            segment_000.m4s
            """.trimIndent()

        val rewritten =
            HlsPlaylistRewriter.rewrite(
                playlist,
                mapOf("segment_000.m4s" to "https://cdn/x.m4s"),
            )

        val expected =
            """
            #EXTM3U
            # this is a comment

            #EXT-X-VERSION:7
            #EXTINF:4.000,
            https://cdn/x.m4s
            """.trimIndent()

        assertEquals(expected, rewritten)
    }

    @Test
    fun throwsWhenSegmentReferenceIsMissingFromUrlMap() {
        val playlist =
            """
            #EXTINF:4.000,
            segment_000.m4s
            """.trimIndent()

        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                HlsPlaylistRewriter.rewrite(playlist, emptyMap())
            }

        assertEquals("No uploaded URL for playlist reference: segment_000.m4s", ex.message)
    }
}
