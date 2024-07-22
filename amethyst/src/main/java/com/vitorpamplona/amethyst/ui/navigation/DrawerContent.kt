/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.ui.navigation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.actions.mediaServers.MediaServersListView
import com.vitorpamplona.amethyst.ui.actions.relays.AllRelayListView
import com.vitorpamplona.amethyst.ui.components.ClickableText
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.note.LoadStatuses
import com.vitorpamplona.amethyst.ui.qrcode.ShowQRDialog
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountBackupDialog
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ConnectOrbotDialog
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.Font18SP
import com.vitorpamplona.amethyst.ui.theme.IconRowModifier
import com.vitorpamplona.amethyst.ui.theme.IconRowTextModifier
import com.vitorpamplona.amethyst.ui.theme.Size16dp
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size22Modifier
import com.vitorpamplona.amethyst.ui.theme.Size26Modifier
import com.vitorpamplona.amethyst.ui.theme.bannerModifier
import com.vitorpamplona.amethyst.ui.theme.drawerSpacing
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.profileContentHeaderModifier
import com.vitorpamplona.ammolite.relays.RelayPool
import com.vitorpamplona.ammolite.relays.RelayPoolStatus
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.events.ImmutableListOfLists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun DrawerContent(
    nav: (String) -> Unit,
    drawerState: DrawerState,
    openSheet: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    val coroutineScope = rememberCoroutineScope()
    val onClickUser = {
        nav("User/${accountViewModel.userProfile().pubkeyHex}")
        coroutineScope.launch { drawerState.close() }
        Unit
    }

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.background,
        drawerTonalElevation = 0.dp,
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
        ) {
            ProfileContent(
                baseAccountUser = accountViewModel.account.userProfile(),
                modifier = profileContentHeaderModifier,
                accountViewModel,
                onClickUser,
            )

            Column(drawerSpacing) {
                EditStatusBoxes(accountViewModel.account.userProfile(), accountViewModel, drawerState)
            }

            FollowingAndFollowerCounts(accountViewModel.account.userProfile(), onClickUser)

            HorizontalDivider(
                thickness = DividerThickness,
                modifier = Modifier.padding(top = 20.dp),
            )

            ListContent(
                modifier = Modifier.fillMaxWidth(),
                drawerState,
                openSheet,
                accountViewModel,
                nav,
            )

            Spacer(modifier = Modifier.weight(1f))

            BottomContent(
                accountViewModel.account.userProfile(),
                drawerState,
                accountViewModel,
                nav,
            )
        }
    }
}

@Composable
fun ProfileContent(
    baseAccountUser: User,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    onClickUser: () -> Unit,
) {
    val userInfo by baseAccountUser.live().userMetadataInfo.observeAsState()

    ProfileContentTemplate(
        profilePubHex = baseAccountUser.pubkeyHex,
        profileBanner = userInfo?.banner,
        profilePicture = userInfo?.profilePicture(),
        bestDisplayName = userInfo?.bestName(),
        tags = userInfo?.tags,
        modifier = modifier,
        accountViewModel = accountViewModel,
        onClick = onClickUser,
    )
}

@Composable
fun ProfileContentTemplate(
    profilePubHex: HexKey,
    profileBanner: String?,
    profilePicture: String?,
    bestDisplayName: String?,
    tags: ImmutableListOfLists<String>?,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    Box {
        if (profileBanner != null) {
            AsyncImage(
                model = profileBanner,
                contentDescription = stringRes(id = R.string.profile_image),
                contentScale = ContentScale.FillWidth,
                modifier = bannerModifier,
            )
        } else {
            Image(
                painter = painterResource(R.drawable.profile_banner),
                contentDescription = stringRes(R.string.profile_banner),
                contentScale = ContentScale.FillWidth,
                modifier = bannerModifier,
            )
        }

        Column(modifier = modifier) {
            RobohashFallbackAsyncImage(
                robot = profilePubHex,
                model = profilePicture,
                contentDescription = stringRes(id = R.string.profile_image),
                modifier =
                    Modifier
                        .width(100.dp)
                        .height(100.dp)
                        .clip(shape = CircleShape)
                        .border(3.dp, MaterialTheme.colorScheme.background, CircleShape)
                        .clickable(onClick = onClick),
                loadProfilePicture = accountViewModel.settings.showProfilePictures.value,
                loadRobohash = accountViewModel.settings.featureSet != FeatureSetType.PERFORMANCE,
            )

            if (bestDisplayName != null) {
                CreateTextWithEmoji(
                    text = bestDisplayName,
                    tags = tags,
                    modifier =
                        Modifier
                            .padding(top = 7.dp)
                            .clickable(onClick = onClick),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EditStatusBoxes(
    baseAccountUser: User,
    accountViewModel: AccountViewModel,
    drawerState: DrawerState,
) {
    LoadStatuses(user = baseAccountUser, accountViewModel) { statuses ->
        if (statuses.isEmpty()) {
            StatusEditBar(accountViewModel = accountViewModel, drawerState = drawerState)
        } else {
            statuses.forEach {
                val originalStatus by it.live().content.observeAsState()

                StatusEditBar(originalStatus, it.address, accountViewModel, drawerState = drawerState)
            }
        }
    }
}

@Composable
fun StatusEditBar(
    savedStatus: String? = null,
    tag: ATag? = null,
    accountViewModel: AccountViewModel,
    drawerState: DrawerState,
) {
    val focusManager = LocalFocusManager.current

    val currentStatus = remember { mutableStateOf(savedStatus ?: "") }
    val hasChanged = remember { derivedStateOf { currentStatus.value != (savedStatus ?: "") } }
    LaunchedEffect(drawerState.isClosed) {
        if (drawerState.isClosed) {
            focusManager.clearFocus(true)
        }
    }

    OutlinedTextField(
        value = currentStatus.value,
        onValueChange = { currentStatus.value = it },
        label = { Text(text = stringRes(R.string.status_update)) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = stringRes(R.string.status_update),
                color = MaterialTheme.colorScheme.placeholderText,
            )
        },
        keyboardOptions =
            KeyboardOptions.Default.copy(
                imeAction = ImeAction.Send,
                capitalization = KeyboardCapitalization.Sentences,
            ),
        keyboardActions =
            KeyboardActions(
                onSend = {
                    if (tag == null) {
                        accountViewModel.createStatus(currentStatus.value)
                    } else {
                        accountViewModel.updateStatus(tag, currentStatus.value)
                    }

                    focusManager.clearFocus(true)
                },
            ),
        singleLine = true,
        trailingIcon = {
            if (hasChanged.value) {
                SendButton {
                    if (tag == null) {
                        accountViewModel.createStatus(currentStatus.value)
                    } else {
                        accountViewModel.updateStatus(tag, currentStatus.value)
                    }
                    focusManager.clearFocus(true)
                }
            } else {
                if (tag != null) {
                    UserStatusDeleteButton {
                        accountViewModel.deleteStatus(tag)
                        focusManager.clearFocus(true)
                    }
                }
            }
        },
    )
}

@Composable
fun SendButton(onClick: () -> Unit) {
    IconButton(
        modifier = Size26Modifier,
        onClick = onClick,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            null,
            modifier = Size20Modifier,
            tint = MaterialTheme.colorScheme.placeholderText,
        )
    }
}

@Composable
fun UserStatusDeleteButton(onClick: () -> Unit) {
    IconButton(
        modifier = Size26Modifier,
        onClick = onClick,
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            null,
            modifier = Size20Modifier,
            tint = MaterialTheme.colorScheme.placeholderText,
        )
    }
}

@Composable
private fun FollowingAndFollowerCounts(
    baseAccountUser: User,
    onClick: () -> Unit,
) {
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

    Row(
        modifier = drawerSpacing.clickable(onClick = onClick),
    ) {
        Text(
            text = followingCount,
            fontWeight = FontWeight.Bold,
        )

        Text(stringRes(R.string.following))

        Spacer(modifier = DoubleHorzSpacer)

        Text(
            text = followerCount,
            fontWeight = FontWeight.Bold,
        )

        Text(stringRes(R.string.followers))
    }
}

@Composable
fun WatchFollow(
    baseAccountUser: User,
    onReady: (String) -> Unit,
) {
    val accountUserFollowsState by baseAccountUser.live().follows.observeAsState()

    LaunchedEffect(key1 = accountUserFollowsState) {
        launch(Dispatchers.IO) {
            onReady(accountUserFollowsState?.user?.cachedFollowCount()?.toString() ?: "--")
        }
    }
}

@Composable
fun WatchFollower(
    baseAccountUser: User,
    onReady: (String) -> Unit,
) {
    val accountUserFollowersState by baseAccountUser.live().followers.observeAsState()

    LaunchedEffect(key1 = accountUserFollowersState) {
        launch(Dispatchers.IO) {
            onReady(accountUserFollowersState?.user?.cachedFollowerCount()?.toString() ?: "--")
        }
    }
}

@Composable
fun ListContent(
    modifier: Modifier,
    drawerState: DrawerState,
    openSheet: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val route = remember(accountViewModel) { "User/${accountViewModel.userProfile().pubkeyHex}" }

    val coroutineScope = rememberCoroutineScope()
    var wantsToEditRelays by remember { mutableStateOf(false) }
    var editMediaServers by remember { mutableStateOf(false) }

    var backupDialogOpen by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(accountViewModel.account.proxy != null) }
    var disconnectTorDialog by remember { mutableStateOf(false) }
    var conectOrbotDialogOpen by remember { mutableStateOf(false) }
    val proxyPort = remember { mutableStateOf(accountViewModel.account.proxyPort.toString()) }
    val context = LocalContext.current

    Column(modifier) {
        NavigationRow(
            title = stringRes(R.string.profile),
            icon = Route.Profile.icon,
            tint = MaterialTheme.colorScheme.primary,
            nav = nav,
            drawerState = drawerState,
            route = route,
        )

        NavigationRow(
            title = stringRes(R.string.bookmarks),
            icon = Route.Bookmarks.icon,
            tint = MaterialTheme.colorScheme.onBackground,
            nav = nav,
            drawerState = drawerState,
            route = Route.Bookmarks.route,
        )

        NavigationRow(
            title = stringRes(R.string.drafts),
            icon = Route.Drafts.icon,
            tint = MaterialTheme.colorScheme.onBackground,
            nav = nav,
            drawerState = drawerState,
            route = Route.Drafts.route,
        )

        IconRowRelays(
            accountViewModel = accountViewModel,
            onClick = {
                coroutineScope.launch { drawerState.close() }
                wantsToEditRelays = true
            },
        )

        IconRow(
            title = "Media Servers",
            icon = androidx.media3.ui.R.drawable.exo_icon_repeat_all,
            tint = MaterialTheme.colorScheme.onBackground,
            onClick = {
                coroutineScope.launch { drawerState.close() }
                editMediaServers = true
            },
        )

        NavigationRow(
            title = stringRes(R.string.security_filters),
            icon = Route.BlockedUsers.icon,
            tint = MaterialTheme.colorScheme.onBackground,
            nav = nav,
            drawerState = drawerState,
            route = Route.BlockedUsers.route,
        )

        accountViewModel.account.keyPair.privKey?.let {
            IconRow(
                title = stringRes(R.string.backup_keys),
                icon = R.drawable.ic_key,
                tint = MaterialTheme.colorScheme.onBackground,
                onClick = {
                    coroutineScope.launch { drawerState.close() }
                    backupDialogOpen = true
                },
            )
        }

        IconRow(
            title =
                if (checked) {
                    stringRes(R.string.disconnect_from_your_orbot_setup)
                } else {
                    stringRes(R.string.connect_via_tor_short)
                },
            icon = R.drawable.ic_tor,
            tint = MaterialTheme.colorScheme.onBackground,
            onLongClick = {
                coroutineScope.launch { drawerState.close() }
                conectOrbotDialogOpen = true
            },
            onClick = {
                if (checked) {
                    disconnectTorDialog = true
                } else {
                    coroutineScope.launch { drawerState.close() }
                    conectOrbotDialogOpen = true
                }
            },
        )

        NavigationRow(
            title = stringRes(R.string.settings),
            icon = Route.Settings.icon,
            tint = MaterialTheme.colorScheme.onBackground,
            nav = nav,
            drawerState = drawerState,
            route = Route.Settings.route,
        )

        Spacer(modifier = Modifier.weight(1f))

        IconRow(
            title = stringRes(R.string.drawer_accounts),
            icon = R.drawable.manage_accounts,
            tint = MaterialTheme.colorScheme.onBackground,
            onClick = openSheet,
        )
    }

    if (wantsToEditRelays) {
        AllRelayListView({ wantsToEditRelays = false }, accountViewModel = accountViewModel, nav = nav)
    }
    if (editMediaServers) {
        MediaServersListView({ editMediaServers = false }, accountViewModel = accountViewModel, nav = nav)
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
                accountViewModel.enableTor(true, proxyPort)
            },
            onError = {
                accountViewModel.toast(
                    stringRes(context, R.string.could_not_connect_to_tor),
                    it,
                )
            },
            proxyPort,
        )
    }

    if (disconnectTorDialog) {
        AlertDialog(
            title = { Text(text = stringRes(R.string.do_you_really_want_to_disable_tor_title)) },
            text = { Text(text = stringRes(R.string.do_you_really_want_to_disable_tor_text)) },
            onDismissRequest = { disconnectTorDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        disconnectTorDialog = false
                        checked = false
                        accountViewModel.enableTor(false, proxyPort)
                    },
                ) {
                    Text(text = stringRes(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { disconnectTorDialog = false },
                ) {
                    Text(text = stringRes(R.string.no))
                }
            },
        )
    }
}

@Composable
private fun RelayStatus(accountViewModel: AccountViewModel) {
    val connectedRelaysText by RelayPool.statusFlow.collectAsStateWithLifecycle(RelayPoolStatus(0, 0))

    RenderRelayStatus(connectedRelaysText)
}

@Composable
private fun RenderRelayStatus(relayPool: RelayPoolStatus) {
    val text by
        remember(relayPool) { derivedStateOf { "${relayPool.connected}/${relayPool.available}" } }

    val placeHolder = MaterialTheme.colorScheme.placeholderText

    val color by
        remember(relayPool) {
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
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
fun NavigationRow(
    title: String,
    icon: Int,
    tint: Color,
    nav: (String) -> Unit,
    drawerState: DrawerState,
    route: String,
) {
    val coroutineScope = rememberCoroutineScope()
    IconRow(
        title,
        icon,
        tint,
        onClick = {
            nav(route)
            coroutineScope.launch { drawerState.close() }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IconRow(
    title: String,
    icon: Int,
    tint: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
    ) {
        Row(
            modifier = IconRowModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                null,
                modifier = Size22Modifier,
                tint = tint,
            )
            Text(
                modifier = IconRowTextModifier,
                text = title,
                fontSize = Font18SP,
            )
        }
    }
}

@Composable
fun IconRowRelays(
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick() },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 15.dp, horizontal = 25.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.relays),
                null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                modifier = Modifier.padding(start = 16.dp),
                text = stringRes(id = R.string.relay_setup),
                fontSize = 18.sp,
            )

            Spacer(modifier = Modifier.width(Size16dp))

            RelayStatus(accountViewModel = accountViewModel)
        }
    }
}

@Composable
fun BottomContent(
    user: User,
    drawerState: DrawerState,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    // store the dialog open or close state
    var dialogOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier) {
        HorizontalDivider(
            modifier = Modifier.padding(top = 15.dp),
            thickness = DividerThickness,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClickableText(
                text =
                    buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        ) {
                            append("v" + BuildConfig.VERSION_NAME + "-" + BuildConfig.FLAVOR.uppercase())
                        }
                    },
                onClick = {
                    nav("Note/${BuildConfig.RELEASE_NOTES_ID}")
                    coroutineScope.launch { drawerState.close() }
                },
                modifier = Modifier.padding(start = 16.dp),
            )
            Box(modifier = Modifier.weight(1F))
            IconButton(
                onClick = {
                    dialogOpen = true
                    coroutineScope.launch { drawerState.close() }
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_qrcode),
                    contentDescription = stringRes(id = R.string.show_npub_as_a_qr_code),
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    if (dialogOpen) {
        ShowQRDialog(
            user,
            accountViewModel,
            onScan = {
                dialogOpen = false
                coroutineScope.launch { drawerState.close() }
                nav(it)
            },
            onClose = { dialogOpen = false },
        )
    }
}
