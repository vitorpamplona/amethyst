package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.runtime.MutableState
import com.vitorpamplona.amethyst.model.Note

sealed class FeedState {
    object Loading : FeedState()
    class Loaded(val feed: MutableState<List<Note>>) : FeedState()
    object Empty : FeedState()
    class FeedError(val errorMessage: String) : FeedState()
}
