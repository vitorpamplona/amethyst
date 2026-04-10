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
package com.vitorpamplona.amethyst.ios.ui

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.ios.cache.IosLocalCache
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArrayOrNull
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.utils.BigDecimal

/**
 * Display data for a note card.
 */
data class NoteDisplayData(
    val id: String,
    val pubKeyHex: String,
    val pubKeyDisplay: String,
    val profilePictureUrl: String? = null,
    val content: String,
    val createdAt: Long,
    val reactionCount: Int = 0,
    val boostCount: Int = 0,
    val replyCount: Int = 0,
    val zapAmount: BigDecimal = BigDecimal("0"),
    val zapCount: Int = 0,
)

/**
 * Display data for a long-form article (NIP-23, kind 30023).
 */
data class ArticleDisplayData(
    val id: String,
    val pubKeyHex: String,
    val pubKeyDisplay: String,
    val profilePictureUrl: String? = null,
    val title: String,
    val summary: String,
    val content: String,
    val image: String? = null,
    val publishedAt: Long,
    val createdAt: Long,
)

/**
 * Extension to convert Event to NoteDisplayData.
 */
fun Event.toNoteDisplayData(cache: IosLocalCache? = null): NoteDisplayData {
    val user = cache?.getUserIfExists(pubKey)

    val displayName =
        user?.toBestDisplayName()
            ?: try {
                pubKey.hexToByteArrayOrNull()?.toNpub() ?: pubKey.take(16) + "..."
            } catch (e: Exception) {
                pubKey.take(16) + "..."
            }

    val pictureUrl = user?.profilePicture()

    return NoteDisplayData(
        id = id,
        pubKeyHex = pubKey,
        pubKeyDisplay = displayName,
        profilePictureUrl = pictureUrl,
        content = content,
        createdAt = createdAt,
    )
}

/**
 * Extension to convert a commons Note to NoteDisplayData with counts.
 */
fun Note.toNoteDisplayData(cache: IosLocalCache? = null): NoteDisplayData {
    val event =
        this.event ?: return NoteDisplayData(
            id = idHex,
            pubKeyHex = "",
            pubKeyDisplay = idHex.take(16) + "...",
            content = "",
            createdAt = 0L,
        )
    val base = event.toNoteDisplayData(cache)
    return base.copy(
        reactionCount = countReactions(),
        boostCount = boosts.size,
        replyCount = replies.size,
        zapAmount = zapsAmount,
        zapCount = zaps.size,
    )
}

/**
 * Extension to convert a LongTextNoteEvent to ArticleDisplayData.
 */
fun LongTextNoteEvent.toArticleDisplayData(cache: IosLocalCache? = null): ArticleDisplayData {
    val user = cache?.getUserIfExists(pubKey)

    val displayName =
        user?.toBestDisplayName()
            ?: try {
                pubKey.hexToByteArrayOrNull()?.toNpub() ?: pubKey.take(16) + "..."
            } catch (e: Exception) {
                pubKey.take(16) + "..."
            }

    val pictureUrl = user?.profilePicture()

    return ArticleDisplayData(
        id = id,
        pubKeyHex = pubKey,
        pubKeyDisplay = displayName,
        profilePictureUrl = pictureUrl,
        title = title() ?: "Untitled",
        summary = summary() ?: "",
        content = content,
        image = image(),
        publishedAt = publishedAt() ?: createdAt,
        createdAt = createdAt,
    )
}

/**
 * Extension to convert a Note containing a LongTextNoteEvent to ArticleDisplayData.
 */
fun Note.toArticleDisplayData(cache: IosLocalCache? = null): ArticleDisplayData? {
    val event = this.event as? LongTextNoteEvent ?: return null
    return event.toArticleDisplayData(cache)
}
