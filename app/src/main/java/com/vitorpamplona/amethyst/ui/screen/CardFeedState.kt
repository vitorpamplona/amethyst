package com.vitorpamplona.amethyst.ui.screen

import com.vitorpamplona.amethyst.model.Note

abstract class Card() {
    abstract fun createdAt(): Long
}

class NoteCard(val note: Note): Card() {
    override fun createdAt(): Long {
        return note.event?.createdAt ?: 0
    }
}

class LikeSetCard(val note: Note, val likeEvents: List<Note>): Card() {
    val createdAt = likeEvents.maxOf { it.event?.createdAt ?: 0 }

    override fun createdAt(): Long {
        return createdAt
    }
}

class BoostSetCard(val note: Note, val boostEvents: List<Note>): Card() {
    val createdAt = boostEvents.maxOf { it.event?.createdAt ?: 0 }

    override fun createdAt(): Long {
        return createdAt
    }
}

sealed class CardFeedState {
    object Loading: CardFeedState()
    class Loaded(val feed: List<Card>): CardFeedState()
    object Empty: CardFeedState()
    class FeedError(val errorMessage: String): CardFeedState()
}
