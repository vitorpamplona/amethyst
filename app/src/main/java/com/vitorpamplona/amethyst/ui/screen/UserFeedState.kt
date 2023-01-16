package com.vitorpamplona.amethyst.ui.screen

import com.vitorpamplona.amethyst.model.User

sealed class UserFeedState {
    object Loading : UserFeedState()
    class Loaded(val feed: List<User>) : UserFeedState()
    object Empty : UserFeedState()
    class FeedError(val errorMessage: String) : UserFeedState()
}