/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note

class BookmarkPrivateFeedFilter(val account: Account) : FeedFilter<Note>() {
    override fun feedKey(): String {
        return account.userProfile().latestBookmarkList?.id ?: ""
    }

    override fun feed(): List<Note> {
        val bookmarks = account.userProfile().latestBookmarkList

        if (!account.isWriteable()) return emptyList()

        val privateTags = bookmarks?.cachedPrivateTags() ?: return emptyList()

        val notes =
            bookmarks.filterEvents(privateTags).mapNotNull { LocalCache.checkGetOrCreateNote(it) }

        val addresses =
            bookmarks.filterAddresses(privateTags).map { LocalCache.getOrCreateAddressableNote(it) }

        return notes
            .plus(addresses)
            .toSet()
            .sortedWith(DefaultFeedOrder)
    }
}
