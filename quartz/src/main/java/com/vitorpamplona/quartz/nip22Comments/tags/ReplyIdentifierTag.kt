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
import com.vitorpamplona.quartz.nip01Core.core.has
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
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

@Immutable
class ReplyIdentifierTag {
    companion object {
        const val TAG_NAME = "i"

        @JvmStatic
        fun match(tag: Tag) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        @JvmStatic
        fun isTagged(
            tag: Array<String>,
            encodedScope: String,
        ) = tag.has(1) && tag[0] == TAG_NAME && tag[1] == encodedScope

        fun isTagged(
            tag: Array<String>,
            encodedScope: Set<String>,
        ) = tag.has(1) && tag[0] == TAG_NAME && tag[1] in encodedScope

        fun matchOrNull(
            tag: Array<String>,
            encodedScope: Set<String>,
        ) = if (tag.has(1) && tag[0] == TAG_NAME && tag[1] in encodedScope) {
            tag[1]
        } else {
            null
        }

        fun isTagged(
            tag: Array<String>,
            test: (String) -> Boolean,
        ) = tag.has(1) && tag[0] == TAG_NAME && test(tag[1])

        fun isTagged(
            tag: Array<String>,
            value: String,
            match: (String, String) -> Boolean,
        ) = tag.has(1) && tag[0] == TAG_NAME && match(tag[1], value)

        fun isTagged(
            tag: Array<String>,
            value: Set<String>,
            match: (String, Set<String>) -> Boolean,
        ) = tag.has(1) && tag[0] == TAG_NAME && match(tag[1], value)

        @JvmStatic
        fun parse(tag: Tag): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return tag[1]
        }

        @JvmStatic
        fun parseExternalId(tag: Tag): ExternalId? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }

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
