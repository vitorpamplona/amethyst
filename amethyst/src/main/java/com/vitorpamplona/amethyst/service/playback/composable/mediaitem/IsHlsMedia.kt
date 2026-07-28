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
package com.vitorpamplona.amethyst.service.playback.composable.mediaitem

import androidx.media3.common.MimeTypes

/**
 * Whether a URL plus its imeta mime identifies HLS.
 *
 * This is the caller-side form for code that holds a URL and a mime but no `MediaItem` yet — UI that
 * has a [MediaItemData] or a `MediaUrlContent`. Once a `MediaItem` exists,
 * `isHlsMediaItem` is the equivalent, and both must answer the same way: the UI decides what to
 * render, the factory decides how to load it, and a disagreement shows up as a player that streams
 * something the surrounding chrome says is a still image.
 *
 * Defined in terms of [MediaItemCache.toExoPlayerMimeType] rather than re-deriving the rules, so it
 * cannot drift from the mime that actually reaches ExoPlayer. That normalizer prefers an explicit
 * mime (mapping the four HLS aliases onto [MimeTypes.APPLICATION_M3U8]) and otherwise falls back to a
 * **path-anchored** `.m3u8` test.
 *
 * Both halves matter. A BUD-10 blossom playlist is `https://host/<sha256>` with no extension at all,
 * so only the mime identifies it; and anchoring to the path stops `video.mp4?ref=a.m3u8` counting as
 * HLS on the strength of its query string.
 *
 * Note this answers *is it HLS*, which callers use as a proxy for *is it live*. The proxy is
 * imprecise in the same way for every HLS URL — an on-demand HLS playlist also answers true — and
 * that imprecision is older than this function. Liveness is only truly knowable from
 * `#EXT-X-ENDLIST` once the playlist is loaded, which is what `HlsLivenessCache` records.
 */
fun isHlsMedia(
    url: String,
    mimeType: String?,
): Boolean = MediaItemCache.toExoPlayerMimeType(mimeType, url) == MimeTypes.APPLICATION_M3U8
