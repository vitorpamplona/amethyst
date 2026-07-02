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
package com.vitorpamplona.quartz.podcasts

import androidx.compose.runtime.Immutable

/**
 * A Podcasting-2.0 `podcast:soundbite` — a highlight clip of an episode, defined as a
 * [startTimeSeconds] offset into the episode audio and a [durationSeconds] length, with an optional
 * [title]. Players surface these as tappable "jump to the good part" chips.
 *
 * Carried as `["soundbite", "<startTime>", "<duration>", "<optional title>"]` tags on the episode
 * event; times are in seconds and may be fractional.
 */
@Immutable
class PodcastSoundbite(
    val startTimeSeconds: Double,
    val durationSeconds: Double,
    val title: String? = null,
) {
    /** Start offset in whole milliseconds, ready for a media player `seekTo`. */
    fun startMillis(): Long = (startTimeSeconds * 1000).toLong()
}
