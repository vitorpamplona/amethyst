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
package com.vitorpamplona.amethyst.ios.ui.wiki

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.ios.cache.IosLocalCache
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArrayOrNull
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent

/**
 * Display data for a wiki article (NIP-54, kind 30818).
 */
data class WikiDisplayData(
    val id: String,
    val pubKeyHex: String,
    val pubKeyDisplay: String,
    val profilePictureUrl: String? = null,
    val title: String,
    val summary: String,
    val content: String,
    val image: String? = null,
    val topics: List<String> = emptyList(),
    val createdAt: Long,
)

/**
 * Extension to convert a Note containing a WikiNoteEvent to WikiDisplayData.
 */
fun Note.toWikiDisplayData(cache: IosLocalCache? = null): WikiDisplayData? {
    val event = this.event as? WikiNoteEvent ?: return null
    val user = cache?.getUserIfExists(event.pubKey)

    val displayName =
        user?.toBestDisplayName()
            ?: try {
                event.pubKey.hexToByteArrayOrNull()?.toNpub() ?: event.pubKey.take(16) + "..."
            } catch (e: Exception) {
                event.pubKey.take(16) + "..."
            }

    val pictureUrl = user?.profilePicture()

    return WikiDisplayData(
        id = event.id,
        pubKeyHex = event.pubKey,
        pubKeyDisplay = displayName,
        profilePictureUrl = pictureUrl,
        title = event.title() ?: "Untitled Wiki",
        summary = event.summary() ?: event.content.take(200),
        content = event.content,
        image = event.image(),
        topics = event.topics(),
        createdAt = event.createdAt,
    )
}
