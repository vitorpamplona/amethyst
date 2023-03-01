package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.vitorpamplona.amethyst.model.RelayInfo
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.UserState
import com.vitorpamplona.amethyst.ui.actions.NewRelayListView
import com.vitorpamplona.amethyst.ui.note.RelayCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RelayFeedViewModel: ViewModel() {
    val order = compareByDescending<RelayInfo> { it.lastEvent }.thenByDescending { it.counter }.thenBy { it.url }

    private val _feedContent = MutableStateFlow<List<RelayInfo>>(emptyList())
    val feedContent = _feedContent.asStateFlow()

    var currentUser: User? = null

    fun refresh() {
        viewModelScope.launch(Dispatchers.Default) {
            val beingUsed = currentUser?.relaysBeingUsed?.values ?: emptyList()
            val beingUsedSet = currentUser?.relaysBeingUsed?.keys ?: emptySet()

            val newRelaysFromRecord = currentUser?.relays?.entries?.mapNotNull {
                if (it.key !in beingUsedSet) {
                    RelayInfo(it.key, 0, 0)
                } else {
                    null
                }
            } ?: emptyList()

            val newList = (beingUsed + newRelaysFromRecord).sortedWith(order)

            _feedContent.update { newList }
        }
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

    var handlerWaiting = AtomicBoolean()

    private fun invalidateData() {
        if (handlerWaiting.getAndSet(true)) return

        val scope = CoroutineScope(Job() + Dispatchers.Default)
        scope.launch {
            try {
                delay(50)
                refresh()
            } finally {
                withContext(NonCancellable) {
                    handlerWaiting.set(false)
                }
            }
            handlerWaiting.set(false)
        }
    }
}

@Composable
fun RelayFeedView(viewModel: RelayFeedViewModel, accountViewModel: AccountViewModel, navController: NavController) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    val feedState by viewModel.feedContent.collectAsState()

    var isRefreshing by remember { mutableStateOf(false) }
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing)

    var wantsToAddRelay by remember {
        mutableStateOf( "")
    }

    if (wantsToAddRelay.isNotEmpty())
        NewRelayListView({ wantsToAddRelay = "" }, account, wantsToAddRelay)

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            viewModel.refresh()
            isRefreshing = false
        }
    }

    SwipeRefresh(
        state = swipeRefreshState,
        onRefresh = {
            isRefreshing = true
        },
    ) {
        Column() {
            val listState = rememberLazyListState()

            LazyColumn(
                contentPadding = PaddingValues(
                    top = 10.dp,
                    bottom = 10.dp
                ),
                state = listState
            ) {
                itemsIndexed(feedState, key = { _, item -> item.url }) { index, item ->
                    RelayCompose(item,
                        accountViewModel = accountViewModel,
                        navController = navController,
                        onAddRelay = { wantsToAddRelay = item.url },
                        onRemoveRelay = { wantsToAddRelay = item.url }
                    )
                }
            }
        }
    }
}
