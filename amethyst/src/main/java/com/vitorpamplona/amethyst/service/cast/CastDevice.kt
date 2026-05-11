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
package com.vitorpamplona.amethyst.service.cast

import androidx.compose.runtime.Immutable

@Immutable
data class CastDevice(
    val id: String,
    val name: String,
)

@Immutable
data class CastRequest(
    val url: String,
    val mimeType: String? = null,
    val title: String? = null,
    val artworkUri: String? = null,
)

// Critical for HLS: sending an `.m3u8` URL with `video/mp4` makes the default
// Cast receiver try to demux a playlist as MP4 and crash, wiping the TV's Cast
// service from the network in the process. Strip query/fragment first so
// signed URLs like `…/stream.m3u8?token=…` still match.
fun CastRequest.effectiveMimeType(): String {
    mimeType?.takeIf { it.isNotBlank() }?.let { return it }
    val path = url.substringBefore('?').substringBefore('#')
    return when {
        path.endsWith(".m3u8", ignoreCase = true) -> "application/vnd.apple.mpegurl"
        path.endsWith(".mpd", ignoreCase = true) -> "application/dash+xml"
        path.endsWith(".webm", ignoreCase = true) -> "video/webm"
        path.endsWith(".mkv", ignoreCase = true) -> "video/x-matroska"
        else -> "video/mp4"
    }
}

sealed class CastSessionState {
    object Idle : CastSessionState()

    data class Connecting(
        val device: CastDevice,
    ) : CastSessionState()

    data class Casting(
        val device: CastDevice,
        val request: CastRequest,
    ) : CastSessionState()

    data class Error(
        val device: CastDevice?,
        val message: String,
    ) : CastSessionState()
}
