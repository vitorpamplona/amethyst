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
package com.vitorpamplona.amethyst.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.desktop.ui.note.NoteDisplayData
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArrayOrNull
import com.vitorpamplona.quartz.nip19Bech32.toNpub

/**
 * Compose-friendly wrapper around [toNoteDisplayData] that observes the
 * event author's user-metadata flow and re-derives the display data
 * whenever it updates. Use at note-card call sites so display names and
 * avatars populate when the kind-0 event arrives from relays after the
 * note itself.
 */
@Composable
fun Event.rememberDisplayData(cache: ICacheProvider?): NoteDisplayData {
    // getOrCreate, not getIfExists: a note is routinely cached before its author's profile is,
    // and a null author here would leave nothing to observe, so the name would never correct
    // itself from the pubkey stub.
    val author = remember(pubKey, cache) { cache?.getOrCreateUser(pubKey) }
    val authorMetaValue = rememberAuthorMetadataKey(author)
    return remember(this, authorMetaValue) { toNoteDisplayData(cache) }
}

/**
 * A token that changes whenever [author]'s kind-0 metadata arrives.
 *
 * Use it as a `remember` key wherever rendering reads an author's name or avatar. A note is
 * almost always cached before its author's profile is, so a one-time lookup draws a pubkey stub
 * and never corrects itself — the cache holds no note-by-author index to invalidate from, and
 * should not: that index would strongly retain every note it knows.
 */
@Composable
fun rememberAuthorMetadataKey(author: User?): Any? {
    val metaValue by produceState<Any?>(initialValue = null, key1 = author) {
        val a = author
        if (a == null) {
            value = null
        } else {
            a.metadata().flow.collect { value = it }
        }
    }
    return metaValue
}

/**
 * Extension to convert Event to NoteDisplayData for the shared NoteCard.
 */
fun Event.toNoteDisplayData(cache: ICacheProvider? = null): NoteDisplayData {
    val user = (cache?.getUserIfExists(pubKey))

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
        tags = ImmutableListOfLists(tags),
    )
}
