package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.vitorpamplona.amethyst.service.NostrUserProfileDataSource
import com.vitorpamplona.amethyst.service.NostrUserProfileFollowersDataSource
import com.vitorpamplona.amethyst.service.NostrUserProfileFollowsDataSource
import com.vitorpamplona.amethyst.service.NostrUserProfileZapsDataSource
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun ThreadScreen(noteId: String?, accountViewModel: AccountViewModel, navController: NavController) {
    val account by accountViewModel.accountLiveData.observeAsState()

    val lifeCycleOwner = LocalLifecycleOwner.current

    DisposableEffect(accountViewModel) {
        val observer = LifecycleEventObserver { source, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                println("Thread Start")
                NostrThreadDataSource.start()
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                println("Thread Stop")
                NostrThreadDataSource.stop()
            }
        }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifeCycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (account != null && noteId != null) {
        NostrThreadDataSource.loadThread(noteId)

        val feedViewModel: NostrThreadFeedViewModel = viewModel()

        Column(Modifier.fillMaxHeight()) {
            Column(
                modifier = Modifier.padding(vertical = 0.dp)
            ) {
                ThreadFeedView(noteId, feedViewModel, accountViewModel, navController)
            }
        }
    }
}
