package com.vitorpamplona.amethyst.ui.navigation

import android.content.Context
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import coil.Coil
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.GLOBAL_FOLLOWS
import com.vitorpamplona.amethyst.model.KIND3_FOLLOWS
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.NostrAccountDataSource
import com.vitorpamplona.amethyst.service.NostrChannelDataSource
import com.vitorpamplona.amethyst.service.NostrChatroomDataSource
import com.vitorpamplona.amethyst.service.NostrChatroomListDataSource
import com.vitorpamplona.amethyst.service.NostrCommunityDataSource
import com.vitorpamplona.amethyst.service.NostrDiscoveryDataSource
import com.vitorpamplona.amethyst.service.NostrGeohashDataSource
import com.vitorpamplona.amethyst.service.NostrHashtagDataSource
import com.vitorpamplona.amethyst.service.NostrHomeDataSource
import com.vitorpamplona.amethyst.service.NostrSearchEventOrUserDataSource
import com.vitorpamplona.amethyst.service.NostrSingleChannelDataSource
import com.vitorpamplona.amethyst.service.NostrSingleEventDataSource
import com.vitorpamplona.amethyst.service.NostrSingleUserDataSource
import com.vitorpamplona.amethyst.service.NostrThreadDataSource
import com.vitorpamplona.amethyst.service.NostrUserProfileDataSource
import com.vitorpamplona.amethyst.service.NostrVideoDataSource
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.model.PeopleListEvent
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.service.relays.RelayPool
import com.vitorpamplona.amethyst.ui.components.RobohashAsyncImageProxy
import com.vitorpamplona.amethyst.ui.note.SearchIcon
import com.vitorpamplona.amethyst.ui.screen.equalImmutableLists
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.SpinnerSelectionDialog
import com.vitorpamplona.amethyst.ui.theme.BottomTopHeight
import com.vitorpamplona.amethyst.ui.theme.Size22Modifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Composable
fun AppTopBar(
    followLists: FollowListViewModel,
    navEntryState: State<NavBackStackEntry?>,
    scaffoldState: ScaffoldState,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val currentRoute by remember(navEntryState.value) {
        derivedStateOf {
            navEntryState.value?.destination?.route?.substringBefore("?")
        }
    }

    RenderTopRouteBar(currentRoute, followLists, scaffoldState, accountViewModel, nav)
}

@Composable
private fun RenderTopRouteBar(
    currentRoute: String?,
    followLists: FollowListViewModel,
    scaffoldState: ScaffoldState,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    when (currentRoute) {
        Route.Channel.base -> NoTopBar()
        Route.Room.base -> NoTopBar()
        Route.Community.base -> NoTopBar()
        Route.Hashtag.base -> NoTopBar()
        Route.Geohash.base -> NoTopBar()
        // Route.Profile.route -> TopBarWithBackButton(nav)
        Route.Home.base -> HomeTopBar(followLists, scaffoldState, accountViewModel, nav)
        Route.Video.base -> StoriesTopBar(followLists, scaffoldState, accountViewModel, nav)
        Route.Discover.base -> DiscoveryTopBar(followLists, scaffoldState, accountViewModel, nav)
        Route.Notification.base -> NotificationTopBar(followLists, scaffoldState, accountViewModel, nav)
        else -> MainTopBar(scaffoldState, accountViewModel, nav)
    }
}

@Composable
fun NoTopBar() {
}

@Composable
fun StoriesTopBar(followLists: FollowListViewModel, scaffoldState: ScaffoldState, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    GenericTopBar(scaffoldState, accountViewModel, nav) { accountViewModel ->
        val list by accountViewModel.accountLiveData.map {
            it.account.defaultStoriesFollowList
        }.observeAsState(GLOBAL_FOLLOWS)

        FollowList(
            followLists,
            list,
            true
        ) { listName ->
            accountViewModel.account.changeDefaultStoriesFollowList(listName)
        }
    }
}

@Composable
fun HomeTopBar(followLists: FollowListViewModel, scaffoldState: ScaffoldState, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    GenericTopBar(scaffoldState, accountViewModel, nav) { accountViewModel ->
        val list by accountViewModel.accountLiveData.map {
            it.account.defaultHomeFollowList
        }.observeAsState(KIND3_FOLLOWS)

        FollowList(
            followLists,
            list,
            true
        ) { listName ->
            accountViewModel.account.changeDefaultHomeFollowList(listName)
        }
    }
}

@Composable
fun NotificationTopBar(followLists: FollowListViewModel, scaffoldState: ScaffoldState, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    GenericTopBar(scaffoldState, accountViewModel, nav) { accountViewModel ->
        val list by accountViewModel.accountLiveData.map {
            it.account.defaultNotificationFollowList
        }.observeAsState(GLOBAL_FOLLOWS)

        FollowList(
            followLists,
            list,
            true
        ) { listName ->
            accountViewModel.account.changeDefaultNotificationFollowList(listName)
        }
    }
}

@Composable
fun DiscoveryTopBar(followLists: FollowListViewModel, scaffoldState: ScaffoldState, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    GenericTopBar(scaffoldState, accountViewModel, nav) { accountViewModel ->
        val list by accountViewModel.accountLiveData.map {
            it.account.defaultDiscoveryFollowList
        }.observeAsState(GLOBAL_FOLLOWS)

        FollowList(
            followLists,
            list,
            true
        ) { listName ->
            accountViewModel.account.changeDefaultDiscoveryFollowList(listName)
        }
    }
}

@Composable
fun MainTopBar(scaffoldState: ScaffoldState, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    GenericTopBar(scaffoldState, accountViewModel, nav) {
        AmethystIcon()
    }
}

@OptIn(coil.annotation.ExperimentalCoilApi::class)
@Composable
fun GenericTopBar(scaffoldState: ScaffoldState, accountViewModel: AccountViewModel, nav: (String) -> Unit, content: @Composable (AccountViewModel) -> Unit) {
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = BottomTopHeight) {
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
                            content(accountViewModel)
                        }
                    }
                }
            },
            navigationIcon = {
                LoggedInUserPictureDrawer(accountViewModel) {
                    coroutineScope.launch {
                        scaffoldState.drawerState.open()
                    }
                }
            },
            actions = {
                SearchButton() {
                    nav(Route.Search.route)
                }
            }
        )
        Divider(thickness = 0.25.dp)
    }
}

@Composable
private fun SearchButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick
    ) {
        SearchIcon(modifier = Size22Modifier, Color.Unspecified)
    }
}

@Composable
private fun LoggedInUserPictureDrawer(
    accountViewModel: AccountViewModel,
    onClick: () -> Unit
) {
    val accountUserState by accountViewModel.account.userProfile().live().metadata.observeAsState()

    val pubkeyHex = remember { accountUserState?.user?.pubkeyHex ?: "" }
    val profilePicture = remember(accountUserState) { accountUserState?.user?.profilePicture() }

    IconButton(
        onClick = onClick,
        modifier = Modifier
    ) {
        RobohashAsyncImageProxy(
            robot = pubkeyHex,
            model = profilePicture,
            contentDescription = stringResource(id = R.string.profile_image),
            modifier = Modifier
                .width(34.dp)
                .height(34.dp)
                .clip(shape = CircleShape)
        )
    }
}

@Composable
fun FollowList(followListsModel: FollowListViewModel, listName: String, withGlobal: Boolean, onChange: (String) -> Unit) {
    val kind3Follow = Pair(KIND3_FOLLOWS, stringResource(id = R.string.follow_list_kind3follows))
    val globalFollow = Pair(GLOBAL_FOLLOWS, stringResource(id = R.string.follow_list_global))

    val defaultOptions = if (withGlobal) listOf(kind3Follow, globalFollow) else listOf(kind3Follow)

    val followLists by followListsModel.followLists.collectAsState()

    val allLists = remember(followLists) {
        (defaultOptions + followLists)
    }

    val followNames by remember(followLists) {
        derivedStateOf {
            allLists.map { it.second }.toImmutableList()
        }
    }

    SimpleTextSpinner(
        placeholder = allLists.firstOrNull { it.first == listName }?.second ?: "Select an Option",
        options = followNames,
        onSelect = {
            onChange(allLists.getOrNull(it)?.first ?: KIND3_FOLLOWS)
        }
    )
}

@Stable
class FollowListViewModel(val account: Account) : ViewModel() {
    private var _followLists = MutableStateFlow<ImmutableList<Pair<String, String>>>(emptyList<Pair<String, String>>().toPersistentList())
    val followLists = _followLists.asStateFlow()

    fun refresh() {
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        scope.launch {
            refreshFollows()
        }
    }

    private suspend fun refreshFollows() {
        checkNotInMainThread()

        val newFollowLists = LocalCache.addressables.mapNotNull {
            val event = (it.value.event as? PeopleListEvent)
            // Has to have an list
            if (event != null &&
                event.pubKey == account.userProfile().pubkeyHex &&
                (event.tags.size > 1 || event.content.length > 50)
            ) {
                Pair(event.dTag(), event.dTag())
            } else {
                null
            }
        }.sortedBy { it.second }.toImmutableList()

        if (!equalImmutableLists(_followLists.value, newFollowLists)) {
            _followLists.emit(newFollowLists)
        }
    }

    var collectorJob: Job? = null

    init {
        Log.d("Init", "App Top Bar")
        refresh()
        collectorJob = viewModelScope.launch(Dispatchers.IO) {
            LocalCache.live.newEventBundles.collect { newNotes ->
                checkNotInMainThread()
                if (newNotes.any { it.event is PeopleListEvent }) {
                    refresh()
                }
            }
        }
    }

    override fun onCleared() {
        collectorJob?.cancel()
        super.onCleared()
    }

    class Factory(val account: Account) : ViewModelProvider.Factory {
        override fun <FollowListViewModel : ViewModel> create(modelClass: Class<FollowListViewModel>): FollowListViewModel {
            return FollowListViewModel(account) as FollowListViewModel
        }
    }
}

@Composable
fun SimpleTextSpinner(
    placeholder: String,
    options: ImmutableList<String>,
    explainers: ImmutableList<String>? = null,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    var optionsShowing by remember { mutableStateOf(false) }
    var currentText by remember(placeholder) { mutableStateOf(placeholder) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.size(20.dp))
            Text(placeholder)
            Icon(
                imageVector = Icons.Default.ExpandMore,
                null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colors.placeholderText
            )
        }
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
            debugState(context)
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

fun debugState(context: Context) {
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
    NostrCommunityDataSource.printCounter()
    NostrDiscoveryDataSource.printCounter()
    NostrHashtagDataSource.printCounter()
    NostrGeohashDataSource.printCounter()
    NostrHomeDataSource.printCounter()
    NostrSearchEventOrUserDataSource.printCounter()
    NostrSingleChannelDataSource.printCounter()
    NostrSingleEventDataSource.printCounter()
    NostrSingleUserDataSource.printCounter()
    NostrThreadDataSource.printCounter()
    NostrUserProfileDataSource.printCounter()
    NostrVideoDataSource.printCounter()

    Log.d("STATE DUMP", "Connected Relays: " + RelayPool.connectedRelays())

    val imageLoader = Coil.imageLoader(context)
    Log.d("STATE DUMP", "Image Disk Cache ${(imageLoader.diskCache?.size ?: 0) / (1024 * 1024)}/${(imageLoader.diskCache?.maxSize ?: 0) / (1024 * 1024)} MB")
    Log.d("STATE DUMP", "Image Memory Cache ${(imageLoader.memoryCache?.size ?: 0) / (1024 * 1024)}/${(imageLoader.memoryCache?.maxSize ?: 0) / (1024 * 1024)} MB")

    Log.d("STATE DUMP", "Notes: " + LocalCache.notes.filter { it.value.event != null }.size + "/" + LocalCache.notes.size)
    Log.d("STATE DUMP", "Addressables: " + LocalCache.addressables.filter { it.value.event != null }.size + "/" + LocalCache.addressables.size)
    Log.d("STATE DUMP", "Users: " + LocalCache.users.filter { it.value.info?.latestMetadata != null }.size + "/" + LocalCache.users.size)

    LocalCache.notes.values.groupBy { it.event?.kind() }.forEach {
        Log.d("STATE DUMP", "Kind ${it.key}: \t${it.value.size} elements ")
    }
    LocalCache.addressables.values.groupBy { it.event?.kind() }.forEach {
        Log.d("STATE DUMP", "Kind ${it.key}: \t${it.value.size} elements ")
    }
}
