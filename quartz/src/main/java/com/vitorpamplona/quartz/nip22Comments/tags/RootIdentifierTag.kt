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
package com.vitorpamplona.quartz.nip22Comments.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.value
import com.vitorpamplona.quartz.nip46RemoteSigner.getOrNull
import com.vitorpamplona.quartz.nip73ExternalIds.ExternalId
import com.vitorpamplona.quartz.nip73ExternalIds.books.BookId
import com.vitorpamplona.quartz.nip73ExternalIds.location.GeohashId
import com.vitorpamplona.quartz.nip73ExternalIds.movies.MovieId
import com.vitorpamplona.quartz.nip73ExternalIds.papers.PaperId
import com.vitorpamplona.quartz.nip73ExternalIds.podcasts.PodcastEpisodeId
import com.vitorpamplona.quartz.nip73ExternalIds.podcasts.PodcastFeedId
import com.vitorpamplona.quartz.nip73ExternalIds.podcasts.PodcastPublisherId
import com.vitorpamplona.quartz.nip73ExternalIds.topics.HashtagId
import com.vitorpamplona.quartz.nip73ExternalIds.urls.UrlId
import com.vitorpamplona.quartz.utils.TagParsingUtils
import com.vitorpamplona.quartz.utils.arrayOfNotNull

@Immutable
class RootIdentifierTag<T : ExternalId> {
    companion object {
        const val TAG_NAME = "I"

        @JvmStatic
        fun match(tag: Tag) = TagParsingUtils.matchesTag(tag, TAG_NAME)

        fun isTagged(
            tag: Array<String>,
            encodedScope: String,
        ) = TagParsingUtils.isTaggedWith(tag, TAG_NAME, encodedScope)

        fun isTagged(
            tag: Array<String>,
            encodedScope: Set<String>,
        ) = TagParsingUtils.isTaggedWithAny(tag, TAG_NAME, encodedScope)

        fun matchOrNull(
            tag: Array<String>,
            encodedScope: Set<String>,
        ) = if (TagParsingUtils.isTaggedWithAny(tag, TAG_NAME, encodedScope)) {
            tag[1]
        } else {
            null
        }

        fun isTagged(
            tag: Array<String>,
            test: (String) -> Boolean,
        ) = TagParsingUtils.validateBasicTag(tag, TAG_NAME) && test(tag.value())

        fun isTagged(
            tag: Array<String>,
            value: String,
            match: (String, String) -> Boolean,
        ) = TagParsingUtils.validateBasicTag(tag, TAG_NAME) && match(tag.value(), value)

        fun isTagged(
            tag: Array<String>,
            value: Set<String>,
            match: (String, Set<String>) -> Boolean,
        ) = TagParsingUtils.validateBasicTag(tag, TAG_NAME) && match(tag.value(), value)

        @JvmStatic
        fun parse(tag: Tag): String? {
            if (!TagParsingUtils.validateBasicTag(tag, TAG_NAME)) return null
            return tag[1]
        }

        @JvmStatic
        fun parseExternalId(tag: Tag): ExternalId? {
            if (!TagParsingUtils.validateBasicTag(tag, TAG_NAME)) return null

            val value = tag[1]
            val hint = tag.getOrNull(2)

            return BookId.parse(value, hint)
                ?: HashtagId.parse(value, hint)
                ?: GeohashId.parse(value, hint)
                ?: MovieId.parse(value, hint)
                ?: PaperId.parse(value, hint)
                ?: PodcastEpisodeId.parse(value, hint)
                ?: PodcastFeedId.parse(value, hint)
                ?: PodcastPublisherId.parse(value, hint)
                ?: UrlId.parse(value, hint)
        }

        @JvmStatic
        fun assemble(
            identity: String,
            hint: String?,
        ) = arrayOfNotNull(TAG_NAME, identity, hint)

        @JvmStatic
        fun assemble(id: ExternalId): Array<String> = assemble(id.toScope(), id.hint())
    }
}
