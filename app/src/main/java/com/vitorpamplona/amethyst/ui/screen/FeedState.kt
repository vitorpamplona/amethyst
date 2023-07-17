package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.runtime.MutableState
import com.vitorpamplona.amethyst.model.Note
import kotlinx.collections.immutable.ImmutableList

sealed class FeedState {
    object Loading : FeedState()
    class Loaded(val feed: MutableState<ImmutableList<Note>>, val showHidden: MutableState<Boolean>) : FeedState()
    object Empty : FeedState()
    class FeedError(val errorMessage: String) : FeedState()
}
