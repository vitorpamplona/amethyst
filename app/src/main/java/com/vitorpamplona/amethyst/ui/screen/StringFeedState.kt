package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.runtime.MutableState
import kotlinx.collections.immutable.ImmutableList

sealed class StringFeedState {
    object Loading : StringFeedState()
    class Loaded(val feed: MutableState<ImmutableList<String>>) : StringFeedState()
    object Empty : StringFeedState()
    class FeedError(val errorMessage: String) : StringFeedState()
}
