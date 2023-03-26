package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.service.NostrThreadDataSource
import com.vitorpamplona.amethyst.ui.dal.ThreadFeedFilter
import com.vitorpamplona.amethyst.ui.screen.NostrThreadFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.ThreadFeedView

@Composable
fun ThreadScreen(noteId: String?, accountViewModel: AccountViewModel, navController: NavController) {
    val account by accountViewModel.accountLiveData.observeAsState()

    val lifeCycleOwner = LocalLifecycleOwner.current

    if (account != null && noteId != null) {
        ThreadFeedFilter.loadThread(noteId)
        val feedViewModel: NostrThreadFeedViewModel = viewModel()

        LaunchedEffect(noteId) {
            NostrThreadDataSource.loadThread(noteId)
            ThreadFeedFilter.loadThread(noteId)
            feedViewModel.invalidateData()
        }

        DisposableEffect(accountViewModel) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    println("Thread Start")
                    NostrThreadDataSource.loadThread(noteId)
                    NostrThreadDataSource.start()
                    ThreadFeedFilter.loadThread(noteId)
                    feedViewModel.invalidateData()
                }
                if (event == Lifecycle.Event.ON_PAUSE) {
                    println("Thread Stop")
                    ThreadFeedFilter.loadThread(null)
                    NostrThreadDataSource.loadThread(null)
                    NostrThreadDataSource.stop()
                }
            }

            lifeCycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifeCycleOwner.lifecycle.removeObserver(observer)
            }
        }

        Column(Modifier.fillMaxHeight()) {
            Column(
                modifier = Modifier.padding(vertical = 0.dp)
            ) {
                ThreadFeedView(noteId, feedViewModel, accountViewModel, navController)
            }
        }
    }
}
