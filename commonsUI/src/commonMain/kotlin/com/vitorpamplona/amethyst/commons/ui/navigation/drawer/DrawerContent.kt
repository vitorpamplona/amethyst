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
package com.vitorpamplona.amethyst.commons.ui.navigation.drawer

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.navigation.DrawerSectionId
import com.vitorpamplona.amethyst.commons.model.navigation.NavBarItem
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNote
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserAssertionsFollowerCount
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserStatuses
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.bookmarks
import com.vitorpamplona.amethyst.commons.resources.drafts
import com.vitorpamplona.amethyst.commons.resources.drawer_accounts
import com.vitorpamplona.amethyst.commons.resources.drawer_section_feeds
import com.vitorpamplona.amethyst.commons.resources.drawer_section_you
import com.vitorpamplona.amethyst.commons.resources.followers
import com.vitorpamplona.amethyst.commons.resources.following
import com.vitorpamplona.amethyst.commons.resources.ic_qrcode
import com.vitorpamplona.amethyst.commons.resources.ic_tor
import com.vitorpamplona.amethyst.commons.resources.longs
import com.vitorpamplona.amethyst.commons.resources.pictures
import com.vitorpamplona.amethyst.commons.resources.profile
import com.vitorpamplona.amethyst.commons.resources.profile_banner
import com.vitorpamplona.amethyst.commons.resources.profile_image
import com.vitorpamplona.amethyst.commons.resources.relay_setup
import com.vitorpamplona.amethyst.commons.resources.relays
import com.vitorpamplona.amethyst.commons.resources.route_chess
import com.vitorpamplona.amethyst.commons.resources.share_hls_video
import com.vitorpamplona.amethyst.commons.resources.show_npub_as_a_qr_code
import com.vitorpamplona.amethyst.commons.resources.status_update
import com.vitorpamplona.amethyst.commons.resources.tor_splash_connecting
import com.vitorpamplona.amethyst.commons.resources.tor_status_connected
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostStatus
import com.vitorpamplona.amethyst.commons.tor.TorServiceStatus
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.layouts.PermanentDrawerWidth
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.NavBarCatalog
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.NavBarItemDef
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.ui.painterRes
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.richtext.CreateTextWithEmoji
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.DrawerSectionHeaderModifier
import com.vitorpamplona.amethyst.commons.ui.theme.Font14SP
import com.vitorpamplona.amethyst.commons.ui.theme.Font18SP
import com.vitorpamplona.amethyst.commons.ui.theme.IconRowTextModifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size22Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size22ModifierWith4Padding
import com.vitorpamplona.amethyst.commons.ui.theme.Size24Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size25Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size26Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.TextStyleBottomNavBar
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.Width16Space
import com.vitorpamplona.amethyst.commons.ui.theme.bannerModifier
import com.vitorpamplona.amethyst.commons.ui.theme.drawerSpacing
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.theme.profileContentHeaderModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip38UserStatus.UserStatusEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.jetbrains.compose.resources.StringResource

@Composable
fun DrawerContent(
    nav: INav,
    openSheet: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    ModalDrawerSheet(
        windowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Start),
        drawerContainerColor = MaterialTheme.colorScheme.background,
        drawerTonalElevation = 0.dp,
    ) {
        DrawerContentBody(nav, openSheet, accountViewModel)
    }
}

/**
 * Expanded windows: the same drawer content, permanently docked on the left of the shell
 * instead of sliding in as a modal sheet.
 */
@Composable
fun PermanentDrawerContent(
    nav: INav,
    openSheet: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    Surface(
        modifier = Modifier.width(PermanentDrawerWidth).fillMaxHeight(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            Modifier.windowInsetsPadding(
                WindowInsets.systemBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Start),
            ),
        ) {
            DrawerContentBody(nav, openSheet, accountViewModel)
        }
    }
}

@Composable
private fun DrawerContentBody(
    nav: INav,
    openSheet: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    val onClickUser = {
        nav.navDrawer(routeFor(accountViewModel.userProfile()))
        nav.closeDrawer()
    }

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
                contentDescription = stringRes(id = Res.string.profile_image),
                contentScale = ContentScale.Crop,
                modifier = bannerModifier,
            )
        } else {
            Image(
                painter = painterRes(Res.drawable.profile_banner, 3),
                contentDescription = stringRes(Res.string.profile_banner),
                contentScale = ContentScale.Crop,
                modifier = bannerModifier,
            )
        }

        Column(modifier = modifier) {
            RobohashFallbackAsyncImage(
                robot = profilePubHex,
                model = profilePicture,
                contentDescription = stringRes(id = Res.string.profile_image),
                modifier =
                    Modifier
                        .width(100.dp)
                        .height(100.dp)
                        .clip(shape = CircleShape)
                        .border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                        .clickable(onClick = onClick),
                loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
                loadRobohash = LocalDisplaySettings.current.loadRobohash,
                autoPlayGif =
                    accountViewModel.settings.autoPlayVideosFlow
                        .collectAsStateWithLifecycle()
                        .value,
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
    val allStatuses by observeUserStatuses(baseAccountUser, accountViewModel)

    // The music status is written by the now-playing publisher, not typed here: editing it would
    // overwrite the track and keep its expiration, so only the other statuses are editable.
    val statuses = remember(allStatuses) { allStatuses.filter { it.address.dTag != UserStatusEvent.MUSIC } }

    if (statuses.isEmpty()) {
        PreviewStatusEditBar(accountViewModel = accountViewModel, nav = nav)
    } else {
        statuses.forEach {
            val noteStatus by observeNote(it, accountViewModel)

            PreviewStatusEditBar(noteStatus.note.event?.content, it.address, accountViewModel, nav)
        }
    }
}

@Composable
fun PreviewStatusEditBar(
    savedStatus: String? = null,
    address: Address? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var isEditing by remember { mutableStateOf(false) }

    if (isEditing) {
        StatusEditBar(savedStatus, address, onDone = { isEditing = false }, accountViewModel, nav)
    } else {
        FakeEditBar(savedStatus) { isEditing = true }
    }
}

@Composable
fun FakeEditBar(
    savedStatus: String? = null,
    onEdit: () -> Unit,
) {
    // ── Static text styled to look like OutlinedTextField ───
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onEdit,
                ).padding(top = 8.dp),
    ) {
        // Outer border — matches OutlinedTextField's unfocused border
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp) // same as OutlinedTextField
                    .border(
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(4.dp),
                    ).padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = savedStatus?.ifEmpty { null } ?: stringRes(Res.string.status_update),
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (savedStatus?.ifEmpty { null } == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        }

        // Floating label — sits on top of the border like Material does
        Text(
            text = stringRes(Res.string.status_update),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .padding(start = 12.dp)
                    .align(Alignment.TopStart)
                    .offset(y = (-8).dp) // float above the border
                    .background(MaterialTheme.colorScheme.surface) // punch through border line
                    .padding(horizontal = 4.dp),
        )
    }
}

@Composable
fun StatusEditBar(
    savedStatus: String? = null,
    address: Address? = null,
    onDone: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    val currentStatus = remember { mutableStateOf(savedStatus ?: "") }

    // In the docked drawer the DrawerState never opens (it stays Closed while the drawer
    // is always on screen), so the modal close-cancels-editing behavior must not apply there.
    LaunchedEffect(nav.drawerState.isClosed) {
        if (!nav.isDrawerDocked && nav.drawerState.isClosed) {
            focusManager.clearFocus(true)
            onDone()
        } else {
            focusRequester.requestFocus()
        }
    }

    OutlinedTextField(
        value = currentStatus.value,
        onValueChange = { currentStatus.value = it },
        label = { Text(text = stringRes(Res.string.status_update)) },
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        placeholder = {
            Text(
                text = stringRes(Res.string.status_update),
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
                    // Collapse back to the read-only bar: in the docked drawer no
                    // drawer-close will ever do it, and in the modal drawer this beats
                    // staying in edit mode until the drawer closes.
                    onDone()
                },
            ),
        singleLine = true,
        trailingIcon = {
            val hasChanged = remember { derivedStateOf { currentStatus.value != (savedStatus ?: "") } }
            if (hasChanged.value) {
                SendButton(
                    tint = MaterialTheme.colorScheme.primary,
                ) {
                    if (address == null) {
                        accountViewModel.createStatus(currentStatus.value)
                    } else {
                        accountViewModel.updateStatus(address, currentStatus.value)
                    }
                    focusManager.clearFocus(true)
                    onDone()
                }
            } else {
                if (address != null) {
                    UserStatusDeleteButton {
                        accountViewModel.deleteStatus(address)
                        focusManager.clearFocus(true)
                        onDone()
                    }
                }
            }
        },
    )
}

@Composable
fun SendButton(
    tint: Color = MaterialTheme.colorScheme.placeholderText,
    onClick: () -> Unit,
) {
    IconButton(
        modifier = Size26Modifier,
        onClick = onClick,
    ) {
        Icon(
            symbol = MaterialSymbols.AutoMirrored.Send,
            null,
            modifier = Size25Modifier,
            tint = tint,
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
            symbol = MaterialSymbols.Delete,
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

        Text(stringRes(Res.string.following))

        Spacer(modifier = DoubleHorzSpacer)

        DisplayFollowerCount(baseAccountUser, accountViewModel)

        Text(stringRes(Res.string.followers))
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
    val followerCount by observeUserAssertionsFollowerCount(baseAccountUser.userProfile(), accountViewModel)

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
    // Per-account, synced through the NIP-78 app-specific data event, and edited on the
    // Side Menu settings screen. Empty (the default) means the full stock drawer.
    val hidden by accountViewModel.hiddenDrawerItemsFlow().collectAsStateWithLifecycle()

    // Device-global and never synced: which headings the user folded away, restored from disk at
    // startup so the menu opens the way they left it. Collected once here rather than per section —
    // one collector feeds every heading. See DrawerSectionCollapsePreferences.
    val collapsePrefs = LocalAppServices.current.drawerSectionCollapsePrefs
    val collapsed by collapsePrefs.flow.collectAsStateWithLifecycle()

    Column(modifier) {
        DrawerSections.forEach { section ->
            // Keyed by section: hiding the last row of a section removes it from the drawer
            // entirely, and without a key the sections below would slide up into its slots and
            // inherit its animateContentSize state, animating a height they never had.
            key(section.id) {
                CatalogSection(
                    section = section,
                    hidden = hidden,
                    expanded = section.id !in collapsed,
                    onToggleExpand = { collapsePrefs.toggle(section.id) },
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        IconRow(
            title = Res.string.drawer_accounts,
            icon = MaterialSymbols.GroupAdd,
            tint = MaterialTheme.colorScheme.onBackground,
            onClick = openSheet,
        )
    }
}

/**
 * The Create section's rows — composer entry points, none of which is a catalog destination. They
 * open as plain pushes rather than through [INav.navDrawer]: those screens host no bottom bar, and
 * a drawer stamp would tell FabBottomBarPadding that one is showing.
 */
@Composable
private fun CreateRows(nav: INav) {
    IconRow(
        title = Res.string.share_hls_video,
        icon = MaterialSymbols.SettingsInputAntenna,
        tint = MaterialTheme.colorScheme.onBackground,
        onClick = {
            nav.closeDrawer()
            nav.nav(Route.NewHlsVideo)
        },
    )

    if (LocalAppPlatform.current.isDebugBuild) {
        IconRow(
            title = Res.string.route_chess,
            icon = MaterialSymbols.ChessKnight,
            tint = MaterialTheme.colorScheme.onBackground,
            onClick = {
                nav.closeDrawer()
                nav.nav(Route.Chess)
            },
        )
    }
}

/**
 * Renders one drawer section: its fixed rows, if it has any, then the catalog rows the user hasn't
 * switched off. Profile gets the primary-colored tint; every other item uses onBackground.
 *
 * A section with nothing left to show renders nothing at all — an empty, permanently collapsed
 * heading is just noise. Two sections always have something: Create is entirely fixed rows, and
 * System carries the relay-status row (not a catalog destination — it shows a live counter).
 */
@Composable
fun CatalogSection(
    section: DrawerSection,
    hidden: Set<NavBarItem>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onBackground = MaterialTheme.colorScheme.onBackground

    val visible = remember(section, hidden) { DrawerSectionVisibility.visibleItems(section, hidden) }
    if (visible.isEmpty() && !section.hasFixedRows) return

    CollapsibleSection(title = section.titleRes, expanded = expanded, onToggleExpand = onToggleExpand) {
        when (section.id) {
            DrawerSectionId.CREATE -> CreateRows(nav)
            DrawerSectionId.SYSTEM ->
                IconRowRelays(
                    accountViewModel = accountViewModel,
                    onClick = {
                        nav.closeDrawer()
                        nav.nav(Route.EditRelays)
                    },
                )
            else -> {}
        }

        visible.forEach { id ->
            NavBarCatalog[id]?.let { def ->
                val tint = if (def.id == NavBarItem.PROFILE) primary else onBackground
                if (def.id == NavBarItem.SCHEDULED_POSTS) {
                    ScheduledPostsNavigationRow(def, tint, accountViewModel, nav)
                } else {
                    CatalogNavigationRow(def, tint, accountViewModel, nav)
                }
            }
        }
    }
}

@Composable
private fun ScheduledPostsNavigationRow(
    def: NavBarItemDef,
    tint: Color,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val accountHex = accountViewModel.account.signer.pubKey
    val allPosts by LocalAppServices.current.scheduledPostStore.flow
        .collectAsStateWithLifecycle()
    val pendingCount by remember(accountHex) {
        derivedStateOf {
            allPosts.count {
                it.accountPubkey == accountHex &&
                    (
                        it.status == ScheduledPostStatus.PENDING ||
                            it.status == ScheduledPostStatus.PUBLISHING ||
                            it.status == ScheduledPostStatus.FAILED
                    )
            }
        }
    }
    IconRowWithBadge(
        title = def.labelRes,
        icon = def.icon,
        tint = tint,
        badgeCount = pendingCount,
        onClick = {
            nav.closeDrawer()
            nav.navDrawer { def.resolveRoute(accountViewModel) }
        },
    )
}

@Composable
private fun IconRowWithBadge(
    title: StringResource,
    icon: MaterialSymbol,
    tint: Color,
    badgeCount: Int,
    onClick: () -> Unit,
) {
    val titleStr = stringRes(title)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = onClick,
                    onClickLabel = titleStr,
                ).padding(vertical = 15.dp, horizontal = 25.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            symbol = icon,
            contentDescription = titleStr,
            modifier = Size22ModifierWith4Padding,
            tint = tint,
        )
        Text(
            modifier = IconRowTextModifier,
            text = titleStr,
            fontSize = Font18SP,
        )
        if (badgeCount > 0) {
            Badge { Text(badgeCount.toString()) }
        }
    }
}

@Composable
fun CatalogNavigationRow(
    def: NavBarItemDef,
    tint: Color,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    NavigationRow(
        title = def.labelRes,
        icon = def.icon,
        tint = tint,
        nav = nav,
        computeRoute = { def.resolveRoute(accountViewModel) },
    )
}

@Composable
private fun CollapsibleSection(
    title: StringResource,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    content: @Composable () -> Unit,
) {
    val sectionTitle = stringRes(title)

    Column(modifier = Modifier.animateContentSize()) {
        Row(
            modifier = DrawerSectionHeaderModifier.clickable(onClick = onToggleExpand),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = sectionTitle,
                fontSize = Font14SP,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                symbol = if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
                contentDescription = sectionTitle,
                modifier = Size22Modifier,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (expanded) {
            content()
        }
    }
}

@Composable
fun NavigationRow(
    title: StringResource,
    icon: MaterialSymbol,
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
            nav.navDrawer(route)
        },
    )
}

@Composable
fun NavigationRow(
    title: StringResource,
    icon: MaterialSymbol,
    tint: Color,
    nav: INav,
    computeRoute: () -> Route,
) {
    IconRow(
        title = title,
        icon = icon,
        tint = tint,
        onClick = {
            nav.closeDrawer()
            nav.navDrawer(computeRoute)
        },
    )
}

@Composable
fun IconRow(
    title: StringResource,
    icon: MaterialSymbol,
    tint: Color,
    onClick: () -> Unit,
) {
    val title = stringRes(title)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = onClick,
                    onClickLabel = title,
                ).padding(vertical = 15.dp, horizontal = 25.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            symbol = icon,
            contentDescription = title,
            modifier = Size22ModifierWith4Padding,
            tint = tint,
        )

        Text(
            modifier = IconRowTextModifier,
            text = title,
            fontSize = Font18SP,
        )
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
                .clickable(onClick = onClick)
                .padding(vertical = 15.dp, horizontal = 25.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterRes(Res.drawable.relays, 4),
            contentDescription = stringRes(Res.string.relay_setup),
            modifier = Size22Modifier,
            tint = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            modifier = IconRowTextModifier,
            text = stringRes(id = Res.string.relay_setup),
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
            horizontalArrangement = Arrangement.Absolute.SpaceBetween,
        ) {
            // The version (release notes link) and, while Tor is in use, its status beside it.
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val platform = LocalAppPlatform.current
                val string =
                    remember(platform) {
                        buildAnnotatedString {
                            withLink(
                                LinkAnnotation.Clickable(
                                    "clickable",
                                    TextStyleBottomNavBar,
                                ) {
                                    platform.releaseNotesId?.let { nav.nav(Route.Note(it)) }
                                    nav.closeDrawer()
                                },
                            ) {
                                append("v")
                                append(platform.appVersionName)
                                if (platform.appFlavor.isNotEmpty()) {
                                    append("-")
                                    append(platform.appFlavor.uppercase())
                                }
                            }
                        }
                    }

                Text(
                    text = string,
                    // Yields to the Tor icon on a narrow drawer instead of pushing it off.
                    modifier = Modifier.weight(1f, fill = false).padding(start = 16.dp),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )

                TorStatusIcon(nav)
            }

            IconButton(
                onClick = {
                    nav.nav(Route.QRDisplay(user.pubkeyHex))
                    nav.closeDrawer()
                },
            ) {
                Icon(
                    painter = painterRes(Res.drawable.ic_qrcode, 2),
                    contentDescription = stringRes(id = Res.string.show_npub_as_a_qr_code),
                    modifier = Size24Modifier,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Tor's state next to the app version, opening the Tor settings on tap. Hidden while Tor is off: that
 * is a choice, not a fault, so it gets no icon at all rather than an alarming one. Connected only once
 * the directory is ready ([TorServiceStatus.isFullyBootstrapped]); a bound proxy still downloading it
 * cannot carry traffic yet, so it reads as connecting.
 */
@Composable
private fun TorStatusIcon(nav: INav) {
    val appServices = LocalAppServices.current
    val torStatus by remember(appServices) { appServices.torStatus }
        .collectAsStateWithLifecycle(TorServiceStatus.Off)
    if (torStatus is TorServiceStatus.Off) return

    val connected = torStatus.isFullyBootstrapped
    IconButton(
        onClick = {
            nav.nav(Route.PrivacyOptions)
            nav.closeDrawer()
        },
    ) {
        Icon(
            painter = painterRes(Res.drawable.ic_tor, 2),
            contentDescription = stringRes(if (connected) Res.string.tor_status_connected else Res.string.tor_splash_connecting),
            modifier = Size20Modifier,
            tint = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview
@Composable
private fun CollapsibleSectionPreview() {
    ThemeComparisonColumn {
        Column {
            CollapsibleSection(title = Res.string.drawer_section_you, expanded = true, onToggleExpand = {}) {
                IconRow(
                    title = Res.string.profile,
                    icon = MaterialSymbols.AccountCircle,
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = {},
                )
                IconRow(
                    title = Res.string.bookmarks,
                    icon = MaterialSymbols.CollectionsBookmark,
                    tint = MaterialTheme.colorScheme.onBackground,
                    onClick = {},
                )
                IconRow(
                    title = Res.string.drafts,
                    icon = MaterialSymbols.Drafts,
                    tint = MaterialTheme.colorScheme.onBackground,
                    onClick = {},
                )
            }
            // Collapsed, to preview the other half of the heading: its rows are hidden and the
            // chevron points down. Real collapse state lives in DrawerSectionCollapsePreferences.
            CollapsibleSection(title = Res.string.drawer_section_feeds, expanded = false, onToggleExpand = {}) {
                IconRow(
                    title = Res.string.pictures,
                    icon = MaterialSymbols.Photo,
                    tint = MaterialTheme.colorScheme.onBackground,
                    onClick = {},
                )
                IconRow(
                    title = Res.string.longs,
                    icon = MaterialSymbols.SmartDisplay,
                    tint = MaterialTheme.colorScheme.onBackground,
                    onClick = {},
                )
            }
        }
    }
}

@Preview(widthDp = 320, heightDp = 1400)
@Composable
private fun ListContentPreview() {
    ThemeComparisonColumn {
        ListContent(
            modifier = Modifier.fillMaxWidth(),
            openSheet = {},
            accountViewModel = mockAccountViewModel(),
            nav = EmptyNav(),
        )
    }
}
