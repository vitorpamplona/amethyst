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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.service.NostrHashtagDataSource
import com.vitorpamplona.amethyst.ui.dal.HashtagFeedFilter
import com.vitorpamplona.amethyst.ui.screen.NostrHashtagFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefresheableFeedView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun HashtagScreen(tag: String?, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val lifeCycleOwner = LocalLifecycleOwner.current

    if (tag != null) {
        HashtagFeedFilter.loadHashtag(accountViewModel.account, tag)
        val feedViewModel: NostrHashtagFeedViewModel = viewModel()

        LaunchedEffect(tag) {
            HashtagFeedFilter.loadHashtag(accountViewModel.account, tag)
            NostrHashtagDataSource.loadHashtag(tag)
            feedViewModel.invalidateData()
        }

        DisposableEffect(accountViewModel) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    println("Hashtag Start")
                    HashtagFeedFilter.loadHashtag(accountViewModel.account, tag)
                    NostrHashtagDataSource.loadHashtag(tag)
                    NostrHashtagDataSource.start()
                    feedViewModel.invalidateData()
                }
                if (event == Lifecycle.Event.ON_PAUSE) {
                    println("Hashtag Stop")
                    HashtagFeedFilter.loadHashtag(accountViewModel.account, null)
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
                HashtagHeader(tag, accountViewModel)
                RefresheableFeedView(feedViewModel, null, accountViewModel, nav)
            }
        }
    }
}

@Composable
fun HashtagHeader(tag: String, account: AccountViewModel) {
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

                        HashtagActionOptions(tag, account)
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

@Composable
private fun HashtagActionOptions(
    tag: String,
    accountViewModel: AccountViewModel
) {
    val coroutineScope = rememberCoroutineScope()

    val userState by accountViewModel.userProfile().live().follows.observeAsState()
    val isFollowingTag by remember(userState) {
        derivedStateOf {
            userState?.user?.isFollowingHashtagCached(tag) ?: false
        }
    }

    if (isFollowingTag) {
        UnfollowButton { coroutineScope.launch(Dispatchers.IO) { accountViewModel.account.unfollow(tag) } }
    } else {
        FollowButton({ coroutineScope.launch(Dispatchers.IO) { accountViewModel.account.follow(tag) } })
    }
}
