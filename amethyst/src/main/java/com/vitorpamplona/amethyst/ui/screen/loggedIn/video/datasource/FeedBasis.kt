/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.video.datasource

import com.vitorpamplona.quartz.experimental.nip95.header.FileStorageHeaderEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoHorizontalEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import com.vitorpamplona.quartz.nip71Video.VideoVerticalEvent
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent

val SUPPORTED_VIDEO_FEED_MIME_TYPES = listOf("image/jpeg", "image/gif", "image/png", "image/webp", "video/mp4", "video/mpeg", "video/webm", "audio/aac", "audio/mpeg", "audio/webm", "audio/wav", "image/avif")
val SUPPORTED_VIDEO_FEED_MIME_TYPES_SET = SUPPORTED_VIDEO_FEED_MIME_TYPES.toSet()

val PictureAndVideoKinds =
    listOf(
        PictureEvent.KIND,
        VideoHorizontalEvent.KIND,
        VideoVerticalEvent.KIND,
        VideoNormalEvent.KIND,
        VideoShortEvent.KIND,
    )

val PictureAndVideoKTags =
    listOf(
        PictureEvent.KIND.toString(),
        VideoHorizontalEvent.KIND.toString(),
        VideoVerticalEvent.KIND.toString(),
        VideoNormalEvent.KIND.toString(),
        VideoShortEvent.KIND.toString(),
    )
val PictureAndVideoLegacyKinds = listOf(FileHeaderEvent.KIND, FileStorageHeaderEvent.KIND)
val PictureAndVideoLegacyKTags = listOf(FileHeaderEvent.KIND.toString(), FileStorageHeaderEvent.KIND.toString())
val LegacyMimeTypes = SUPPORTED_VIDEO_FEED_MIME_TYPES
val LegacyMimeTypeMap = mapOf("m" to LegacyMimeTypes)
