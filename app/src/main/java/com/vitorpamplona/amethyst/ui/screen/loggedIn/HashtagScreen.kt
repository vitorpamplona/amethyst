package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.NostrHashtagDataSource
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.ui.actions.SignerDialog
import com.vitorpamplona.amethyst.ui.screen.NostrHashtagFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefresheableFeedView
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import com.vitorpamplona.quartz.events.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun HashtagScreen(tag: String?, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    if (tag == null) return

    PrepareViewModelsHashtagScreen(tag, accountViewModel, nav)
}

@Composable
fun PrepareViewModelsHashtagScreen(tag: String, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val followsFeedViewModel: NostrHashtagFeedViewModel = viewModel(
        key = tag + "HashtagFeedViewModel",
        factory = NostrHashtagFeedViewModel.Factory(
            tag,
            accountViewModel.account
        )
    )

    HashtagScreen(tag, followsFeedViewModel, accountViewModel, nav)
}

@Composable
fun HashtagScreen(tag: String, feedViewModel: NostrHashtagFeedViewModel, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val lifeCycleOwner = LocalLifecycleOwner.current

    NostrHashtagDataSource.loadHashtag(tag)

    LaunchedEffect(tag) {
        NostrHashtagDataSource.start()
        feedViewModel.invalidateData()
    }

    DisposableEffect(accountViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                println("Hashtag Start")
                NostrHashtagDataSource.loadHashtag(tag)
                NostrHashtagDataSource.start()
                feedViewModel.invalidateData()
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                println("Hashtag Stop")
                NostrHashtagDataSource.loadHashtag(null)
                NostrHashtagDataSource.stop()
            }
        }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifeCycleOwner.lifecycle.removeObserver(observer)
            NostrHashtagDataSource.loadHashtag(null)
            NostrHashtagDataSource.stop()
        }
    }

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.padding(vertical = 0.dp)
        ) {
            RefresheableFeedView(
                feedViewModel,
                null,
                accountViewModel = accountViewModel,
                nav = nav
            )
        }
    }
}

@Composable
fun HashtagHeader(tag: String, modifier: Modifier = StdPadding, account: AccountViewModel, onClick: () -> Unit = { }) {
    Column(
        Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Column(modifier = modifier) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "#$tag",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                HashtagActionOptions(tag, account)
            }
        }

        Divider(
            thickness = 0.25.dp
        )
    }
}

@Composable
fun HashtagActionOptions(
    tag: String,
    accountViewModel: AccountViewModel
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val userState by accountViewModel.userProfile().live().follows.observeAsState()
    val isFollowingTag by remember(userState) {
        derivedStateOf {
            userState?.user?.isFollowingHashtagCached(tag) ?: false
        }
    }
    var event by remember { mutableStateOf<Event?>(null) }

    if (event != null) {
        SignerDialog(
            onClose = {
                event = null
            },
            onPost = {
                scope.launch(Dispatchers.IO) {
                    val signedEvent = Event.fromJson(it)
                    Client.send(signedEvent)
                    LocalCache.verifyAndConsume(signedEvent, null)
                    event = null
                }
            },
            data = event!!.toJson()
        )
    }

    if (isFollowingTag) {
        UnfollowButton {
            if (!accountViewModel.isWriteable()) {
                if (accountViewModel.loggedInWithAmber()) {
                    event = accountViewModel.account.unfollowHashtag(tag, false)
                } else {
                    scope.launch {
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.login_with_a_private_key_to_be_able_to_unfollow),
                                Toast.LENGTH_SHORT
                            )
                            .show()
                    }
                }
            } else {
                scope.launch(Dispatchers.IO) {
                    accountViewModel.account.unfollowHashtag(tag, true)
                }
            }
        }
    } else {
        FollowButton {
            if (!accountViewModel.isWriteable()) {
                if (accountViewModel.loggedInWithAmber()) {
                    event = accountViewModel.account.followHashtag(tag, false)
                } else {
                    scope.launch {
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.login_with_a_private_key_to_be_able_to_follow),
                                Toast.LENGTH_SHORT
                            )
                            .show()
                    }
                }
            } else {
                scope.launch(Dispatchers.IO) {
                    accountViewModel.account.followHashtag(tag, true)
                }
            }
        }
    }
}
