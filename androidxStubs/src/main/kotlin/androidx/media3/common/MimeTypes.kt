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
package androidx.media3.common

/** JVM stand-in for androidx.media3.common.MimeTypes. */
object MimeTypes {
    const val APPLICATION_M3U8 = "application/x-mpegURL"
    const val APPLICATION_MPD = "application/dash+xml"
    const val VIDEO_MP4 = "video/mp4"
    const val VIDEO_H264 = "video/avc"
    const val VIDEO_WEBM = "video/webm"
    const val AUDIO_AAC = "audio/mp4a-latm"
    const val AUDIO_MPEG = "audio/mpeg"
    const val IMAGE_JPEG = "image/jpeg"

    fun isVideo(mimeType: String?) = mimeType?.startsWith("video/") == true

    fun isAudio(mimeType: String?) = mimeType?.startsWith("audio/") == true
}
