/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.ui.feeds.LoadedFeedState
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.firstFullCharOrEmoji
import com.vitorpamplona.quartz.nip01Core.core.ImmutableListOfLists
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.MutableStateFlow

@Immutable
interface Card {
    fun createdAt(): Long

    fun id(): String
}

@Immutable
class BadgeCard(
    val note: Note,
) : Card {
    override fun createdAt(): Long = note.createdAt() ?: 0L

    override fun id() = note.idHex
}

@Immutable
class NoteCard(
    val note: Note,
) : Card {
    override fun createdAt(): Long = note.createdAt() ?: 0L

    override fun id() = note.idHex
}

@Immutable
class ZapUserSetCard(
    val user: User,
    val zapEvents: ImmutableList<CombinedZap>,
) : Card {
    val createdAt = zapEvents.maxOfOrNull { it.createdAt() ?: 0L } ?: 0L

    override fun createdAt(): Long = createdAt

    override fun id() = user.pubkeyHex + "U" + createdAt
}

@Immutable
class MultiSetCard(
    val note: Note,
    val boostEvents: ImmutableList<Note>,
    val likeEvents: ImmutableList<Note>,
    val zapEvents: ImmutableList<CombinedZap>,
) : Card {
    val maxCreatedAt =
        maxOf(
            zapEvents.maxOfOrNull { it.createdAt() ?: 0L } ?: 0L,
            likeEvents.maxOfOrNull { it.createdAt() ?: 0L } ?: 0L,
            boostEvents.maxOfOrNull { it.createdAt() ?: 0L } ?: 0L,
        )

    val minCreatedAt =
        minOf(
            zapEvents.minOfOrNull { it.createdAt() ?: Long.MAX_VALUE } ?: Long.MAX_VALUE,
            likeEvents.minOfOrNull { it.createdAt() ?: Long.MAX_VALUE } ?: Long.MAX_VALUE,
            boostEvents.minOfOrNull { it.createdAt() ?: Long.MAX_VALUE } ?: Long.MAX_VALUE,
        )

    val likeEventsByType =
        likeEvents
            .groupBy {
                it.event
                    ?.content
                    ?.firstFullCharOrEmoji(ImmutableListOfLists(it.event?.tags ?: emptyArray()))
                    ?: "+"
            }.mapValues { it.value.toImmutableList() }
            .toImmutableMap()

    override fun createdAt(): Long = maxCreatedAt

    override fun id() = note.idHex + "X" + maxCreatedAt + "X" + minCreatedAt
}

@Immutable
class MessageSetCard(
    val note: Note,
) : Card {
    override fun createdAt(): Long = note.createdAt() ?: 0L

    override fun id() = note.idHex
}

@Immutable
sealed class CardFeedState {
    @Immutable object Loading : CardFeedState()

    @Stable
    class Loaded(
        val feed: MutableStateFlow<LoadedFeedState<Card>>,
    ) : CardFeedState()

    @Immutable object Empty : CardFeedState()

    @Immutable class FeedError(
        val errorMessage: String,
    ) : CardFeedState()
}
