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
package com.vitorpamplona.amethyst.model.topNavFeeds.bookmarks

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.OldBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.BookmarkIdTag
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.EventBookmark
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

@Immutable
data class PublicBookmarkFeedSnapshot(
    val eventIds: Set<HexKey> = emptySet(),
    val addresses: Set<Address> = emptySet(),
) {
    fun matches(event: Event): Boolean =
        event.id in eventIds ||
            (event as? AddressableEvent)?.address() in addresses
}

val PublicBookmarkListKinds = listOf(BookmarkListEvent.KIND, OldBookmarkListEvent.KIND)

fun publicBookmarkSnapshotForAuthors(
    authors: Set<HexKey>,
    cache: LocalCache,
): PublicBookmarkFeedSnapshot {
    if (authors.isEmpty()) return PublicBookmarkFeedSnapshot()

    val eventIds = mutableSetOf<HexKey>()
    val addresses = mutableSetOf<Address>()

    authors.forEach { pubkey ->
        val bookmarkList = cache.getOrCreateAddressableNote(BookmarkListEvent.createBookmarkAddress(pubkey)).event as? BookmarkListEvent
        bookmarkList?.publicBookmarks()?.collectInto(eventIds, addresses)

        val oldBookmarkList = cache.getOrCreateAddressableNote(OldBookmarkListEvent.createBookmarkAddress(pubkey)).event as? OldBookmarkListEvent
        oldBookmarkList?.publicBookmarks()?.collectInto(eventIds, addresses)
    }

    return PublicBookmarkFeedSnapshot(eventIds, addresses)
}

fun publicBookmarkSnapshotFlow(
    authors: Set<HexKey>,
    cache: LocalCache,
): kotlinx.coroutines.flow.Flow<PublicBookmarkFeedSnapshot> {
    if (authors.isEmpty()) {
        return flowOf(PublicBookmarkFeedSnapshot())
    }

    return cache
        .observeEvents<Event>(
            Filter(
                kinds = PublicBookmarkListKinds,
                authors = authors.sorted(),
            ),
        ).map {
            publicBookmarkSnapshotForAuthors(authors, cache)
        }.onStart {
            emit(publicBookmarkSnapshotForAuthors(authors, cache))
        }.distinctUntilChanged()
}

private fun List<BookmarkIdTag>.collectInto(
    eventIds: MutableSet<HexKey>,
    addresses: MutableSet<Address>,
) {
    forEach {
        when (it) {
            is EventBookmark -> eventIds.add(it.eventId)
            is AddressBookmark -> addresses.add(it.address)
        }
    }
}
