package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.NostrCommunityDataSource
import com.vitorpamplona.amethyst.ui.note.CommunityHeader
import com.vitorpamplona.amethyst.ui.screen.NostrCommunityFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefresheableFeedView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun CommunityScreen(aTagHex: String?, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    if (aTagHex == null) return

    var noteDefBase by remember { mutableStateOf<AddressableNote?>(LocalCache.getAddressableNoteIfExists(aTagHex)) }

    if (noteDefBase == null) {
        LaunchedEffect(aTagHex) {
            // waits to resolve.
            launch(Dispatchers.IO) {
                val newNote = LocalCache.checkGetOrCreateAddressableNote(aTagHex)
                if (newNote != noteDefBase) {
                    noteDefBase = newNote
                }
            }
        }
    }

    noteDefBase?.let {
        PrepareViewModelsCommunityScreen(
            note = it,
            accountViewModel = accountViewModel,
            nav = nav
        )
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

    DisposableEffect(accountViewModel) {
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
        CommunityHeader(baseNote = note, showBottomDiviser = true, accountViewModel = accountViewModel, nav = nav)
        RefresheableFeedView(
            feedViewModel,
            null,
            accountViewModel = accountViewModel,
            nav = nav
        )
    }
}
