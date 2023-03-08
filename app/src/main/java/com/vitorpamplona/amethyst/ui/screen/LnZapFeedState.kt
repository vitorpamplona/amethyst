package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.runtime.MutableState
import com.vitorpamplona.amethyst.model.Note

sealed class LnZapFeedState {
    object Loading : LnZapFeedState()
    class Loaded(val feed: MutableState<List<Pair<Note, Note>>>) : LnZapFeedState()
    object Empty : LnZapFeedState()
    class FeedError(val errorMessage: String) : LnZapFeedState()
}
