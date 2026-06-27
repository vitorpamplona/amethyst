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
package com.vitorpamplona.quartz.nipXXPodcasting20.episode.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.podcasts.PodcastAudio
import com.vitorpamplona.quartz.utils.ensure

/**
 * Podcasting-2.0 episode video tag: `["video", "<url>", "<optional_media_type>"]`. The same wire
 * shape as the audio tag; an episode MAY ship a video alongside (or instead of) its audio. Parses
 * into the shared [PodcastAudio] media holder so a client can play it through the same path.
 */
class VideoTag {
    companion object {
        const val TAG_NAME = "video"

        fun parse(tag: Array<String>): PodcastAudio? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            val mediaType = tag.getOrNull(2)?.takeIf { it.isNotEmpty() }
            return PodcastAudio(tag[1], mediaType)
        }

        fun assemble(
            url: String,
            mediaType: String? = null,
        ): Array<String> =
            if (mediaType.isNullOrEmpty()) {
                arrayOf(TAG_NAME, url)
            } else {
                arrayOf(TAG_NAME, url, mediaType)
            }

        fun assemble(video: PodcastAudio) = assemble(video.url, video.mediaType)
    }
}
