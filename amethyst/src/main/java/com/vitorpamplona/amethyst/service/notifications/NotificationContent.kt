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
package com.vitorpamplona.amethyst.service.notifications

import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoEvent

/**
 * Content-extraction helpers shared by the per-kind notification renderers:
 * decrypting private payloads, pulling a clean one-line excerpt, and resolving
 * the media URL to show as a notification's big picture.
 */
object NotificationContent {
    /** First non-blank line of [content], trimmed to [max] chars, or "" if none. */
    fun excerpt(
        content: String?,
        max: Int = 280,
    ): String =
        content
            ?.split("\n")
            ?.firstOrNull { it.isNotBlank() }
            ?.take(max)
            ?: ""

    suspend fun decryptZapContentAuthor(
        event: LnZapRequestEvent,
        signer: NostrSigner,
    ): Event? =
        if (event.isPrivateZap() && event.zappedAuthor().contains(event.pubKey)) {
            signer.decryptZapEvent(event)
        } else {
            event
        }

    suspend fun decryptContent(
        note: Note,
        signer: NostrSigner,
    ): String? =
        when (val event = note.event) {
            is PrivateDmEvent -> event.decryptContent(signer)
            is LnZapRequestEvent -> decryptZapContentAuthor(event, signer)?.content
            else -> event?.content
        }

    /**
     * The primary image/thumbnail URL to render as a notification's big picture,
     * or null if the event carries no displayable media. Pictures use their first
     * imeta url; videos prefer the poster-frame `image`, falling back to the video
     * url only when no poster is present.
     */
    fun mediaImageUrl(event: Event?): String? =
        when (event) {
            is PictureEvent -> event.imetaTags().firstOrNull()?.url
            is VideoEvent -> {
                val meta = event.imetaTags().firstOrNull()
                meta?.image?.firstOrNull() ?: meta?.url
            }
            else -> null
        }
}
