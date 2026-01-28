/*
 * Copyright (c) 2025 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.navigation.drawer

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNote
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserContactCardsFollowerCount
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserInfo
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserStatuses
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.painterRes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.keyBackup.AccountBackupDialog
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.Font18SP
import com.vitorpamplona.amethyst.ui.theme.IconRowModifier
import com.vitorpamplona.amethyst.ui.theme.IconRowTextModifier
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size22Modifier
import com.vitorpamplona.amethyst.ui.theme.Size26Modifier
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.Width16Space
import com.vitorpamplona.amethyst.ui.theme.bannerModifier
import com.vitorpamplona.amethyst.ui.theme.drawerSpacing
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.profileContentHeaderModifier
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Composable
fun DrawerContent(
    nav: INav,
    openSheet: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    val onClickUser = {
        nav.nav(routeFor(accountViewModel.userProfile()))
        nav.closeDrawer()
    }

    ModalDrawerSheet(
        windowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Start),
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
                EditStatusBoxes(accountViewModel.account.userProfile(), accountViewModel, nav)
            }

            FollowingAndFollowerCounts(accountViewModel.account, accountViewModel, onClickUser)

            HorizontalDivider(
                thickness = DividerThickness,
                modifier = Modifier.padding(top = 20.dp),
            )

            Spacer(modifier = StdHorzSpacer)

            ListContent(
                modifier = Modifier.fillMaxWidth(),
                openSheet,
                accountViewModel,
                nav,
            )

            Spacer(modifier = Modifier.weight(1f))

            BottomContent(
                accountViewModel.account.userProfile(),
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
    val userInfo by observeUserInfo(baseAccountUser, accountViewModel)

    ProfileContentTemplate(
        profilePubHex = baseAccountUser.pubkeyHex,
        profileBanner = userInfo?.info?.banner,
        profilePicture = userInfo?.info?.profilePicture(),
        bestDisplayName = userInfo?.info?.bestName(),
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
                painter = painterRes(R.drawable.profile_banner, 3),
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
                        .border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                        .clickable(onClick = onClick),
                loadProfilePicture = accountViewModel.settings.showProfilePictures(),
                loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
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
    nav: INav,
) {
    val statuses by observeUserStatuses(baseAccountUser, accountViewModel)

    if (statuses.isEmpty()) {
        StatusEditBar(accountViewModel = accountViewModel, nav = nav)
    } else {
        statuses.forEach {
            val noteStatus by observeNote(it, accountViewModel)

            StatusEditBar(noteStatus.note.event?.content, it.address, accountViewModel, nav)
        }
    }
}

@Composable
fun StatusEditBar(
    savedStatus: String? = null,
    address: Address? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val focusManager = LocalFocusManager.current

    val currentStatus = remember { mutableStateOf(savedStatus ?: "") }
    val hasChanged = remember { derivedStateOf { currentStatus.value != (savedStatus ?: "") } }
    LaunchedEffect(nav.drawerState.isClosed) {
        if (nav.drawerState.isClosed) {
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
                    if (address == null) {
                        accountViewModel.createStatus(currentStatus.value)
                    } else {
                        accountViewModel.updateStatus(address, currentStatus.value)
                    }

                    focusManager.clearFocus(true)
                },
            ),
        singleLine = true,
        trailingIcon = {
            if (hasChanged.value) {
                SendButton {
                    if (address == null) {
                        accountViewModel.createStatus(currentStatus.value)
                    } else {
                        accountViewModel.updateStatus(address, currentStatus.value)
                    }
                    focusManager.clearFocus(true)
                }
            } else {
                if (address != null) {
                    UserStatusDeleteButton {
                        accountViewModel.deleteStatus(address)
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
    baseAccountUser: Account,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    Row(
        modifier = drawerSpacing.clickable(onClick = onClick),
    ) {
        DisplayFollowingCount(baseAccountUser)

        Text(stringRes(R.string.following))

        Spacer(modifier = DoubleHorzSpacer)

        DisplayFollowerCount(baseAccountUser, accountViewModel)

        Text(stringRes(R.string.followers))
    }
}

@Composable
fun DisplayFollowingCount(baseAccountUser: Account) {
    val followingCount by baseAccountUser.kind3FollowList.flow.collectAsStateWithLifecycle()

    Text(
        text = followingCount.authors.size.toString(),
        fontWeight = FontWeight.Bold,
    )
}

@Composable
fun DisplayFollowerCount(
    baseAccountUser: Account,
    accountViewModel: AccountViewModel,
) {
    val followerCount by observeUserContactCardsFollowerCount(baseAccountUser.userProfile(), accountViewModel)

    Text(
        text = followerCount,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
fun ListContent(
    modifier: Modifier,
    openSheet: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var backupDialogOpen by remember { mutableStateOf(false) }

    Column(modifier) {
        NavigationRow(
            title = R.string.profile,
            icon = Icons.Default.AccountCircle,
            tint = MaterialTheme.colorScheme.primary,
            nav = nav,
            route = remember { Route.Profile(accountViewModel.userProfile().pubkeyHex) },
        )

        NavigationRow(
            title = R.string.my_lists,
            icon = ImageVector.vectorResource(R.drawable.format_list_bulleted_type),
            tint = MaterialTheme.colorScheme.onBackground,
            nav = nav,
            route = Route.Lists,
        )

        NavigationRow(
            title = R.string.bookmarks,
            icon = Icons.Outlined.CollectionsBookmark,
            tint = MaterialTheme.colorScheme.onBackground,
            nav = nav,
            route = Route.BookmarkGroups,
        )

        NavigationRow(
            title = R.string.drafts,
            icon = Icons.Outlined.Drafts,
            tint = MaterialTheme.colorScheme.onBackground,
            nav = nav,
            route = Route.Drafts,
        )

        NavigationRow(
            title = R.string.route_chess,
            icon = R.drawable.ic_chess,
            iconReference = 1,
            tint = MaterialTheme.colorScheme.onBackground,
            nav = nav,
            route = Route.Chess,
        )

        IconRowRelays(
            accountViewModel = accountViewModel,
            onClick = {
                nav.closeDrawer()
                nav.nav(Route.EditRelays)
            },
        )

        IconRow(
            title = R.string.media_servers,
            icon = Icons.Outlined.CloudUpload,
            tint = MaterialTheme.colorScheme.onBackground,
            onClick = {
                nav.closeDrawer()
                nav.nav(Route.EditMediaServers)
            },
        )

        NavigationRow(
            title = R.string.security_filters,
            icon = Icons.Outlined.Security,
            tint = MaterialTheme.colorScheme.onBackground,
            nav = nav,
            route = Route.SecurityFilters,
        )

        NavigationRow(
            title = R.string.privacy_options,
            icon = R.drawable.ic_tor,
            iconReference = 1,
            tint = MaterialTheme.colorScheme.onBackground,
            nav = nav,
            route = Route.PrivacyOptions,
        )

        accountViewModel.account.settings.keyPair.privKey?.let {
            IconRow(
                title = R.string.backup_keys,
                icon = Icons.Outlined.Key,
                tint = MaterialTheme.colorScheme.onBackground,
                onClick = {
                    nav.closeDrawer()
                    backupDialogOpen = true
                },
            )
        }

        NavigationRow(
            title = R.string.preferences,
            icon = Icons.Outlined.Settings,
            tint = MaterialTheme.colorScheme.onBackground,
            nav = nav,
            route = Route.Settings,
        )

        NavigationRow(
            title = R.string.user_preferences,
            icon = Icons.Outlined.Translate,
            tint = MaterialTheme.colorScheme.onBackground,
            nav = nav,
            route = Route.UserSettings,
        )

        Spacer(modifier = Modifier.weight(1f))

        IconRow(
            title = R.string.drawer_accounts,
            icon = Icons.Outlined.GroupAdd,
            tint = MaterialTheme.colorScheme.onBackground,
            onClick = openSheet,
        )
    }

    if (backupDialogOpen) {
        AccountBackupDialog(accountViewModel, onClose = { backupDialogOpen = false })
    }
}

@Composable
fun NavigationRow(
    title: Int,
    icon: Int,
    iconReference: Int,
    tint: Color,
    nav: INav,
    route: Route,
) {
    IconRow(
        title,
        icon,
        iconReference,
        tint,
        onClick = {
            nav.closeDrawer()
            nav.nav(route)
        },
    )
}

@Composable
fun NavigationRow(
    title: Int,
    icon: ImageVector,
    tint: Color,
    nav: INav,
    route: Route,
) {
    IconRow(
        title = title,
        icon = icon,
        tint = tint,
        onClick = {
            nav.closeDrawer()
            nav.nav(route)
        },
    )
}

@Composable
fun IconRow(
    title: Int,
    icon: Int,
    iconReference: Int,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = onClick,
                ),
    ) {
        Row(
            modifier = IconRowModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterRes(icon, iconReference),
                contentDescription = stringRes(title),
                modifier = Size22Modifier,
                tint = tint,
            )
            Text(
                modifier = IconRowTextModifier,
                text = stringRes(title),
                fontSize = Font18SP,
            )
        }
    }
}

@Composable
fun IconRow(
    title: Int,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = stringRes(title),
                    onClick = onClick,
                ),
    ) {
        Row(
            modifier = IconRowModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = stringRes(title),
                modifier = Size22Modifier.padding(end = 4.dp),
                tint = tint,
            )

            Text(
                modifier = IconRowTextModifier,
                text = stringRes(title),
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
                .padding(vertical = 15.dp, horizontal = 25.dp)
                .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterRes(R.drawable.relays, 4),
            contentDescription = stringRes(R.string.relay_setup),
            modifier = Size22Modifier,
            tint = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            modifier = IconRowTextModifier,
            text = stringRes(id = R.string.relay_setup),
            fontSize = Font18SP,
        )

        Spacer(modifier = Width16Space)

        RelayStatus(accountViewModel)
    }
}

class PoolStatus(
    val share: String,
    val isConnected: Boolean,
)

@Composable
private fun RelayStatus(accountViewModel: AccountViewModel) {
    val statusCounterFlow: Flow<PoolStatus> =
        remember(accountViewModel) {
            combine(
                accountViewModel.account.client.connectedRelaysFlow(),
                accountViewModel.account.client.availableRelaysFlow(),
            ) { connected, available ->
                PoolStatus("${connected.size}/${available.size}", connected.isNotEmpty())
            }
        }

    val relayPool by statusCounterFlow.collectAsStateWithLifecycle(PoolStatus("", false))

    Text(
        text = relayPool.share,
        color = if (relayPool.isConnected) MaterialTheme.colorScheme.placeholderText else Color.Red,
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
fun BottomContent(
    user: User,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
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
            val string =
                remember {
                    buildAnnotatedString {
                        withLink(
                            LinkAnnotation.Clickable(
                                "clickable",
                                TextLinkStyles(
                                    SpanStyle(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                ),
                            ) {
                                nav.nav(Route.Note(BuildConfig.RELEASE_NOTES_ID))
                                nav.closeDrawer()
                            },
                        ) {
                            append("v" + BuildConfig.VERSION_NAME + "-" + BuildConfig.FLAVOR.uppercase())
                        }
                    }
                }

            Text(
                text = string,
                modifier = Modifier.padding(start = 16.dp),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
            Box(modifier = Modifier.weight(1F))
            IconButton(
                onClick = {
                    nav.nav(Route.QRDisplay(user.pubkeyHex))
                    nav.closeDrawer()
                },
            ) {
                Icon(
                    painter = painterRes(R.drawable.ic_qrcode, 2),
                    contentDescription = stringRes(id = R.string.show_npub_as_a_qr_code),
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
