package com.vitorpamplona.amethyst.ui.screen

import com.vitorpamplona.amethyst.model.Note


sealed class FeedState {
    object Loading : FeedState()
    class Loaded(val feed: List<Note>) : FeedState()
    object Empty : FeedState()
    class FeedError(val errorMessage: String) : FeedState()
}