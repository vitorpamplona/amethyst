package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.service.NostrCommunityDataSource
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.screen.NostrCommunityFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefresheableFeedView

@Composable
fun CommunityScreen(aTagHex: String?, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    if (aTagHex == null) return

    LoadAddressableNote(aTagHex = aTagHex, accountViewModel) {
        it?.let {
            PrepareViewModelsCommunityScreen(
                note = it,
                accountViewModel = accountViewModel,
                nav = nav
            )
        }
    }
}

@Composable
fun PrepareViewModelsCommunityScreen(note: AddressableNote, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val followsFeedViewModel: NostrCommunityFeedViewModel = viewModel(
        key = note.idHex + "CommunityFeedViewModel",
        factory = NostrCommunityFeedViewModel.Factory(
            note,
            accountViewModel.account
        )
    )

    CommunityScreen(note, followsFeedViewModel, accountViewModel, nav)
}

@Composable
fun CommunityScreen(note: AddressableNote, feedViewModel: NostrCommunityFeedViewModel, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val lifeCycleOwner = LocalLifecycleOwner.current

    NostrCommunityDataSource.loadCommunity(note)

    LaunchedEffect(note) {
        feedViewModel.invalidateData()
    }

    DisposableEffect(lifeCycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                println("Community Start")
                NostrCommunityDataSource.start()
                feedViewModel.invalidateData()
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                println("Community Stop")
                NostrCommunityDataSource.loadCommunity(null)
                NostrCommunityDataSource.stop()
            }
        }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifeCycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(Modifier.fillMaxSize()) {
        RefresheableFeedView(
            feedViewModel,
            null,
            accountViewModel = accountViewModel,
            nav = nav
        )
    }
}
