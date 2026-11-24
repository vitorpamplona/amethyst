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
package com.vitorpamplona.amethyst.commons.model.marmot

import com.vitorpamplona.amethyst.commons.feeds.Card
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.MARMOT_GROUP_LAST_READ_PREFIX
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.marmotGroupLastReadRoute
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupChatroom
import com.vitorpamplona.amethyst.commons.notifications.MessageSetCard
import com.vitorpamplona.amethyst.commons.notifications.NoteCard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Whether [note] is a message of one of this account's Marmot chats that the chat already counts as
 * read: one the user saw there, or history a linked device delivered, which is read on import
 * (`indexImportedMarmotHistory` moves the chat's last-read time past it). Such a message stays in
 * Notifications, dated by its own `created_at`, but it is no news: it lights no unread badge and
 * raises no system notification.
 */
fun Account.isReadMarmotMessage(note: Note): Boolean {
    val rooms = note.inGatherers ?: return false
    val room =
        rooms.firstOrNull { gatherer ->
            gatherer is MarmotGroupChatroom && marmotGroupList.rooms.get(gatherer.nostrGroupId) === gatherer
        } as? MarmotGroupChatroom ?: return false
    val createdAt = note.createdAt() ?: return false
    return createdAt <= loadLastRead(marmotGroupLastReadRoute(room.nostrGroupId))
}

/** Whether [card] is only Marmot messages the chat already read ([isReadMarmotMessage]): no news for a badge. */
fun Account.isReadMarmotCard(card: Card): Boolean =
    when (card) {
        is MessageSetCard -> isReadMarmotMessage(card.note)
        is NoteCard -> isReadMarmotMessage(card.note)
        else -> false
    }

/**
 * Every Marmot chat's last-read time, emitting whenever one moves (or a chat gets one): what
 * [isReadMarmotMessage] reads, so a badge built on it must recompute when this emits, not only when
 * its feed does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun marmotChatReadPositions(lastReadPerRoute: StateFlow<Map<String, StateFlow<Long>>>): Flow<List<Long>> =
    lastReadPerRoute.flatMapLatest { routes ->
        val chats = routes.filterKeys { it.startsWith(MARMOT_GROUP_LAST_READ_PREFIX) }.values
        if (chats.isEmpty()) flowOf(emptyList()) else combine(chats) { it.toList() }
    }

/**
 * Whether the Notifications badge has news: the newest card that is news ([newestNews]) is newer
 * than the tab's [lastRead] time. Recomputed when the cards change, when the tab is read, and when a
 * Marmot chat is read ([marmotReads]), which can turn its messages in the feed into no news.
 */
fun <C> notificationHasNews(
    lastRead: Flow<Long>,
    cards: Flow<C?>,
    marmotReads: Flow<*>,
    newestNews: (C) -> Long?,
): Flow<Boolean> =
    combine(lastRead, cards, marmotReads) { read, current, _ ->
        val newest = current?.let(newestNews)
        newest != null && newest > read
    }

/** The newest card of [cards] (newest first) that is news to the Notifications badge, skipping read Marmot messages. */
fun Account.newestNotificationNews(cards: List<Card>): Card? = cards.firstOrNull { !isReadMarmotCard(it) }
