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
package com.vitorpamplona.quartz.nipF4Podcasts.episode

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nipF4Podcasts.episode.tags.AudioTag
import com.vitorpamplona.quartz.nipF4Podcasts.episode.tags.DescriptionTag
import com.vitorpamplona.quartz.nipF4Podcasts.episode.tags.ImageTag
import com.vitorpamplona.quartz.nipF4Podcasts.episode.tags.TitleTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-F4 podcast episode (`kind:54`), regular event authored by the podcast pubkey.
 *
 * `content` is the markdown-formatted episode notes. Title, image, short description,
 * and one or more audio URLs live in tags so clients can render listings without
 * parsing markdown.
 */
@Immutable
class PodcastEpisodeEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun image() = tags.firstNotNullOfOrNull(ImageTag::parse)

    fun description() = tags.firstNotNullOfOrNull(DescriptionTag::parse)

    fun audios() = tags.mapNotNull(AudioTag::parse)

    companion object {
        const val KIND = 54
        const val ALT_DESCRIPTION_PREFIX = "Podcast episode"

        fun build(
            title: String,
            description: String,
            audios: List<AudioTag>,
            markdownContent: String = "",
            image: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<PodcastEpisodeEvent>.() -> Unit = {},
        ) = eventTemplate<PodcastEpisodeEvent>(KIND, markdownContent, createdAt) {
            alt("$ALT_DESCRIPTION_PREFIX: $title")

            title(title)
            description(description)
            image?.let { image(it) }
            audios.forEach { audio(it) }

            initializer()
        }
    }
}
