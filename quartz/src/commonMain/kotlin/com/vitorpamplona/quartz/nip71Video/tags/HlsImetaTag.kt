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
package com.vitorpamplona.quartz.nip71Video.tags

/**
 * Non-standard `hls` property inside a NIP-71 `imeta` tag: the adaptive HLS manifest for the
 * same footage the entry's `url` serves progressively.
 *
 * NIP-71 itself expects a ladder to be published as separate `imeta` entries, one of which
 * carries an HLS media type. divine.video instead keeps one `imeta` per video and hangs the
 * manifest off this key (see `divine-web/src/lib/videoParser.ts`), so a client that only reads
 * `url` is pinned to the progressive rendition and never adapts. Quartz reads both shapes;
 * `selectVideoTrack()` turns an `hls` property into a playable variant of its own.
 */
class HlsImetaTag {
    companion object {
        const val TAG_NAME = "hls"

        /** The media type an `hls` url is served with; NIP-71's own ladder example uses it too. */
        const val MIME_TYPE = "application/vnd.apple.mpegurl"
    }
}
