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
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import kotlinx.serialization.Serializable

/**
 * The Podcasting-2.0 chapters document referenced by an episode's `chapters` tag — a JSON file
 * (per the podcast-namespace `chapters.json` spec) hosted off-event. Episodes carry only the URL;
 * a client fetches and parses it into this model to render a tappable chapter list.
 */
@Immutable
@Serializable
class PodcastChapters(
    val version: String? = null,
    val chapters: List<PodcastChapter> = emptyList(),
) {
    companion object {
        /** Lenient parse of a chapters.json body; returns null on malformed input. */
        fun parse(json: String): PodcastChapters? = runCatching { JsonMapper.fromJson<PodcastChapters>(json) }.getOrNull()
    }
}

/** One chapter marker. [startTime] is in seconds (may be fractional per the spec). */
@Immutable
@Serializable
class PodcastChapter(
    val startTime: Double = 0.0,
    val title: String? = null,
    /** Chapter artwork URL (the spec field is `img`). */
    val img: String? = null,
    /** A related link for the chapter. */
    val url: String? = null,
    /** Whether the chapter should appear in a table of contents; absent means yes. */
    val toc: Boolean? = null,
) {
    /** Whole-second start used for seeking/labeling. */
    fun startSeconds(): Long = startTime.toLong()
}
