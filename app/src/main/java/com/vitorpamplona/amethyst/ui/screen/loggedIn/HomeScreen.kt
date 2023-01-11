package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.ExperimentalLifecycleComposeApi
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.service.NostrHomeDataSource
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@OptIn(ExperimentalLifecycleComposeApi::class)
@Composable
fun HomeScreen(accountViewModel: AccountViewModel) {
    val account by accountViewModel.accountLiveData.observeAsState()

    if (account != null) {
        val feedViewModel: FeedViewModel = viewModel { FeedViewModel( NostrHomeDataSource ) }

        Column(Modifier.fillMaxHeight()) {
            Column(
                modifier = Modifier.padding(vertical = 0.dp)
            ) {
                FeedView(feedViewModel, accountViewModel)
            }
        }
    }
}