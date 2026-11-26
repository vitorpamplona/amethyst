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

class VideoRouteTest {
    @Test
    fun withoutTorEverythingOffTheCachePlaysAsBefore() {
        assertEquals(VideoRoute.PLAIN, VideoRoute.of("https://a.example/index.m3u8", viaCache = false, mustUseTor = false))
        assertEquals(VideoRoute.PLAIN, VideoRoute.of("http://a.example/live", viaCache = false, mustUseTor = false))
        assertEquals(VideoRoute.CACHE, VideoRoute.of("https://a.example/v.mp4", viaCache = true, mustUseTor = false))
        assertEquals(VideoRoute.PLAIN, VideoRoute.of("rtmp://a.example/live", viaCache = false, mustUseTor = false))
    }

    @Test
    fun torRoutedStreamsOffTheCacheGoThroughTheRelay() {
        assertEquals(VideoRoute.TOR_RELAY, VideoRoute.of("https://a.example/index.m3u8", viaCache = false, mustUseTor = true))
        assertEquals(VideoRoute.TOR_RELAY, VideoRoute.of("HTTP://abc.onion/live.m3u8", viaCache = false, mustUseTor = true))
        // The cache already fetches over the video role's client.
        assertEquals(VideoRoute.CACHE, VideoRoute.of("https://a.example/v.mp4", viaCache = true, mustUseTor = true))
    }

    @Test
    fun whatTheRelayCannotCarryIsRefusedAndLocalFilesNeverLeave() {
        assertEquals(VideoRoute.REFUSED, VideoRoute.of("rtmp://a.example/live", viaCache = false, mustUseTor = true))
        assertEquals(VideoRoute.REFUSED, VideoRoute.of("no-scheme", viaCache = false, mustUseTor = true))
        assertEquals(VideoRoute.PLAIN, VideoRoute.of("file:///var/mobile/x.mp4", viaCache = false, mustUseTor = true))
    }
}
