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
package com.vitorpamplona.amethyst.commons.model.marmotGroups

import com.vitorpamplona.marmot.appComponents.EncryptedMediaPolicyV2
import com.vitorpamplona.marmot.appComponents.EncryptedMediaReferenceV2
import com.vitorpamplona.marmot.appComponents.EncryptedMediaV2
import com.vitorpamplona.quartz.nip01Core.core.Event

/**
 * NIP-30 custom emoji inside a Marmot group, the way MDK 0.12+ sends them.
 *
 * The image of a Marmot custom emoji is an ordinary encrypted attachment of
 * the same message: the event carries an `encrypted-media-v2` `imeta` for it,
 * and the `["emoji", shortcode, url]` tag names it by that attachment's FIRST
 * locator. So the URL is ciphertext, opened with the media key of the epoch
 * that carried the message.
 *
 * Two rules follow from that, both MDK's:
 * - An emoji tag whose URL matches no attachment of the same event is not a
 *   decryptable reference. Its URL is never fetched, and the `:shortcode:`
 *   stays literal text.
 * - An attachment some emoji tag names is artwork for the text, not a file
 *   the message sends, so it is drawn inline and not as a gallery item.
 */
object MarmotCustomEmoji {
    const val TAG_EMOJI = "emoji"

    /** MDK's `validate_message_tags`: the extra tags of one message, by count and by bytes. */
    const val MAX_MESSAGE_TAGS = 64
    const val MAX_MESSAGE_TAG_BYTES = 16 * 1024

    /** Every `encrypted-media-v2` attachment of [event], in tag order. */
    fun attachments(event: Event): List<EncryptedMediaReferenceV2> =
        event.tags.mapNotNull { tag ->
            if (tag.size > 1 && tag[0] == "imeta") EncryptedMediaV2.parseImetaTagOrNull(tag) else null
        }

    /** The locator an emoji tag names an attachment by: its first, as MDK writes it. */
    fun firstLocator(reference: EncryptedMediaReferenceV2): String? = reference.locators.firstOrNull()?.value

    /** The blossom locator the renderer downloads, which is the first `blossom-v1` one. */
    fun downloadLocator(reference: EncryptedMediaReferenceV2): String? = reference.locators.firstOrNull { it.kind == EncryptedMediaPolicyV2.INITIAL_LOCATOR_KIND }?.value

    /**
     * Emoji URL → the attachment it names, for every emoji tag of [event] that
     * names one of the event's own attachments.
     */
    fun matchedEmojiAttachments(event: Event): Map<String, EncryptedMediaReferenceV2> {
        val emojiUrls =
            event.tags.mapNotNullTo(HashSet()) { tag ->
                if (tag.size >= 3 && tag[0] == TAG_EMOJI) tag[2] else null
            }
        if (emojiUrls.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, EncryptedMediaReferenceV2>()
        for (reference in attachments(event)) {
            val locator = firstLocator(reference) ?: continue
            if (locator in emojiUrls && locator !in result) result[locator] = reference
        }
        return result
    }

    /** True when every attachment of [event] is emoji artwork, so the message is text with inline images. */
    fun hasOnlyEmojiAttachments(event: Event): Boolean {
        val all = attachments(event)
        if (all.isEmpty()) return false
        val matched = matchedEmojiAttachments(event)
        return all.all { firstLocator(it) in matched }
    }

    /**
     * The tags a renderer may use for [event]: its own tags with every emoji tag
     * whose URL is not backed by an attachment removed, so that `:shortcode:`
     * renders as literal text instead of fetching an arbitrary URL.
     */
    fun safeRenderTags(event: Event): Array<Array<String>> {
        if (event.tags.none { it.size >= 3 && it[0] == TAG_EMOJI }) return event.tags
        val matched = matchedEmojiAttachments(event).keys
        return event.tags.filter { tag -> !(tag.size >= 3 && tag[0] == TAG_EMOJI && tag[2] !in matched) }.toTypedArray()
    }

    /**
     * The `:shortcode:` a custom-emoji reaction key starts with (`:party:` from `:party:https://…`),
     * or null for anything else. In a group every reaction carries its own encrypted copy of the
     * picture, under its own URL, so the shortcode is what makes two reactions the same one.
     */
    fun reactionShortcode(reaction: String): String? {
        if (reaction.length < 3 || reaction[0] != ':') return null
        val end = reaction.indexOf(':', 1)
        return if (end > 1) reaction.substring(0, end + 1) else null
    }

    /** MDK's limits on the extra tags of a message. */
    fun withinTagLimits(tags: List<Array<String>>): Boolean = tags.size <= MAX_MESSAGE_TAGS && tags.sumOf { tag -> tag.sumOf { it.encodeToByteArray().size } } <= MAX_MESSAGE_TAG_BYTES
}
