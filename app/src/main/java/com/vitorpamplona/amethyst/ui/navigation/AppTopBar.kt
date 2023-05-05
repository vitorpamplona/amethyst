package com.vitorpamplona.amethyst.ui.navigation

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.Coil
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.GLOBAL_FOLLOWS
import com.vitorpamplona.amethyst.model.KIND3_FOLLOWS
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.NostrAccountDataSource
import com.vitorpamplona.amethyst.service.NostrChannelDataSource
import com.vitorpamplona.amethyst.service.NostrChatroomDataSource
import com.vitorpamplona.amethyst.service.NostrChatroomListDataSource
import com.vitorpamplona.amethyst.service.NostrGlobalDataSource
import com.vitorpamplona.amethyst.service.NostrHashtagDataSource
import com.vitorpamplona.amethyst.service.NostrHomeDataSource
import com.vitorpamplona.amethyst.service.NostrSearchEventOrUserDataSource
import com.vitorpamplona.amethyst.service.NostrSingleChannelDataSource
import com.vitorpamplona.amethyst.service.NostrSingleEventDataSource
import com.vitorpamplona.amethyst.service.NostrSingleUserDataSource
import com.vitorpamplona.amethyst.service.NostrThreadDataSource
import com.vitorpamplona.amethyst.service.NostrUserProfileDataSource
import com.vitorpamplona.amethyst.service.model.PeopleListEvent
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.service.relays.RelayPool
import com.vitorpamplona.amethyst.ui.actions.NewRelayListView
import com.vitorpamplona.amethyst.ui.components.ResizeImage
import com.vitorpamplona.amethyst.ui.components.RobohashAsyncImageProxy
import com.vitorpamplona.amethyst.ui.screen.RelayPoolViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.SpinnerSelectionDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppTopBar(navController: NavHostController, scaffoldState: ScaffoldState, accountViewModel: AccountViewModel) {
    when (currentRoute(navController)?.substringBefore("?")) {
        // Route.Profile.route -> TopBarWithBackButton(navController)
        Route.Home.base -> HomeTopBar(scaffoldState, accountViewModel)
        Route.Video.base -> StoriesTopBar(scaffoldState, accountViewModel)
        else -> MainTopBar(scaffoldState, accountViewModel)
    }
}

@Composable
fun StoriesTopBar(scaffoldState: ScaffoldState, accountViewModel: AccountViewModel) {
    GenericTopBar(scaffoldState, accountViewModel) { account ->
        FollowList(account.defaultStoriesFollowList, account.userProfile(), true) { listName ->
            account.changeDefaultStoriesFollowList(listName)
        }
    }
}

@Composable
fun HomeTopBar(scaffoldState: ScaffoldState, accountViewModel: AccountViewModel) {
    GenericTopBar(scaffoldState, accountViewModel) { account ->
        FollowList(account.defaultHomeFollowList, account.userProfile(), false) { listName ->
            account.changeDefaultHomeFollowList(listName)
        }
    }
}

@Composable
fun MainTopBar(scaffoldState: ScaffoldState, accountViewModel: AccountViewModel) {
    GenericTopBar(scaffoldState, accountViewModel) {
        AmethystIcon()
    }
}

@OptIn(coil.annotation.ExperimentalCoilApi::class)
@Composable
fun GenericTopBar(scaffoldState: ScaffoldState, accountViewModel: AccountViewModel, content: @Composable (Account) -> Unit) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    val relayViewModel: RelayPoolViewModel = viewModel { RelayPoolViewModel() }
    val connectedRelaysLiveData by relayViewModel.connectedRelaysLiveData.observeAsState()
    val availableRelaysLiveData by relayViewModel.availableRelaysLiveData.observeAsState()

    val coroutineScope = rememberCoroutineScope()

    var wantsToEditRelays by remember {
        mutableStateOf(false)
    }

    if (wantsToEditRelays) {
        NewRelayListView({ wantsToEditRelays = false }, account)
    }

    Column() {
        TopAppBar(
            elevation = 0.dp,
            backgroundColor = MaterialTheme.colors.surface,
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
                            content(account)
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.End

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
                LoggedInUserPictureDrawer(account) {
                    coroutineScope.launch {
                        scaffoldState.drawerState.open()
                    }
                }
            },
            actions = {
                IconButton(
                    onClick = { wantsToEditRelays = true },
                    modifier = Modifier
                ) {
                    Icon(
                        painter = painterResource(R.drawable.relays),
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
private fun LoggedInUserPictureDrawer(
    account: Account,
    onClick: () -> Unit
) {
    val accountUserState by account.userProfile().live().metadata.observeAsState()
    val accountUser = accountUserState?.user ?: return

    IconButton(
        onClick = onClick,
        modifier = Modifier
    ) {
        RobohashAsyncImageProxy(
            robot = accountUser.pubkeyHex,
            model = ResizeImage(accountUser.profilePicture(), 34.dp),
            contentDescription = stringResource(id = R.string.profile_image),
            modifier = Modifier
                .width(34.dp)
                .height(34.dp)
                .clip(shape = CircleShape)
        )
    }
}

@Composable
fun FollowList(listName: String, loggedIn: User, withGlobal: Boolean, onChange: (String) -> Unit) {
    // Notification
    val dbState = LocalCache.live.observeAsState()
    val db = dbState.value ?: return

    val kind3Follow = Pair(KIND3_FOLLOWS, stringResource(id = R.string.follow_list_kind3follows))
    val globalFollow = Pair(GLOBAL_FOLLOWS, stringResource(id = R.string.follow_list_global))

    val defaultOptions = if (withGlobal) listOf(kind3Follow, globalFollow) else listOf(kind3Follow)

    var followLists by remember { mutableStateOf(defaultOptions) }
    val followNames = remember { derivedStateOf { followLists.map { it.second } } }

    LaunchedEffect(key1 = db) {
        withContext(Dispatchers.IO) {
            followLists = defaultOptions + LocalCache.addressables.mapNotNull {
                val event = (it.value.event as? PeopleListEvent)
                // Has to have an list
                if (event != null && event.pubKey == loggedIn.pubkeyHex && (event.tags.size > 1 || event.content.length > 50)) {
                    Pair(event.dTag(), event.dTag())
                } else {
                    null
                }
            }.sortedBy { it.second }
        }
    }

    SimpleTextSpinner(
        placeholder = followLists.firstOrNull { it.first == listName }?.second ?: "Select an Option",
        options = followNames.value,
        onSelect = {
            onChange(followLists.getOrNull(it)?.first ?: KIND3_FOLLOWS)
        }
    )
}

@Composable
fun SimpleTextSpinner(
    placeholder: String,
    options: List<String>,
    explainers: List<String>? = null,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    var optionsShowing by remember { mutableStateOf(false) }
    var currentText by remember { mutableStateOf(placeholder) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(currentText)
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) {
                    optionsShowing = true
                }
        )
    }

    if (optionsShowing) {
        options.isNotEmpty().also {
            SpinnerSelectionDialog(options = options, explainers = explainers, onDismiss = { optionsShowing = false }) {
                currentText = options[it]
                optionsShowing = false
                onSelect(it)
            }
        }
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

@Composable
fun AmethystIcon() {
    val context = LocalContext.current

    IconButton(
        onClick = {
            Client.allSubscriptions().map {
                "$it ${
                Client.getSubscriptionFilters(it)
                    .joinToString { it.filter.toJson() }
                }"
            }.forEach {
                Log.d("STATE DUMP", it)
            }

            NostrAccountDataSource.printCounter()
            NostrChannelDataSource.printCounter()
            NostrChatroomDataSource.printCounter()
            NostrChatroomListDataSource.printCounter()
            NostrGlobalDataSource.printCounter()
            NostrHashtagDataSource.printCounter()
            NostrHomeDataSource.printCounter()
            NostrSearchEventOrUserDataSource.printCounter()
            NostrSingleChannelDataSource.printCounter()
            NostrSingleEventDataSource.printCounter()
            NostrSingleUserDataSource.printCounter()
            NostrThreadDataSource.printCounter()
            NostrUserProfileDataSource.printCounter()

            Log.d("STATE DUMP", "Connected Relays: " + RelayPool.connectedRelays())

            val imageLoader = Coil.imageLoader(context)
            Log.d("STATE DUMP", "Image Disk Cache ${(imageLoader.diskCache?.size ?: 0) / (1024 * 1024)}/${(imageLoader.diskCache?.maxSize ?: 0) / (1024 * 1024)} MB")
            Log.d("STATE DUMP", "Image Memory Cache ${(imageLoader.memoryCache?.size ?: 0) / (1024 * 1024)}/${(imageLoader.memoryCache?.maxSize ?: 0) / (1024 * 1024)} MB")

            Log.d("STATE DUMP", "Notes: " + LocalCache.notes.filter { it.value.event != null }.size + "/" + LocalCache.notes.size)
            Log.d("STATE DUMP", "Users: " + LocalCache.users.filter { it.value.info?.latestMetadata != null }.size + "/" + LocalCache.users.size)
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
