package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

@Immutable
abstract class Card() {
    abstract fun createdAt(): Long
    abstract fun id(): String
}

@Immutable
class BadgeCard(val note: Note) : Card() {
    override fun createdAt(): Long {
        return note.createdAt() ?: 0
    }

    override fun id() = note.idHex
}

@Immutable
class NoteCard(val note: Note) : Card() {
    override fun createdAt(): Long {
        return note.createdAt() ?: 0
    }

    override fun id() = note.idHex
}

@Immutable
class ZapUserSetCard(val user: User, val zapEvents: ImmutableMap<Note, Note>) : Card() {
    val createdAt = zapEvents.maxOf { it.value.createdAt() ?: 0 }
    override fun createdAt(): Long {
        return createdAt
    }
    override fun id() = user.pubkeyHex + "U" + createdAt
}

@Immutable
class MultiSetCard(val note: Note, val boostEvents: ImmutableList<Note>, val likeEvents: ImmutableList<Note>, val zapEvents: ImmutableMap<Note, Note>) : Card() {
    val maxCreatedAt = maxOf(
        zapEvents.maxOfOrNull { it.value.createdAt() ?: 0 } ?: 0,
        likeEvents.maxOfOrNull { it.createdAt() ?: 0 } ?: 0,
        boostEvents.maxOfOrNull { it.createdAt() ?: 0 } ?: 0
    )

    val minCreatedAt = minOf(
        zapEvents.minOfOrNull { it.value.createdAt() ?: Long.MAX_VALUE } ?: Long.MAX_VALUE,
        likeEvents.minOfOrNull { it.createdAt() ?: Long.MAX_VALUE } ?: Long.MAX_VALUE,
        boostEvents.minOfOrNull { it.createdAt() ?: Long.MAX_VALUE } ?: Long.MAX_VALUE
    )

    override fun createdAt(): Long {
        return maxCreatedAt
    }
    override fun id() = note.idHex + "X" + maxCreatedAt + "X" + minCreatedAt
}

@Immutable
class MessageSetCard(val note: Note) : Card() {
    override fun createdAt(): Long {
        return note.createdAt() ?: 0
    }

    override fun id() = note.idHex
}

@Immutable
sealed class CardFeedState {
    @Immutable
    object Loading : CardFeedState()

    @Stable
    class Loaded(val feed: MutableState<ImmutableList<Card>>) : CardFeedState()

    @Immutable
    object Empty : CardFeedState()

    @Immutable
    class FeedError(val errorMessage: String) : CardFeedState()
}
