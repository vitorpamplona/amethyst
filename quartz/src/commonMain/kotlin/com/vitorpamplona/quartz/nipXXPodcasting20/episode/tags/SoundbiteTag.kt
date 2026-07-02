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
import com.vitorpamplona.quartz.podcasts.PodcastSoundbite
import com.vitorpamplona.quartz.utils.ensure

/**
 * Podcasting-2.0 episode soundbite: `["soundbite", "<startTime>", "<duration>", "<optional title>"]`.
 *
 * `startTime` and `duration` are in seconds (may be fractional). Title is optional. Maps to the
 * shared [PodcastSoundbite] holder. A soundbite with a non-positive duration is dropped as invalid.
 */
class SoundbiteTag {
    companion object {
        const val TAG_NAME = "soundbite"

        fun parse(tag: Array<String>): PodcastSoundbite? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            val start = tag[1].toDoubleOrNull() ?: return null
            val duration = tag[2].toDoubleOrNull() ?: return null
            ensure(start >= 0.0) { return null }
            ensure(duration > 0.0) { return null }
            return PodcastSoundbite(start, duration, tag.getOrNull(3)?.takeIf { it.isNotEmpty() })
        }

        fun assemble(soundbite: PodcastSoundbite): Array<String> {
            val head = arrayOf(TAG_NAME, soundbite.startTimeSeconds.toString(), soundbite.durationSeconds.toString())
            val title = soundbite.title
            return if (title.isNullOrEmpty()) head else head + title
        }
    }
}
