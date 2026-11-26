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
package com.vitorpamplona.amethyst.commons.video.hls

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs on the JVM and the iOS simulator, so both `resolveHttpUrl` actuals (java.net.URI and NSURL)
 * resolve the playlists' relative URIs the same way.
 */
class HlsPlaylistRewriterTest {
    private val relay: (String) -> String = { "R[$it]" }

    private fun rewrite(
        playlist: String,
        base: String,
    ) = HlsPlaylistRewriter.rewrite(playlist, base, relay)

    @Test
    fun masterPlaylistVariantsAndRenditionsResolveAgainstThePlaylist() {
        val master =
            """
            #EXTM3U
            #EXT-X-VERSION:6
            #EXT-X-INDEPENDENT-SEGMENTS
            #EXT-X-SESSION-KEY:METHOD=AES-128,URI="keys/session.key"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aac",NAME="English",DEFAULT=YES,URI="audio/en/index.m3u8"
            #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="English",URI="/subs/en.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.4d401e,mp4a.40.2",AUDIO="aac",SUBTITLES="subs"
            low/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2400000,RESOLUTION=1280x720
            ../high/index.m3u8?token=abc
            #EXT-X-STREAM-INF:BANDWIDTH=5000000
            https://other.example/hd/index.m3u8
            #EXT-X-I-FRAME-STREAM-INF:BANDWIDTH=90000,URI="low/iframes.m3u8"
            """.trimIndent()

        val expected =
            """
            #EXTM3U
            #EXT-X-VERSION:6
            #EXT-X-INDEPENDENT-SEGMENTS
            #EXT-X-SESSION-KEY:METHOD=AES-128,URI="R[http://cdn.example/live/show/keys/session.key]"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aac",NAME="English",DEFAULT=YES,URI="R[http://cdn.example/live/show/audio/en/index.m3u8]"
            #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="English",URI="R[http://cdn.example/subs/en.m3u8]"
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.4d401e,mp4a.40.2",AUDIO="aac",SUBTITLES="subs"
            R[http://cdn.example/live/show/low/index.m3u8]
            #EXT-X-STREAM-INF:BANDWIDTH=2400000,RESOLUTION=1280x720
            R[http://cdn.example/live/high/index.m3u8?token=abc]
            #EXT-X-STREAM-INF:BANDWIDTH=5000000
            R[https://other.example/hd/index.m3u8]
            #EXT-X-I-FRAME-STREAM-INF:BANDWIDTH=90000,URI="R[http://cdn.example/live/show/low/iframes.m3u8]"
            """.trimIndent()

        assertEquals(expected, rewrite(master, "http://cdn.example/live/show/master.m3u8?sig=1"))
    }

    @Test
    fun mediaPlaylistSegmentsKeysMapsAndByteRanges() {
        val media =
            """
            #EXTM3U
            #EXT-X-TARGETDURATION:4
            #EXT-X-MEDIA-SEQUENCE:120
            #EXT-X-KEY:METHOD=AES-128,URI="../keys/k1.bin",IV=0x00000000000000000000000000000001
            #EXT-X-MAP:URI="init.mp4",BYTERANGE="720@0"
            #EXTINF:4.000,
            #EXT-X-BYTERANGE:100000@720
            main.mp4
            # a comment that names seg.ts stays as it is
            #EXTINF:4.000,title
            seg121.ts?token=a%20b
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:4.000,
            //edge.example/seg122.ts
            #EXTINF:4.000,
            /abs/seg123.ts
            #EXT-X-ENDLIST
            """.trimIndent()

        val expected =
            """
            #EXTM3U
            #EXT-X-TARGETDURATION:4
            #EXT-X-MEDIA-SEQUENCE:120
            #EXT-X-KEY:METHOD=AES-128,URI="R[https://cdn.example/vod/keys/k1.bin]",IV=0x00000000000000000000000000000001
            #EXT-X-MAP:URI="R[https://cdn.example/vod/720p/init.mp4]",BYTERANGE="720@0"
            #EXTINF:4.000,
            #EXT-X-BYTERANGE:100000@720
            R[https://cdn.example/vod/720p/main.mp4]
            # a comment that names seg.ts stays as it is
            #EXTINF:4.000,title
            R[https://cdn.example/vod/720p/seg121.ts?token=a%20b]
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:4.000,
            R[https://edge.example/seg122.ts]
            #EXTINF:4.000,
            R[https://cdn.example/abs/seg123.ts]
            #EXT-X-ENDLIST
            """.trimIndent()

        assertEquals(expected, rewrite(media, "https://cdn.example/vod/720p/index.m3u8"))
    }

    @Test
    fun lowLatencyPartsHintsAndRenditionReports() {
        val live =
            """
            #EXTM3U
            #EXT-X-SERVER-CONTROL:CAN-BLOCK-RELOAD=YES,PART-HOLD-BACK=1.0
            #EXT-X-PART:DURATION=0.5,URI="part500.0.mp4",INDEPENDENT=YES
            #EXT-X-PRELOAD-HINT:TYPE=PART,URI="part500.1.mp4"
            #EXT-X-RENDITION-REPORT:URI="../low/index.m3u8",LAST-MSN=500,LAST-PART=0
            """.trimIndent()
        val out = rewrite(live, "http://live.example/stream/high/index.m3u8") ?: error("refused")
        assertTrue("URI=\"R[http://live.example/stream/high/part500.0.mp4]\",INDEPENDENT=YES" in out, out)
        assertTrue("URI=\"R[http://live.example/stream/high/part500.1.mp4]\"" in out, out)
        assertTrue("URI=\"R[http://live.example/stream/low/index.m3u8]\",LAST-MSN=500" in out, out)
    }

    @Test
    fun aRelativeUriResolvesAgainstARedirectTargetNotTheFirstUrl() {
        val out = rewrite("#EXTM3U\n#EXTINF:2,\nseg.ts", "http://moved.example/new/place/index.m3u8")
        assertEquals("#EXTM3U\n#EXTINF:2,\nR[http://moved.example/new/place/seg.ts]", out)
    }

    @Test
    fun keepsLineEndingsAndDropsAByteOrderMark() {
        val out = rewrite("\uFEFF#EXTM3U\r\n#EXTINF:2,\r\nseg.ts\r\n", "http://a.example/x/index.m3u8")
        assertEquals("#EXTM3U\r\n#EXTINF:2,\r\nR[http://a.example/x/seg.ts]\r\n", out)
    }

    @Test
    fun dataUrisStayInline() {
        val playlist = "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"data:text/plain;base64,AAAA\"\n#EXTINF:2,\nseg.ts"
        val out = rewrite(playlist, "http://a.example/index.m3u8")
        assertEquals("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"data:text/plain;base64,AAAA\"\n#EXTINF:2,\nR[http://a.example/seg.ts]", out)
    }

    @Test
    fun aUriTheRelayCannotCarryRefusesTheWholePlaylist() {
        // FairPlay keys: the player would fetch them itself.
        assertNull(rewrite("#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"skd://key-id\"\n#EXTINF:2,\nseg.ts", "http://a.example/index.m3u8"))
        assertNull(rewrite("#EXTM3U\n#EXTINF:2,\nftp://a.example/seg.ts", "http://a.example/index.m3u8"))
    }

    @Test
    fun onlyTheUriAttributeIsRewritten() {
        // A client attribute that merely ends in URI is no file the player fetches.
        val line = "#EXT-X-SESSION-DATA:DATA-ID=\"com.example\",X-HOME-URI=\"home.html\""
        assertEquals("#EXTM3U\n$line", rewrite("#EXTM3U\n$line", "http://a.example/index.m3u8"))
    }

    @Test
    fun tellsPlaylistsApart() {
        assertTrue(HlsPlaylistRewriter.looksLikePlaylist("http://a.example/x", "application/vnd.apple.mpegurl; charset=utf-8"))
        assertTrue(HlsPlaylistRewriter.looksLikePlaylist("http://a.example/x", "audio/x-mpegURL"))
        assertTrue(HlsPlaylistRewriter.looksLikePlaylist("http://a.example/index.m3u8?x=1", "text/plain"))
        assertFalse(HlsPlaylistRewriter.looksLikePlaylist("http://a.example/seg.ts?ref=a.m3u8", "video/mp2t"))

        assertTrue(HlsPlaylistRewriter.startsPlaylist("#EXTM3U\n".encodeToByteArray()))
        assertTrue(HlsPlaylistRewriter.startsPlaylist(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "#EXTM3U".encodeToByteArray()))
        assertFalse(HlsPlaylistRewriter.startsPlaylist(byteArrayOf(0x47, 0x40, 0x11, 0x10)))
        assertFalse(HlsPlaylistRewriter.startsPlaylist("#EXT".encodeToByteArray()))
    }
}
