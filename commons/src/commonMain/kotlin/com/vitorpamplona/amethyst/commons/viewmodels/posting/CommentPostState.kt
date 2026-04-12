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
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip22Comments.notify
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.emojis
import com.vitorpamplona.quartz.nip73ExternalIds.ExternalId
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.imetas

/**
 * Platform-independent state for composing a NIP-22 comment.
 *
 * Holds shared post options and builds event templates for both
 * reply-to-event and reply-to-external-identity scenarios.
 * Platform ViewModels wrap this to add uploads, drafts, and signing.
 */
@Stable
class CommentPostState {
    val options = PostOptions()

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
            !hasMediaToUpload

    /**
     * Build a CommentEvent template as a reply to another event.
     *
     * @param taggedMessage the message after tagging
     * @param replyingTo event hint for the event being replied to
     * @param notifyPTags p-tags to notify (from tagger + moderators)
     * @param emojis custom emoji tags found in the message
     * @param usedAttachments iMeta attachments referenced in the message
     * @return the event template
     */
    fun createReplyTemplate(
        taggedMessage: String,
        replyingTo: EventHintBundle<Event>,
        notifyPTags: List<PTag>,
        emojis: List<EmojiUrlTag>,
        usedAttachments: List<IMetaTag>,
    ): EventTemplate<out Event> =
        CommentEvent.replyBuilder(
            msg = taggedMessage,
            replyingTo = replyingTo,
        ) {
            notify(notifyPTags.distinctBy { it.pubKey })

            hashtags(findHashtags(taggedMessage))
            references(findURLs(taggedMessage))
            quotes(findNostrUris(taggedMessage))

            options.applyTo(this)

            emojis(emojis)
            imetas(usedAttachments)
        }

    /**
     * Build a CommentEvent template as a reply to an external identity (NIP-73).
     *
     * @param taggedMessage the message after tagging
     * @param extId the external identity being commented on
     * @param notifyPTags p-tags to notify
     * @param emojis custom emoji tags found in the message
     * @param usedAttachments iMeta attachments referenced in the message
     * @return the event template
     */
    fun createExternalReplyTemplate(
        taggedMessage: String,
        extId: ExternalId,
        notifyPTags: List<PTag>,
        emojis: List<EmojiUrlTag>,
        usedAttachments: List<IMetaTag>,
    ): EventTemplate<out Event> =
        CommentEvent.replyExternalIdentity(
            msg = taggedMessage,
            extId = extId,
        ) {
            notify(notifyPTags)

            hashtags(findHashtags(taggedMessage))
            references(findURLs(taggedMessage))
            quotes(findNostrUris(taggedMessage))

            options.applyTo(this)

            emojis(emojis)
            imetas(usedAttachments)
        }

    fun cancel() {
        options.reset()
    }
}
