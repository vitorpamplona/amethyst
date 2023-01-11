package com.vitorpamplona.amethyst.ui.navigation

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.ui.screen.RelayPoolViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.launch

@Composable
fun AppTopBar(navController: NavHostController, scaffoldState: ScaffoldState, accountViewModel: AccountViewModel) {
    when (currentRoute(navController)) {
        Route.Profile.route,
        Route.Lists.route,
        Route.Topics.route,
        Route.Bookmarks.route,
        Route.Moments.route -> TopBarWithBackButton(navController)
        else -> MainTopBar(scaffoldState, accountViewModel)
    }
}

@Composable
fun MainTopBar(scaffoldState: ScaffoldState, accountViewModel: AccountViewModel) {
    val accountUserState by accountViewModel.userLiveData.observeAsState()
    val accountUser = accountUserState?.user

    val relayViewModel: RelayPoolViewModel = viewModel { RelayPoolViewModel() }
    val relayPoolLiveData by relayViewModel.relayPoolLiveData.observeAsState()

    val coroutineScope = rememberCoroutineScope()

    Column() {
        TopAppBar(
            elevation = 0.dp,
            backgroundColor = Color(0xFFFFFF),
            title = {
                Column(
                    modifier = Modifier
                        .padding(start = 22.dp, end = 0.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = {
                            Client.subscriptions.map { "${it.key} ${it.value.joinToString { it.toJson() }}" }.forEach {
                                Log.d("CURRENT FILTERS", it)
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_amethyst),
                            null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colors.primary
                        )
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
                    AsyncImage(
                        model = accountUser?.profilePicture() ?: "https://robohash.org/ohno.png",
                        contentDescription = "Profile Image",
                        modifier = Modifier
                            .width(34.dp)
                            .clip(shape = CircleShape),
                    )
                }
            },
            actions = {
                Text(
                    relayPoolLiveData ?: "--/--",
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )

                IconButton(
                    onClick = {}, modifier = Modifier
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