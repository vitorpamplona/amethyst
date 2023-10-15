package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.service.NostrThreadDataSource
import com.vitorpamplona.amethyst.ui.screen.NostrThreadFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.ThreadFeedView

@Composable
fun ThreadScreen(noteId: String?, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    if (noteId == null) return

    val lifeCycleOwner = LocalLifecycleOwner.current

    val feedViewModel: NostrThreadFeedViewModel = viewModel(
        key = noteId + "NostrThreadFeedViewModel",
        factory = NostrThreadFeedViewModel.Factory(accountViewModel.account, noteId)
    )

    NostrThreadDataSource.loadThread(noteId)

    DisposableEffect(noteId) {
        feedViewModel.invalidateData(true)
        onDispose {
            NostrThreadDataSource.loadThread(null)
            NostrThreadDataSource.stop()
        }
    }

    DisposableEffect(lifeCycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                println("Thread Start")
                NostrThreadDataSource.loadThread(noteId)
                NostrThreadDataSource.start()
                feedViewModel.invalidateData(true)
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                println("Thread Stop")
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
        Column() {
            ThreadFeedView(noteId, feedViewModel, accountViewModel, nav)
        }
    }
}
