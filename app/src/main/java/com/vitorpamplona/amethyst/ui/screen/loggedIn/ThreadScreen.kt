package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.service.NostrThreadDataSource
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun ThreadScreen(noteId: String?, accountViewModel: AccountViewModel, navController: NavController) {
    val account by accountViewModel.accountLiveData.observeAsState()

    if (account != null && noteId != null) {
        NostrThreadDataSource.loadThread(noteId)

        val feedViewModel: FeedViewModel = viewModel { FeedViewModel( NostrThreadDataSource ) }

        Column(Modifier.fillMaxHeight()) {
            Column(
                modifier = Modifier.padding(vertical = 0.dp)
            ) {
                ThreadFeedView(noteId, feedViewModel, accountViewModel, navController)
            }
        }
    }
}
