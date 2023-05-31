package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.RelayInfo
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.UserState
import com.vitorpamplona.amethyst.ui.actions.NewRelayListView
import com.vitorpamplona.amethyst.ui.components.BundledUpdate
import com.vitorpamplona.amethyst.ui.note.RelayCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RelayFeedViewModel : ViewModel() {
    val order = compareByDescending<RelayInfo> { it.lastEvent }.thenByDescending { it.counter }.thenBy { it.url }

    private val _feedContent = MutableStateFlow<List<RelayInfo>>(emptyList())
    val feedContent = _feedContent.asStateFlow()

    var currentUser: User? = null

    fun refresh() {
        viewModelScope.launch(Dispatchers.Default) {
            refreshSuspended()
        }
    }

    fun refreshSuspended() {
        val beingUsed = currentUser?.relaysBeingUsed?.values ?: emptyList()
        val beingUsedSet = currentUser?.relaysBeingUsed?.keys ?: emptySet()

        val newRelaysFromRecord = currentUser?.latestContactList?.relays()?.entries?.mapNotNull {
            if (it.key !in beingUsedSet) {
                RelayInfo(it.key, 0, 0)
            } else {
                null
            }
        } ?: emptyList()

        val newList = (beingUsed + newRelaysFromRecord).sortedWith(order)

        _feedContent.update { newList }
    }

    val listener: (UserState) -> Unit = {
        invalidateData()
    }

    fun subscribeTo(user: User) {
        currentUser = user
        user.live().relays.observeForever(listener)
        user.live().relayInfo.observeForever(listener)
        invalidateData()
    }

    fun unsubscribeTo(user: User) {
        user.live().relays.removeObserver(listener)
        user.live().relayInfo.removeObserver(listener)
        currentUser = null
    }

    private val bundler = BundledUpdate(250, Dispatchers.IO)

    fun invalidateData() {
        bundler.invalidate() {
            // adds the time to perform the refresh into this delay
            // holding off new updates in case of heavy refresh routines.
            refreshSuspended()
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun RelayFeedView(
    viewModel: RelayFeedViewModel,
    accountViewModel: AccountViewModel,
    enablePullRefresh: Boolean = true
) {
    val feedState by viewModel.feedContent.collectAsState()

    var wantsToAddRelay by remember {
        mutableStateOf("")
    }

    if (wantsToAddRelay.isNotEmpty()) {
        NewRelayListView({ wantsToAddRelay = "" }, accountViewModel, wantsToAddRelay)
    }

    var refreshing by remember { mutableStateOf(false) }
    val refresh = { refreshing = true; viewModel.refresh(); refreshing = false }
    val pullRefreshState = rememberPullRefreshState(refreshing, onRefresh = refresh)

    val modifier = if (enablePullRefresh) {
        Modifier.pullRefresh(pullRefreshState)
    } else {
        Modifier
    }

    Box(modifier) {
        Column() {
            val listState = rememberLazyListState()

            LazyColumn(
                contentPadding = PaddingValues(
                    top = 10.dp,
                    bottom = 10.dp
                ),
                state = listState
            ) {
                itemsIndexed(feedState, key = { _, item -> item.url }) { _, item ->
                    RelayCompose(
                        item,
                        accountViewModel = accountViewModel,
                        onAddRelay = { wantsToAddRelay = item.url },
                        onRemoveRelay = { wantsToAddRelay = item.url }
                    )
                }
            }
        }

        if (enablePullRefresh) {
            PullRefreshIndicator(refreshing, pullRefreshState, Modifier.align(Alignment.TopCenter))
        }
    }
}
