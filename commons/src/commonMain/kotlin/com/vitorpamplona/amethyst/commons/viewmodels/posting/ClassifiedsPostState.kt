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
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.emojis
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nip99Classifieds.image
import com.vitorpamplona.quartz.nip99Classifieds.tags.ConditionTag
import com.vitorpamplona.quartz.nip99Classifieds.tags.PriceTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Platform-independent state for creating a classified listing (NIP-99).
 *
 * Holds draft fields and builds the event template. Platform ViewModels
 * wrap this to add uploads, drafts, user suggestions, and signing.
 */
@Stable
class ClassifiedsPostState {
    val options = PostOptions()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _price = MutableStateFlow("")
    val price: StateFlow<String> = _price.asStateFlow()

    private val _location = MutableStateFlow("")
    val location: StateFlow<String> = _location.asStateFlow()

    private val _category = MutableStateFlow("")
    val category: StateFlow<String> = _category.asStateFlow()

    private val _condition = MutableStateFlow(ConditionTag.CONDITION.USED_LIKE_NEW)
    val condition: StateFlow<ConditionTag.CONDITION> = _condition.asStateFlow()

    private val _productImageUrls = MutableStateFlow(listOf<String>())
    val productImageUrls: StateFlow<List<String>> = _productImageUrls.asStateFlow()

    fun updateTitle(value: String) {
        _title.value = value
    }

    fun updatePrice(value: String) {
        _price.value = value
    }

    fun updateLocation(value: String) {
        _location.value = value
    }

    fun updateCategory(value: String) {
        _category.value = value
    }

    fun updateCondition(value: ConditionTag.CONDITION) {
        _condition.value = value
    }

    fun updateProductImageUrls(value: List<String>) {
        _productImageUrls.value = value
    }

    fun canPost(
        messageText: String,
        isUploading: Boolean,
        wantsInvoice: Boolean,
        wantsZapRaiser: Boolean,
        zapRaiserAmount: Long?,
        hasMediaToUpload: Boolean,
    ): Boolean =
        messageText.isNotBlank() &&
            !isUploading &&
            !wantsInvoice &&
            (!wantsZapRaiser || zapRaiserAmount != null) &&
            _title.value.isNotBlank() &&
            _price.value.isNotBlank() &&
            _category.value.isNotBlank() &&
            !hasMediaToUpload

    /**
     * Build the ClassifiedsEvent template from current state.
     *
     * @param taggedMessage the message after tagging
     * @param emojis custom emoji tags found in the message
     * @param usedAttachments iMeta attachments referenced in the message (including product images)
     * @param productImageUrls URLs of product images to tag
     * @return the event template
     */
    fun createTemplate(
        taggedMessage: String,
        emojis: List<EmojiUrlTag>,
        usedAttachments: List<IMetaTag>,
        productImageUrls: List<String>,
    ): EventTemplate<out Event> {
        val quotes = findNostrUris(taggedMessage)

        return ClassifiedsEvent.build(
            _title.value,
            PriceTag(_price.value, "SATS", null),
            taggedMessage,
            _location.value.ifBlank { null },
            _condition.value,
        ) {
            productImageUrls.forEach { image(it) }

            hashtags(listOfNotNull(_category.value.ifBlank { null }) + findHashtags(taggedMessage))
            quotes(quotes)

            options.applyTo(this)

            emojis(emojis)
            imetas(usedAttachments)
            references(findURLs(taggedMessage))
        }
    }

    fun cancel() {
        _title.value = ""
        _price.value = ""
        _location.value = ""
        _category.value = ""
        _condition.value = ConditionTag.CONDITION.USED_LIKE_NEW
        _productImageUrls.value = emptyList()
        options.reset()
    }
}
