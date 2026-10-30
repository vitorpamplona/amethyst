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
package com.vitorpamplona.amethyst.commons.nip64Chess.dal

import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.FilterByListParams
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter

/**
 * The chess lobby: games whose challenger or players match the list picked in the top bar. Open
 * challenges, live games and finished ones are all here, newest activity first; each card says
 * which it is.
 */
class ChessLobbyFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + followList().code

    override fun limit() = 200

    fun followList(): TopFilter = account.settings.defaultChessFollowList.value

    override fun showHiddenKey(): Boolean = followList() is TopFilter.MuteList

    override fun feed(): List<Note> {
        val params = buildFilterParams()
        val games = LocalCache.notes.filterIntoSet { _, note -> note.isChessGameStart() && matches(note, params) }
        return sort(games)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> {
        val params = buildFilterParams()
        return newItems.mapNotNullTo(HashSet()) { it.chessGameStartNote()?.takeIf { start -> start.isChessGameStart() && matches(start, params) } }
    }

    private fun matches(
        start: Note,
        params: FilterByListParams,
    ): Boolean {
        val event = start.event ?: return false
        return params.match(event, start.relays) || start.replies.any { move -> move.event?.let { params.match(it, move.relays) } == true }
    }

    private fun buildFilterParams(): FilterByListParams = FilterByListParams.create(account.liveChessFollowLists.value, account.hiddenUsers.flow.value)

    override fun sort(items: Set<Note>): List<Note> = items.sortedByChessActivity()
}
