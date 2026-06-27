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
package com.vitorpamplona.quartz.nipXXPodcasting20.metadata

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.podcasts.PodcastShow
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.serialization.Serializable

/**
 * The Podcasting-2.0 draft stores show-level metadata in a `kind:30078` (NIP-78 app-data) event
 * with `d="podcast-metadata"`, where the channel fields live as a JSON object in `content` rather
 * than in tags. This is a parsed, read-only view over such an event that adapts it to the
 * spec-neutral [PodcastShow], so a podstr show merges into the same list and card as a NIP-F4
 * [com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent].
 *
 * Note: `kind:30078` is heavily overloaded across NIPs and clients; only events whose `d` tag is
 * exactly [PODCAST_METADATA_D_TAG] and whose content is valid podcast-metadata JSON resolve here.
 */
@Immutable
class Podcasting20PodcastMetadata(
    val event: AppSpecificDataEvent,
    private val content: Content,
) : PodcastShow {
    override fun showTitle() = content.title

    override fun showImage() = content.image

    override fun showDescription() = content.description

    override fun showWebsites() = listOfNotNull(content.website?.takeIf { it.isNotEmpty() })

    override fun showAuthor() = content.author?.takeIf { it.isNotEmpty() }

    override fun showCategories() = content.categories.filter { it.isNotEmpty() }

    override fun showFundingUrls() = content.funding.filter { it.isNotEmpty() }

    override fun showIsExplicit() = content.explicit ?: false

    override fun showIsComplete() = content.complete ?: false

    override fun showCopyright() = content.copyright?.takeIf { it.isNotEmpty() }

    fun language() = content.language

    /** Contact email for the show, if provided. */
    fun email() = content.email?.takeIf { it.isNotEmpty() }

    /** "episodic" or "serial" per Podcasting 2.0, if provided. */
    fun type() = content.type?.takeIf { it.isNotEmpty() }

    /** Whether the show is locked (premium / subscription-gated). */
    fun isLocked() = content.locked ?: false

    /** The stable podcast GUID (Podcasting 2.0 `podcast:guid`), if provided. */
    fun guid() = content.guid?.takeIf { it.isNotEmpty() }

    /**
     * The subset of the Podcasting-2.0 `kind:30078` metadata JSON this client reads. Unknown keys
     * (notably `value` for value-for-value splits) are ignored by the lenient mapper and can be
     * surfaced later without changing the wire format.
     */
    @Serializable
    class Content(
        val title: String? = null,
        val description: String? = null,
        val author: String? = null,
        val email: String? = null,
        val image: String? = null,
        val language: String? = null,
        val categories: List<String> = emptyList(),
        val explicit: Boolean? = null,
        val website: String? = null,
        val copyright: String? = null,
        val funding: List<String> = emptyList(),
        val locked: Boolean? = null,
        val type: String? = null,
        val complete: Boolean? = null,
        val guid: String? = null,
    )

    companion object {
        const val PODCAST_METADATA_D_TAG = "podcast-metadata"

        /**
         * Returns a view if [event] is a podcast-metadata app-data event with parseable JSON,
         * otherwise null (wrong `d` tag, or non-JSON/encrypted content such as a user's own
         * app settings).
         */
        fun parse(event: AppSpecificDataEvent): Podcasting20PodcastMetadata? {
            if (event.dTag() != PODCAST_METADATA_D_TAG) return null
            val content = runCatching { JsonMapper.fromJson<Content>(event.content) }.getOrNull() ?: return null
            return Podcasting20PodcastMetadata(event, content)
        }

        /**
         * Builds the kind:30078 show-metadata event template (`d="podcast-metadata"`) by serializing
         * [content] to its JSON body. Unset/default fields are omitted, keeping the payload minimal.
         */
        fun build(
            content: Content,
            createdAt: Long = TimeUtils.now(),
        ): EventTemplate<AppSpecificDataEvent> = AppSpecificDataEvent.build(PODCAST_METADATA_D_TAG, JsonMapper.toJson(content), createdAt)
    }
}
