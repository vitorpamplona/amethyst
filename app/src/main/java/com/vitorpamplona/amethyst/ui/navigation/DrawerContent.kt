package com.vitorpamplona.amethyst.ui.navigation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ServiceManager
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.HttpClient
import com.vitorpamplona.amethyst.service.relays.RelayPool
import com.vitorpamplona.amethyst.service.relays.RelayPoolStatus
import com.vitorpamplona.amethyst.ui.actions.NewRelayListView
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.RobohashAsyncImageProxy
import com.vitorpamplona.amethyst.ui.note.LoadStatuses
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountBackupDialog
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ConnectOrbotDialog
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size16dp
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size26Modifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.events.toImmutableListOfLists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun DrawerContent(
    nav: (String) -> Unit,
    drawerState: DrawerState,
    openSheet: () -> Unit,
    accountViewModel: AccountViewModel
) {
    val automaticallyShowProfilePicture = remember {
        accountViewModel.settings.showProfilePictures.value
    }

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.background,
        drawerTonalElevation = 0.dp
    ) {
        Column() {
            ProfileContent(
                accountViewModel.account.userProfile(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 25.dp)
                    .padding(top = 70.dp),
                drawerState,
                accountViewModel,
                nav
            )
            Divider(
                thickness = DividerThickness,
                modifier = Modifier.padding(top = 20.dp)
            )
            ListContent(
                nav,
                drawerState,
                openSheet,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                accountViewModel
            )

            BottomContent(
                accountViewModel.account.userProfile(),
                drawerState,
                automaticallyShowProfilePicture,
                nav
            )
        }
    }
}

@Composable
fun ProfileContent(
    baseAccountUser: User,
    modifier: Modifier = Modifier,
    drawerState: DrawerState,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    val accountUserState by baseAccountUser.live().metadata.observeAsState()

    val profilePubHex = remember(accountUserState) { accountUserState?.user?.pubkeyHex } ?: return

    val profileBanner = remember(accountUserState) { accountUserState?.user?.info?.banner?.ifBlank { null } }
    val profilePicture = remember(accountUserState) { accountUserState?.user?.profilePicture() }
    // val bestUserName = remember(accountUserState) { accountUserState?.user?.bestUsername() }
    val bestDisplayName = remember(accountUserState) { accountUserState?.user?.toBestDisplayName() }
    val tags = remember(accountUserState) { accountUserState?.user?.info?.latestMetadata?.tags?.toImmutableListOfLists() }
    val route = remember(accountUserState) { "User/${accountUserState?.user?.pubkeyHex}" }

    val automaticallyShowProfilePicture = remember {
        accountViewModel.settings.showProfilePictures.value
    }

    Box {
        if (profileBanner != null) {
            AsyncImage(
                model = profileBanner,
                contentDescription = stringResource(id = R.string.profile_image),
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )
        } else {
            Image(
                painter = painterResource(R.drawable.profile_banner),
                contentDescription = stringResource(R.string.profile_banner),
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )
        }

        Column(modifier = modifier) {
            RobohashAsyncImageProxy(
                robot = profilePubHex,
                model = profilePicture,
                contentDescription = stringResource(id = R.string.profile_image),
                modifier = Modifier
                    .width(100.dp)
                    .height(100.dp)
                    .clip(shape = CircleShape)
                    .border(3.dp, MaterialTheme.colorScheme.background, CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(onClick = {
                        nav(route)
                        coroutineScope.launch {
                            drawerState.close()
                        }
                    }),
                loadProfilePicture = automaticallyShowProfilePicture
            )

            if (bestDisplayName != null) {
                CreateTextWithEmoji(
                    text = bestDisplayName,
                    tags = tags,
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .clickable(onClick = {
                            nav(route)
                            coroutineScope.launch {
                                drawerState.close()
                            }
                        }),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(Modifier.padding(top = Size10dp)) {
                EditStatusBoxes(baseAccountUser, accountViewModel)
            }

            Row(
                modifier = Modifier
                    .padding(top = Size10dp)
                    .clickable(onClick = {
                        nav(route)
                        coroutineScope.launch {
                            drawerState.close()
                        }
                    })
            ) {
                FollowingAndFollowerCounts(baseAccountUser)
            }
        }
    }
}

@Composable
private fun EditStatusBoxes(baseAccountUser: User, accountViewModel: AccountViewModel) {
    val focusManager = LocalFocusManager.current

    LoadStatuses(user = baseAccountUser, accountViewModel) { statuses ->
        if (statuses.isEmpty()) {
            val currentStatus = remember {
                mutableStateOf("")
            }
            val hasChanged by remember {
                derivedStateOf {
                    currentStatus.value != ""
                }
            }

            OutlinedTextField(
                value = currentStatus.value,
                onValueChange = { currentStatus.value = it },
                label = { Text(text = stringResource(R.string.status_update)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = stringResource(R.string.status_update),
                        color = MaterialTheme.colorScheme.placeholderText
                    )
                },
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Send,
                    capitalization = KeyboardCapitalization.Sentences
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        accountViewModel.createStatus(currentStatus.value)
                        focusManager.clearFocus(true)
                    }
                ),
                singleLine = true,
                trailingIcon = {
                    if (hasChanged) {
                        SendButton() {
                            accountViewModel.createStatus(currentStatus.value)
                            focusManager.clearFocus(true)
                        }
                    }
                }
            )
        } else {
            statuses.forEach {
                val originalStatus by it.live().content.observeAsState("")

                val thisStatus = remember {
                    mutableStateOf(originalStatus)
                }
                val hasChanged by remember {
                    derivedStateOf {
                        thisStatus.value != originalStatus
                    }
                }

                OutlinedTextField(
                    value = thisStatus.value,
                    onValueChange = { thisStatus.value = it },
                    label = { Text(text = stringResource(R.string.status_update)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.status_update),
                            color = MaterialTheme.colorScheme.placeholderText
                        )
                    },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Send,
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            accountViewModel.updateStatus(it, thisStatus.value)
                            focusManager.clearFocus(true)
                        }
                    ),
                    singleLine = true,
                    trailingIcon = {
                        if (hasChanged) {
                            SendButton() {
                                accountViewModel.updateStatus(it, thisStatus.value)
                                focusManager.clearFocus(true)
                            }
                        } else {
                            UserStatusDeleteButton() {
                                accountViewModel.deleteStatus(it)
                                focusManager.clearFocus(true)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SendButton(onClick: () -> Unit) {
    IconButton(
        modifier = Size26Modifier,
        onClick = onClick
    ) {
        Icon(
            imageVector = Icons.Default.Send,
            null,
            modifier = Size20Modifier,
            tint = MaterialTheme.colorScheme.placeholderText
        )
    }
}

@Composable
fun UserStatusDeleteButton(onClick: () -> Unit) {
    IconButton(
        modifier = Size26Modifier,
        onClick = onClick
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            null,
            modifier = Size20Modifier,
            tint = MaterialTheme.colorScheme.placeholderText
        )
    }
}

@Composable
private fun FollowingAndFollowerCounts(baseAccountUser: User) {
    var followingCount by remember { mutableStateOf("--") }
    var followerCount by remember { mutableStateOf("--") }

    WatchFollow(baseAccountUser = baseAccountUser) { newFollowing ->
        if (followingCount != newFollowing) {
            followingCount = newFollowing
        }
    }

    WatchFollower(baseAccountUser = baseAccountUser) { newFollower ->
        if (followerCount != newFollower) {
            followerCount = newFollower
        }
    }

    Text(
        text = followingCount,
        fontWeight = FontWeight.Bold
    )

    Text(stringResource(R.string.following))

    Spacer(modifier = DoubleHorzSpacer)

    Text(
        text = followerCount,
        fontWeight = FontWeight.Bold
    )

    Text(stringResource(R.string.followers))
}

@Composable
fun WatchFollow(baseAccountUser: User, onReady: (String) -> Unit) {
    val accountUserFollowsState by baseAccountUser.live().follows.observeAsState()

    LaunchedEffect(key1 = accountUserFollowsState) {
        launch(Dispatchers.IO) {
            onReady(accountUserFollowsState?.user?.cachedFollowCount()?.toString() ?: "--")
        }
    }
}

@Composable
fun WatchFollower(baseAccountUser: User, onReady: (String) -> Unit) {
    val accountUserFollowersState by baseAccountUser.live().followers.observeAsState()

    LaunchedEffect(key1 = accountUserFollowersState) {
        launch(Dispatchers.IO) {
            onReady(accountUserFollowersState?.user?.cachedFollowerCount()?.toString() ?: "--")
        }
    }
}

@Composable
fun ListContent(
    nav: (String) -> Unit,
    drawerState: DrawerState,
    openSheet: () -> Unit,
    modifier: Modifier,
    accountViewModel: AccountViewModel
) {
    val route = remember(accountViewModel) {
        "User/${accountViewModel.userProfile().pubkeyHex}"
    }

    val coroutineScope = rememberCoroutineScope()
    var wantsToEditRelays by remember {
        mutableStateOf(false)
    }

    var backupDialogOpen by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(accountViewModel.account.proxy != null) }
    var disconnectTorDialog by remember { mutableStateOf(false) }
    var conectOrbotDialogOpen by remember { mutableStateOf(false) }
    val proxyPort = remember { mutableStateOf(accountViewModel.account.proxyPort.toString()) }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
    ) {
        NavigationRow(
            title = stringResource(R.string.profile),
            icon = Route.Profile.icon,
            tint = MaterialTheme.colorScheme.primary,
            nav = nav,
            drawerState = drawerState,
            route = route
        )

        NavigationRow(
            title = stringResource(R.string.bookmarks),
            icon = Route.Bookmarks.icon,
            tint = MaterialTheme.colorScheme.onBackground,
            nav = nav,
            drawerState = drawerState,
            route = Route.Bookmarks.route
        )

        IconRowRelays(
            accountViewModel = accountViewModel,
            onClick = {
                coroutineScope.launch {
                    drawerState.close()
                }
                wantsToEditRelays = true
            }
        )

        NavigationRow(
            title = stringResource(R.string.security_filters),
            icon = Route.BlockedUsers.icon,
            tint = MaterialTheme.colorScheme.onBackground,
            nav = nav,
            drawerState = drawerState,
            route = Route.BlockedUsers.route
        )

        accountViewModel.account.keyPair.privKey?.let {
            IconRow(
                title = stringResource(R.string.backup_keys),
                icon = R.drawable.ic_key,
                tint = MaterialTheme.colorScheme.onBackground,
                onClick = {
                    coroutineScope.launch {
                        drawerState.close()
                    }
                    backupDialogOpen = true
                }
            )
        }

        val textTorProxy = if (checked) stringResource(R.string.disconnect_from_your_orbot_setup) else stringResource(R.string.connect_via_tor_short)
        IconRow(
            title = textTorProxy,
            icon = R.drawable.ic_tor,
            tint = MaterialTheme.colorScheme.onBackground,
            onLongClick = {
                coroutineScope.launch {
                    drawerState.close()
                }
                conectOrbotDialogOpen = true
            },
            onClick = {
                if (checked) {
                    disconnectTorDialog = true
                } else {
                    coroutineScope.launch {
                        drawerState.close()
                    }
                    conectOrbotDialogOpen = true
                }
            }
        )

        NavigationRow(
            title = stringResource(R.string.settings),
            icon = Route.Settings.icon,
            tint = MaterialTheme.colorScheme.onBackground,
            nav = nav,
            drawerState = drawerState,
            route = Route.Settings.route
        )

        Spacer(modifier = Modifier.weight(1f))

        IconRow(
            title = stringResource(R.string.drawer_accounts),
            icon = R.drawable.manage_accounts,
            tint = MaterialTheme.colorScheme.onBackground,
            onClick = openSheet
        )
    }

    if (wantsToEditRelays) {
        NewRelayListView({ wantsToEditRelays = false }, accountViewModel, nav = nav)
    }
    if (backupDialogOpen) {
        AccountBackupDialog(accountViewModel, onClose = { backupDialogOpen = false })
    }
    if (conectOrbotDialogOpen) {
        ConnectOrbotDialog(
            onClose = { conectOrbotDialogOpen = false },
            onPost = {
                conectOrbotDialogOpen = false
                disconnectTorDialog = false
                checked = true
                coroutineScope.launch(Dispatchers.IO) {
                    enableTor(accountViewModel.account, true, proxyPort)
                }
            },
            onError = {
                accountViewModel.toast(
                    context.getString(R.string.could_not_connect_to_tor),
                    it
                )
            },
            proxyPort
        )
    }

    if (disconnectTorDialog) {
        AlertDialog(
            title = {
                Text(text = stringResource(R.string.do_you_really_want_to_disable_tor_title))
            },
            text = {
                Text(text = stringResource(R.string.do_you_really_want_to_disable_tor_text))
            },
            onDismissRequest = {
                disconnectTorDialog = false
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        disconnectTorDialog = false
                        checked = false
                        coroutineScope.launch(Dispatchers.IO) {
                            enableTor(
                                accountViewModel.account,
                                false,
                                proxyPort
                            )
                        }
                    }
                ) {
                    Text(text = stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        disconnectTorDialog = false
                    }
                ) {
                    Text(text = stringResource(R.string.no))
                }
            }
        )
    }
}

private suspend fun enableTor(
    account: Account,
    checked: Boolean,
    portNumber: MutableState<String>
) {
    account.proxyPort = portNumber.value.toInt()
    account.proxy = HttpClient.initProxy(checked, "127.0.0.1", account.proxyPort)
    LocalPreferences.saveToEncryptedStorage(account)
    ServiceManager.pause()
    ServiceManager.start()
}

@Composable
private fun RelayStatus(accountViewModel: AccountViewModel) {
    val connectedRelaysText by RelayPool.statusFlow.collectAsStateWithLifecycle(RelayPoolStatus(0, 0))

    RenderRelayStatus(connectedRelaysText)
}

@Composable
private fun RenderRelayStatus(
    relayPool: RelayPoolStatus
) {
    val text by remember(relayPool) {
        derivedStateOf {
            "${relayPool.connected}/${relayPool.available}"
        }
    }

    val placeHolder = MaterialTheme.colorScheme.placeholderText

    val color by remember(relayPool) {
        derivedStateOf {
            if (relayPool.isConnected) {
                placeHolder
            } else {
                Color.Red
            }
        }
    }

    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.titleMedium
    )
}

@Composable
fun NavigationRow(
    title: String,
    icon: Int,
    tint: Color,
    nav: (String) -> Unit,
    drawerState: DrawerState,
    route: String
) {
    val coroutineScope = rememberCoroutineScope()
    IconRow(title, icon, tint, onClick = {
        nav(route)
        coroutineScope.launch {
            drawerState.close()
        }
    })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IconRow(title: String, icon: Int, tint: Color, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 15.dp, horizontal = 25.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(icon),
                null,
                modifier = Modifier.size(22.dp),
                tint = tint
            )
            Text(
                modifier = Modifier.padding(start = 16.dp),
                text = title,
                fontSize = 18.sp
            )
        }
    }
}

@Composable
fun IconRowRelays(accountViewModel: AccountViewModel, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 15.dp, horizontal = 25.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.relays),
                null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )

            Text(
                modifier = Modifier.padding(start = 16.dp),
                text = stringResource(id = R.string.relay_setup),
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.width(Size16dp))

            RelayStatus(accountViewModel = accountViewModel)
        }
    }
}

@Composable
fun BottomContent(user: User, drawerState: DrawerState, loadProfilePicture: Boolean, nav: (String) -> Unit) {
    val coroutineScope = rememberCoroutineScope()

    // store the dialog open or close state
    var dialogOpen by remember {
        mutableStateOf(false)
    }

    Column(modifier = Modifier) {
        Divider(
            modifier = Modifier.padding(top = 15.dp),
            thickness = DividerThickness
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.padding(start = 16.dp),
                text = "v" + BuildConfig.VERSION_NAME + "-" + BuildConfig.FLAVOR.uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            /*
            IconButton(
                onClick = {
                    when (AppCompatDelegate.getDefaultNightMode()) {
                        AppCompatDelegate.MODE_NIGHT_NO -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                        AppCompatDelegate.MODE_NIGHT_YES -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                        else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    }
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_theme),
                    null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }*/
            Box(modifier = Modifier.weight(1F))
            IconButton(onClick = {
                dialogOpen = true
                coroutineScope.launch {
                    drawerState.close()
                }
            }) {
                Icon(
                    painter = painterResource(R.drawable.ic_qrcode),
                    null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (dialogOpen) {
        ShowQRDialog(
            user,
            loadProfilePicture = loadProfilePicture,
            onScan = {
                dialogOpen = false
                coroutineScope.launch {
                    drawerState.close()
                }
                nav(it)
            },
            onClose = { dialogOpen = false }
        )
    }
}
