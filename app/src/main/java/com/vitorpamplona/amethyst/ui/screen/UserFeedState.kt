package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.runtime.MutableState
import com.vitorpamplona.amethyst.model.User

sealed class UserFeedState {
    object Loading : UserFeedState()
    class Loaded(val feed: MutableState<List<User>>) : UserFeedState()
    object Empty : UserFeedState()
    class FeedError(val errorMessage: String) : UserFeedState()
}
