package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.ExperimentalLifecycleComposeApi
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.service.NostrGlobalDataSource
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@OptIn(ExperimentalLifecycleComposeApi::class)
@Composable
fun SearchScreen(accountViewModel: AccountViewModel, navController: NavController) {
    val feedViewModel: FeedViewModel = viewModel { FeedViewModel( NostrGlobalDataSource ) }

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.padding(vertical = 0.dp)
        ) {
            FeedView(feedViewModel, accountViewModel, navController)
        }
    }
}
