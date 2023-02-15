package com.vitorpamplona.amethyst.ui.navigation

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ScaffoldState
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.Coil
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.RoboHashCache
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.NostrAccountDataSource
import com.vitorpamplona.amethyst.service.NostrChannelDataSource
import com.vitorpamplona.amethyst.service.NostrChatRoomDataSource
import com.vitorpamplona.amethyst.service.NostrChatroomListDataSource
import com.vitorpamplona.amethyst.service.NostrGlobalDataSource
import com.vitorpamplona.amethyst.service.NostrHomeDataSource
import com.vitorpamplona.amethyst.service.NostrNotificationDataSource
import com.vitorpamplona.amethyst.service.NostrSearchEventOrUserDataSource
import com.vitorpamplona.amethyst.service.NostrSingleChannelDataSource
import com.vitorpamplona.amethyst.service.NostrSingleEventDataSource
import com.vitorpamplona.amethyst.service.NostrSingleUserDataSource
import com.vitorpamplona.amethyst.service.NostrThreadDataSource
import com.vitorpamplona.amethyst.service.NostrUserProfileDataSource
import com.vitorpamplona.amethyst.service.NostrUserProfileFollowersDataSource
import com.vitorpamplona.amethyst.service.NostrUserProfileFollowsDataSource
import com.vitorpamplona.amethyst.service.NostrUserProfileZapsDataSource
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.service.relays.RelayPool
import com.vitorpamplona.amethyst.ui.AsyncImageProxy
import com.vitorpamplona.amethyst.ui.ResizeImage
import com.vitorpamplona.amethyst.ui.actions.NewRelayListView
import com.vitorpamplona.amethyst.ui.actions.NewUserMetadataView
import com.vitorpamplona.amethyst.ui.screen.RelayPoolViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import java.net.URLEncoder
import kotlinx.coroutines.launch

@Composable
fun AppTopBar(navController: NavHostController, scaffoldState: ScaffoldState, accountViewModel: AccountViewModel) {
    when (currentRoute(navController)) {
        //Route.Profile.route -> TopBarWithBackButton(navController)
        else -> MainTopBar(scaffoldState, accountViewModel)
    }
}


@Composable
fun MainTopBar(scaffoldState: ScaffoldState, accountViewModel: AccountViewModel) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    val accountUserState by account.userProfile().liveMetadata.observeAsState()
    val accountUser = accountUserState?.user ?: return

    val relayViewModel: RelayPoolViewModel = viewModel { RelayPoolViewModel() }
    val connectedRelaysLiveData by relayViewModel.connectedRelaysLiveData.observeAsState()
    val availableRelaysLiveData by relayViewModel.availableRelaysLiveData.observeAsState()

    val coroutineScope = rememberCoroutineScope()

    val context = LocalContext.current
    val ctx = LocalContext.current.applicationContext

    var wantsToEditRelays by remember {
        mutableStateOf(false)
    }

    if (wantsToEditRelays)
        NewRelayListView({ wantsToEditRelays = false }, account)

    Column() {
        TopAppBar(
            elevation = 0.dp,
            backgroundColor = Color(0xFFFFFF),
            title = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .padding(start = 0.dp, end = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            IconButton(
                                onClick = {
                                    Client.allSubscriptions().map {
                                        "${it} ${
                                            Client.getSubscriptionFilters(it)
                                                .joinToString { it.filter.toJson() }
                                        }"
                                    }.forEach {
                                        Log.d("CURRENT FILTERS", it)
                                    }

                                    NostrAccountDataSource.printCounter()
                                    NostrChannelDataSource.printCounter()
                                    NostrChatRoomDataSource.printCounter()
                                    NostrChatroomListDataSource.printCounter()

                                    NostrGlobalDataSource.printCounter()
                                    NostrHomeDataSource.printCounter()
                                    NostrNotificationDataSource.printCounter()

                                    NostrSingleEventDataSource.printCounter()
                                    NostrSearchEventOrUserDataSource.printCounter()
                                    NostrSingleChannelDataSource.printCounter()
                                    NostrSingleUserDataSource.printCounter()
                                    NostrThreadDataSource.printCounter()

                                    NostrUserProfileDataSource.printCounter()
                                    NostrUserProfileFollowersDataSource.printCounter()
                                    NostrUserProfileFollowsDataSource.printCounter()
                                    NostrUserProfileZapsDataSource.printCounter()

                                    println("Connected Relays: " + RelayPool.connectedRelays())

                                    val imageLoader = Coil.imageLoader(context)
                                    println("Image Disk Cache ${(imageLoader.diskCache?.size ?: 0)/(1024*1024)}/${(imageLoader.diskCache?.maxSize ?: 0)/(1024*1024)} MB")
                                    println("Image Memory Cache ${(imageLoader.memoryCache?.size ?: 0)/(1024*1024)}/${(imageLoader.memoryCache?.maxSize ?: 0)/(1024*1024)} MB")

                                    println("Notes: " + LocalCache.notes.size)
                                    println("Users: " + LocalCache.users.size)
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.amethyst),
                                    null,
                                    modifier = Modifier.size(40.dp),
                                    tint = Color.Unspecified
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.End,

                        ) {
                            Row(
                                modifier = Modifier.fillMaxHeight(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${connectedRelaysLiveData ?: "--"}/${availableRelaysLiveData ?: "--"}",
                                    color = if (connectedRelaysLiveData == 0) Color.Red else MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                                    style = MaterialTheme.typography.subtitle1,
                                    modifier = Modifier.clickable(
                                        onClick = {
                                            wantsToEditRelays = true
                                        }
                                    )
                                )
                            }
                        }
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            scaffoldState.drawerState.open()
                        }
                    },
                    modifier = Modifier
                ) {
                    AsyncImageProxy(
                        model = ResizeImage(accountUser.profilePicture(), 34.dp),
                        placeholder = BitmapPainter(RoboHashCache.get(ctx, accountUser.pubkeyHex)),
                        fallback = BitmapPainter(RoboHashCache.get(ctx, accountUser.pubkeyHex)),
                        error = BitmapPainter(RoboHashCache.get(ctx, accountUser.pubkeyHex)),
                        contentDescription = "Profile Image",
                        modifier = Modifier
                            .width(34.dp)
                            .height(34.dp)
                            .clip(shape = CircleShape),
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = { wantsToEditRelays = true }, modifier = Modifier
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_trends),
                        null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.Unspecified
                    )
                }
            }
        )
        Divider(thickness = 0.25.dp)
    }
}

@Composable
fun TopBarWithBackButton(navController: NavHostController) {
    Column() {
        TopAppBar(
            elevation = 0.dp,
            backgroundColor = Color(0xFFFFFF),
            title = {},
            navigationIcon = {
                IconButton(
                    onClick = {
                        navController.popBackStack()
                    },
                    modifier = Modifier
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colors.primary
                    )
                }
            },
            actions = {}
        )
        Divider(thickness = 0.25.dp)
    }
}