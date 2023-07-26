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
import com.fonfon.kgeohash.toGeoHash
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.NostrGeohashDataSource
import com.vitorpamplona.amethyst.service.ReverseGeoLocationUtil
import com.vitorpamplona.amethyst.ui.screen.NostrGeoHashFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefresheableFeedView
import com.vitorpamplona.amethyst.ui.theme.HalfPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun GeoHashScreen(tag: String?, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    if (tag == null) return

    PrepareViewModelsGeoHashScreen(tag, accountViewModel, nav)
}

@Composable
fun PrepareViewModelsGeoHashScreen(tag: String, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val followsFeedViewModel: NostrGeoHashFeedViewModel = viewModel(
        key = tag + "GeoHashFeedViewModel",
        factory = NostrGeoHashFeedViewModel.Factory(
            tag,
            accountViewModel.account
        )
    )

    GeoHashScreen(tag, followsFeedViewModel, accountViewModel, nav)
}

@Composable
fun GeoHashScreen(tag: String, feedViewModel: NostrGeoHashFeedViewModel, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val lifeCycleOwner = LocalLifecycleOwner.current

    NostrGeohashDataSource.loadHashtag(tag)

    LaunchedEffect(tag) {
        NostrGeohashDataSource.start()
        feedViewModel.invalidateData()
    }

    DisposableEffect(accountViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                println("Hashtag Start")
                NostrGeohashDataSource.loadHashtag(tag)
                NostrGeohashDataSource.start()
                feedViewModel.invalidateData()
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                println("Hashtag Stop")
                NostrGeohashDataSource.loadHashtag(null)
                NostrGeohashDataSource.stop()
            }
        }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifeCycleOwner.lifecycle.removeObserver(observer)
            NostrGeohashDataSource.loadHashtag(null)
            NostrGeohashDataSource.stop()
        }
    }

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.padding(vertical = 0.dp)
        ) {
            GeoHashHeader(tag, accountViewModel)
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
fun GeoHashHeader(tag: String, account: AccountViewModel, onClick: () -> Unit = { }) {
    Column(
        Modifier.clickable { onClick() }
    ) {
        Column(modifier = HalfPadding) {
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
                        val context = LocalContext.current
                        val cityName = remember(tag) {
                            ReverseGeoLocationUtil().execute(tag.toGeoHash().toLocation(), context)
                        }

                        Text(
                            "$cityName ($tag)",
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val userState by accountViewModel.userProfile().live().follows.observeAsState()
    val isFollowingTag by remember(userState) {
        derivedStateOf {
            userState?.user?.isFollowingGeohashCached(tag) ?: false
        }
    }

    if (isFollowingTag) {
        UnfollowButton {
            if (!accountViewModel.isWriteable()) {
                scope.launch {
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.login_with_a_private_key_to_be_able_to_unfollow),
                            Toast.LENGTH_SHORT
                        )
                        .show()
                }
            } else {
                scope.launch(Dispatchers.IO) {
                    accountViewModel.account.unfollowGeohash(tag)
                }
            }
        }
    } else {
        FollowButton {
            if (!accountViewModel.isWriteable()) {
                scope.launch {
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.login_with_a_private_key_to_be_able_to_follow),
                            Toast.LENGTH_SHORT
                        )
                        .show()
                }
            } else {
                scope.launch(Dispatchers.IO) {
                    accountViewModel.account.followGeohash(tag)
                }
            }
        }
    }
}
