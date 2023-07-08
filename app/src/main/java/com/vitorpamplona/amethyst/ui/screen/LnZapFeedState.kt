package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.model.Note
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class ZapReqResponse(val zapRequest: Note, val zapEvent: Note)

@Stable
sealed class LnZapFeedState {
    object Loading : LnZapFeedState()
    class Loaded(val feed: MutableState<ImmutableList<ZapReqResponse>>) : LnZapFeedState()
    object Empty : LnZapFeedState()
    class FeedError(val errorMessage: String) : LnZapFeedState()
}
