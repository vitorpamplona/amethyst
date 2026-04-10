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
package com.vitorpamplona.amethyst.ios.ui.highlights

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.ios.cache.IosLocalCache
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArrayOrNull
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent

/**
 * Display data for a highlight (NIP-84, kind 9802).
 */
data class HighlightDisplayData(
    val id: String,
    val pubKeyHex: String,
    val pubKeyDisplay: String,
    val profilePictureUrl: String? = null,
    val quote: String,
    val comment: String? = null,
    val context: String? = null,
    val sourceUrl: String? = null,
    val sourceAuthorHex: String? = null,
    val sourceAuthorDisplay: String? = null,
    val createdAt: Long,
)

/**
 * Extension to convert a Note containing a HighlightEvent to HighlightDisplayData.
 */
fun Note.toHighlightDisplayData(cache: IosLocalCache? = null): HighlightDisplayData? {
    val event = this.event as? HighlightEvent ?: return null
    val user = cache?.getUserIfExists(event.pubKey)

    val displayName =
        user?.toBestDisplayName()
            ?: try {
                event.pubKey.hexToByteArrayOrNull()?.toNpub() ?: event.pubKey.take(16) + "..."
            } catch (e: Exception) {
                event.pubKey.take(16) + "..."
            }

    val pictureUrl = user?.profilePicture()

    val sourceAuthorHex = event.author()
    val sourceAuthorUser = sourceAuthorHex?.let { cache?.getUserIfExists(it) }
    val sourceAuthorDisplay =
        sourceAuthorUser?.toBestDisplayName()
            ?: sourceAuthorHex?.let {
                try {
                    it.hexToByteArrayOrNull()?.toNpub() ?: it.take(16) + "..."
                } catch (e: Exception) {
                    it.take(16) + "..."
                }
            }

    return HighlightDisplayData(
        id = event.id,
        pubKeyHex = event.pubKey,
        pubKeyDisplay = displayName,
        profilePictureUrl = pictureUrl,
        quote = event.quote(),
        comment = event.comment(),
        context = event.context(),
        sourceUrl = event.inUrl(),
        sourceAuthorHex = sourceAuthorHex,
        sourceAuthorDisplay = sourceAuthorDisplay,
        createdAt = event.createdAt,
    )
}
