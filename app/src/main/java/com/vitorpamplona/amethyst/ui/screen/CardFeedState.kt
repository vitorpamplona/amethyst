package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.runtime.MutableState
import com.vitorpamplona.amethyst.model.Note

abstract class Card() {
    abstract fun createdAt(): Long
    abstract fun id(): String
}

class BadgeCard(val note: Note) : Card() {
    override fun createdAt(): Long {
        return note.createdAt() ?: 0
    }

    override fun id() = note.idHex
}

class NoteCard(val note: Note) : Card() {
    override fun createdAt(): Long {
        return note.createdAt() ?: 0
    }

    override fun id() = note.idHex
}

class LikeSetCard(val note: Note, val likeEvents: List<Note>) : Card() {
    val createdAt = likeEvents.maxOf { it.createdAt() ?: 0 }
    override fun createdAt(): Long {
        return createdAt
    }
    override fun id() = note.idHex + "L" + createdAt
}

class ZapSetCard(val note: Note, val zapEvents: Map<Note, Note>) : Card() {
    val createdAt = zapEvents.maxOf { it.value.createdAt() ?: 0 }
    override fun createdAt(): Long {
        return createdAt
    }
    override fun id() = note.idHex + "Z" + createdAt
}

class MultiSetCard(val note: Note, val boostEvents: List<Note>, val likeEvents: List<Note>, val zapEvents: Map<Note, Note>) : Card() {
    val createdAt = maxOf(
        zapEvents.maxOfOrNull { it.value.createdAt() ?: 0 } ?: 0,
        likeEvents.maxOfOrNull { it.createdAt() ?: 0 } ?: 0,
        boostEvents.maxOfOrNull { it.createdAt() ?: 0 } ?: 0
    )

    override fun createdAt(): Long {
        return createdAt
    }
    override fun id() = note.idHex + "X" + createdAt
}

class BoostSetCard(val note: Note, val boostEvents: List<Note>) : Card() {
    val createdAt = boostEvents.maxOf { it.createdAt() ?: 0 }

    override fun createdAt(): Long {
        return createdAt
    }

    override fun id() = note.idHex + "B" + createdAt
}

class MessageSetCard(val note: Note) : Card() {
    override fun createdAt(): Long {
        return note.createdAt() ?: 0
    }

    override fun id() = note.idHex
}

sealed class CardFeedState {
    object Loading : CardFeedState()
    class Loaded(val feed: MutableState<List<Card>>) : CardFeedState()
    object Empty : CardFeedState()
    class FeedError(val errorMessage: String) : CardFeedState()
}
