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
package com.vitorpamplona.amethyst.commons.viewmodels.posting

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.emojis
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.uuid.ExperimentalUuidApi

/**
 * Platform-independent state for composing a long-form article (NIP-23).
 *
 * Holds draft fields and builds the event template. Platform ViewModels
 * wrap this to add uploads, drafts, user suggestions, and signing.
 */
@Stable
class LongFormPostState {
    val options = PostOptions()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _summary = MutableStateFlow("")
    val summary: StateFlow<String> = _summary.asStateFlow()

    private val _coverImageUrl = MutableStateFlow("")
    val coverImageUrl: StateFlow<String> = _coverImageUrl.asStateFlow()

    private val _publishedAt = MutableStateFlow(TimeUtils.now())
    val publishedAt: StateFlow<Long> = _publishedAt.asStateFlow()

    private val _tags = MutableStateFlow(listOf<String>())
    val tags: StateFlow<List<String>> = _tags.asStateFlow()

    private val _slug = MutableStateFlow("")
    val slug: StateFlow<String> = _slug.asStateFlow()

    private val _existingDTag = MutableStateFlow<String?>(null)
    val existingDTag: StateFlow<String?> = _existingDTag.asStateFlow()

    val isEditing: Boolean get() = _existingDTag.value != null

    fun updateTitle(value: String) {
        _title.value = value
    }

    fun updateSummary(value: String) {
        _summary.value = value
    }

    fun updateCoverImageUrl(value: String) {
        _coverImageUrl.value = value
    }

    fun updatePublishedAt(value: Long) {
        _publishedAt.value = value
    }

    fun updateTags(value: List<String>) {
        _tags.value = value
    }

    fun updateSlug(value: String) {
        _slug.value = value
    }

    fun updateExistingDTag(value: String?) {
        _existingDTag.value = value
    }

    fun canPost(
        messageText: String,
        isUploadingImage: Boolean,
        wantsInvoice: Boolean,
        wantsZapRaiser: Boolean,
        zapRaiserAmount: Long?,
        hasMediaToUpload: Boolean,
    ): Boolean =
        _title.value.isNotBlank() &&
            messageText.isNotBlank() &&
            !isUploadingImage &&
            !wantsInvoice &&
            (!wantsZapRaiser || zapRaiserAmount != null) &&
            !hasMediaToUpload

    /**
     * Build the LongTextNoteEvent template from current state.
     *
     * @param taggedMessage the message after tagging (@mentions resolved to nostr: URIs)
     * @param emojis custom emoji tags found in the message
     * @param usedAttachments iMeta attachments referenced in the message
     * @return the event template, or null if title is blank
     */
    @OptIn(ExperimentalUuidApi::class)
    fun createTemplate(
        taggedMessage: String,
        emojis: List<EmojiUrlTag>,
        usedAttachments: List<IMetaTag>,
    ): EventTemplate<out Event>? {
        if (_title.value.isBlank()) return null

        return LongTextNoteEvent.build(
            description = taggedMessage,
            title = _title.value.trim(),
            summary = _summary.value.trim().ifBlank { null },
            image = _coverImageUrl.value.trim().ifBlank { null },
            publishedAt = _publishedAt.value,
            dTag = _existingDTag.value ?: _slug.value.ifBlank { RandomInstance.randomChars(16) },
        ) {
            hashtags(findHashtags(taggedMessage) + _tags.value)
            references(findURLs(taggedMessage))
            quotes(findNostrUris(taggedMessage))

            options.applyTo(this)

            emojis(emojis)
            imetas(usedAttachments)
        }
    }

    fun cancel() {
        _title.value = ""
        _summary.value = ""
        _coverImageUrl.value = ""
        _publishedAt.value = TimeUtils.now()
        _tags.value = emptyList()
        _slug.value = ""
        _existingDTag.value = null
        options.reset()
    }

    companion object {
        /**
         * Find custom emojis in text that match the user's emoji set.
         */
        fun findEmoji(
            message: String,
            emojiCodes: List<Pair<String, String>>?,
        ): List<EmojiUrlTag> {
            if (emojiCodes == null) return emptyList()
            return CustomEmoji.findAllEmojiCodes(message).mapNotNull { possibleEmoji ->
                emojiCodes
                    .firstOrNull { it.first == possibleEmoji }
                    ?.let { EmojiUrlTag(it.first, it.second) }
            }
        }
    }
}
