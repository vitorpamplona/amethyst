package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.NostrHashtagDataSource
import com.vitorpamplona.amethyst.ui.dal.HashtagFeedFilter
import com.vitorpamplona.amethyst.ui.screen.FeedView
import com.vitorpamplona.amethyst.ui.screen.NostrHashtagFeedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun HashtagScreen(tag: String?, accountViewModel: AccountViewModel, navController: NavController) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    val lifeCycleOwner = LocalLifecycleOwner.current

    if (tag != null) {
        HashtagFeedFilter.loadHashtag(account, tag)
        val feedViewModel: NostrHashtagFeedViewModel = viewModel()

        LaunchedEffect(tag) {
            HashtagFeedFilter.loadHashtag(account, tag)
            NostrHashtagDataSource.loadHashtag(tag)
            feedViewModel.invalidateData()
        }

        DisposableEffect(accountViewModel) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    println("Hashtag Start")
                    HashtagFeedFilter.loadHashtag(account, tag)
                    NostrHashtagDataSource.loadHashtag(tag)
                    NostrHashtagDataSource.start()
                    feedViewModel.invalidateData()
                }
                if (event == Lifecycle.Event.ON_PAUSE) {
                    println("Hashtag Stop")
                    HashtagFeedFilter.loadHashtag(account, null)
                    NostrHashtagDataSource.loadHashtag(null)
                    NostrHashtagDataSource.stop()
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
                HashtagHeader(tag, account)
                FeedView(feedViewModel, accountViewModel, navController, null)
            }
        }
    }
}

@Composable
fun HashtagHeader(tag: String, account: Account) {
    val userState by account.userProfile().live().follows.observeAsState()
    val userFollows = userState?.user ?: return

    val coroutineScope = rememberCoroutineScope()

    Column() {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "#$tag",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )

                        if (userFollows.isFollowingHashtagCached(tag)) {
                            UnfollowButton { coroutineScope.launch(Dispatchers.IO) { account.unfollow(tag) } }
                        } else {
                            FollowButton({ coroutineScope.launch(Dispatchers.IO) { account.follow(tag) } })
                        }
                    }
                }
            }
        }

        Divider(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp),
            thickness = 0.25.dp
        )
    }
}
