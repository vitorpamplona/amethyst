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
 * Spec-neutral media reference (a URL plus optional MIME type) for a podcast episode, used by the
 * shared [PodcastEpisode] abstraction so a UI can play episodes regardless of which podcast NIP
 * produced them. Despite the name it covers both audio and video sources.
 *
 * Both NIP-F4 (`kind:54`) and the Podcasting-2.0 draft (`kind:30054`) carry audio in identical
 * `["audio", "<url>", "<optional_media_type>"]` tags; the Podcasting-2.0 draft uses the same shape
 * for its `video` tag. Each event maps its own tag class into this holder.
 */
@Immutable
class PodcastAudio(
    val url: String,
    val mediaType: String? = null,
)
