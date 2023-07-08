package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.runtime.MutableState
import com.vitorpamplona.amethyst.model.User
import kotlinx.collections.immutable.ImmutableList

sealed class UserFeedState {
    object Loading : UserFeedState()
    class Loaded(val feed: MutableState<ImmutableList<User>>) : UserFeedState()
    object Empty : UserFeedState()
    class FeedError(val errorMessage: String) : UserFeedState()
}
