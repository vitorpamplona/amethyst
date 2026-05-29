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
package com.vitorpamplona.amethyst.desktop.ui.deck

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.domain.nip46.SignerConnectionState
import com.vitorpamplona.amethyst.commons.feeds.custom.FeedDefinition
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.account.AccountInfo
import com.vitorpamplona.amethyst.commons.tor.TorServiceStatus
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar
import com.vitorpamplona.amethyst.desktop.DesktopPreferences
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.platform.titleBarInsetTop
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import kotlinx.collections.immutable.ImmutableList

private val EXPANDED_WIDTH = 240.dp
private val COLLAPSED_WIDTH = 64.dp

/**
 * Data class for a sidebar navigation item.
 */
private data class NavItem(
    val type: DeckColumnType,
    val label: String,
    val icon: MaterialSymbol,
)

private val NAV_ITEMS =
    listOf(
        NavItem(DeckColumnType.HomeFeed, "Home", MaterialSymbols.Home),
        NavItem(DeckColumnType.Search, "Search", MaterialSymbols.Search),
        NavItem(DeckColumnType.Messages, "Messages", MaterialSymbols.Mail),
        NavItem(DeckColumnType.Wallet, "Wallet", MaterialSymbols.AccountBalanceWallet),
        NavItem(DeckColumnType.Bookmarks, "Bookmarks", MaterialSymbols.Bookmark),
        NavItem(DeckColumnType.Settings, "Settings", MaterialSymbols.Settings),
    )

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MainSidebar(
    activeNpub: String?,
    allAccounts: ImmutableList<AccountInfo>,
    localCache: DesktopLocalCache?,
    onSwitchAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (String) -> Unit,
    onAddColumn: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigate: (DeckColumnType) -> Unit,
    activeColumnType: DeckColumnType?,
    onShowImportFollowListDialog: () -> Unit = {},
    signerConnectionState: SignerConnectionState,
    lastPingTimeSec: Long?,
    torStatus: TorServiceStatus,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(!DesktopPreferences.sidebarCollapsed) }

    val animatedWidth by animateDpAsState(
        targetValue = if (expanded) EXPANDED_WIDTH else COLLAPSED_WIDTH,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
    )

    // Observe metadata to recompose when display names/pictures load
    @Suppress("UNUSED_VARIABLE")
    val metadataVersion by localCache?.metadataVersion?.collectAsState()
        ?: remember { mutableStateOf(0L) }

    // Resolve user info for avatar header
    val pubkeyHex = remember(activeNpub) { activeNpub?.let { decodePublicKeyAsHexOrNull(it) } }
    val user =
        remember(pubkeyHex, metadataVersion) {
            pubkeyHex?.let { localCache?.getUserIfExists(it) }
        }
    val displayName =
        remember(user, metadataVersion) {
            user?.let {
                val name = it.toBestDisplayName()
                name.takeIf { n -> n != it.pubkeyDisplayHex() }
            }
        }
    val avatarUrl = remember(user, metadataVersion) { user?.profilePicture() }

    // Custom feeds from repository
    val feedRepo = LocalFeedRepository.current
    val allFeeds by feedRepo.feeds.collectAsState()

    Column(
        modifier =
            modifier
                .width(animatedWidth)
                .fillMaxHeight()
                .clipToBounds()
                .background(MaterialTheme.colorScheme.surface)
                .padding(top = 8.dp + titleBarInsetTop, bottom = 8.dp),
    ) {
        // -- Avatar + Account header --
        SidebarAccountHeader(
            activeNpub = activeNpub,
            allAccounts = allAccounts,
            localCache = localCache,
            displayName = displayName,
            avatarUrl = avatarUrl,
            pubkeyHex = pubkeyHex,
            expanded = expanded,
            onSwitchAccount = onSwitchAccount,
            onAddAccount = onAddAccount,
            onRemoveAccount = onRemoveAccount,
        )

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
        Spacer(Modifier.height(8.dp))

        // -- Nav items (scrollable) --
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        ) {
            NAV_ITEMS.forEach { item ->
                val isActive = activeColumnType?.typeKey() == item.type.typeKey()
                SidebarNavItem(
                    icon = item.icon,
                    label = item.label,
                    isActive = isActive,
                    expanded = expanded,
                    onClick = { onNavigate(item.type) },
                )
            }

            // -- Custom feeds section --
            if (allFeeds.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                Spacer(Modifier.height(8.dp))

                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(tween(200, delayMillis = 100)),
                    exit = fadeOut(tween(100)),
                ) {
                    Text(
                        text = "FEEDS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                allFeeds.forEach { feed ->
                    val isActive =
                        activeColumnType is DeckColumnType.CustomFeed &&
                            activeColumnType.feedId == feed.id
                    SidebarFeedItem(
                        feed = feed,
                        isActive = isActive,
                        expanded = expanded,
                        onClick = {
                            onNavigate(
                                DeckColumnType.CustomFeed(
                                    feedId = feed.id,
                                    feedName = feed.name,
                                    feedEmoji = feed.emoji,
                                ),
                            )
                        },
                    )
                }

                // "+ Add Feed" button
                SidebarNavItem(
                    icon = MaterialSymbols.Add,
                    label = "Add Feed",
                    isActive = false,
                    expanded = expanded,
                    onClick = onAddColumn,
                )
            }
        }

        // -- Bottom section: status indicators + collapse toggle --
        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
        Spacer(Modifier.height(4.dp))

        // Tor status — same style as nav items
        val torLabel =
            when (torStatus) {
                is TorServiceStatus.Off -> "Tor: Off"
                is TorServiceStatus.Connecting -> "Tor: Connecting"
                is TorServiceStatus.Active -> "Tor: Connected"
                is TorServiceStatus.Error -> "Tor: Error"
            }
        val torIcon =
            if (torStatus is TorServiceStatus.Active) MaterialSymbols.Security else MaterialSymbols.Shield
        SidebarNavItem(
            icon = torIcon,
            label = torLabel,
            isActive = false,
            expanded = expanded,
            onClick = onOpenSettings,
        )

        // Bunker heartbeat — same style as nav items (only show when connected)
        if (signerConnectionState is SignerConnectionState.Connected) {
            SidebarNavItem(
                icon = MaterialSymbols.Favorite,
                label = "Bunker: OK",
                isActive = false,
                expanded = expanded,
                onClick = onOpenSettings,
            )
        }

        Spacer(Modifier.height(4.dp))

        // Collapse toggle
        SidebarNavItem(
            icon = if (expanded) MaterialSymbols.AutoMirrored.KeyboardArrowLeft else MaterialSymbols.ChevronRight,
            label = if (expanded) "Collapse" else "Expand",
            isActive = false,
            expanded = expanded,
            onClick = {
                expanded = !expanded
                DesktopPreferences.sidebarCollapsed = !expanded
            },
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SidebarAccountHeader(
    activeNpub: String?,
    allAccounts: ImmutableList<AccountInfo>,
    localCache: DesktopLocalCache?,
    displayName: String?,
    avatarUrl: String?,
    pubkeyHex: String?,
    expanded: Boolean,
    onSwitchAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (String) -> Unit,
) {
    var showDropdown by remember { mutableStateOf(false) }
    var isHovered by remember { mutableStateOf(false) }

    val hoverBg =
        if (isHovered) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        } else {
            Color.Transparent
        }

    Box {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { showDropdown = true }
                    .background(hoverBg)
                    .onPointerEvent(PointerEventType.Enter) { isHovered = true }
                    .onPointerEvent(PointerEventType.Exit) { isHovered = false }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar
            if (pubkeyHex != null) {
                UserAvatar(
                    userHex = pubkeyHex,
                    pictureUrl = avatarUrl,
                    size = if (expanded) 32.dp else 28.dp,
                    contentDescription = "Account avatar",
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .size(if (expanded) 32.dp else 28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        MaterialSymbols.Person,
                        contentDescription = "Account",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName ?: "Account",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (activeNpub != null) {
                        Text(
                            text = npubShortForSidebar(activeNpub),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Account switcher dropdown menu
        DropdownMenu(
            expanded = showDropdown,
            onDismissRequest = { showDropdown = false },
            offset = DpOffset(x = if (expanded) 240.dp else 56.dp, y = 0.dp),
        ) {
            allAccounts.forEach { account ->
                val isActive = account.npub == activeNpub
                DropdownMenuItem(
                    text = {
                        Text(
                            text = resolveDisplayNameForSidebar(account.npub, localCache) ?: npubShortForSidebar(account.npub),
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        showDropdown = false
                        onSwitchAccount(account.npub)
                    },
                    trailingIcon = {
                        if (isActive) {
                            Icon(MaterialSymbols.Check, contentDescription = "Active", modifier = Modifier.size(16.dp))
                        }
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Add Account") },
                onClick = {
                    showDropdown = false
                    onAddAccount()
                },
                leadingIcon = { Icon(MaterialSymbols.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
        }
    }
}

/**
 * A single navigation item row with icon + optional label.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SidebarNavItem(
    icon: MaterialSymbol,
    label: String,
    isActive: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    var isHovered by remember { mutableStateOf(false) }

    val backgroundColor =
        when {
            isActive -> MaterialTheme.colorScheme.primaryContainer
            isHovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            else -> MaterialTheme.colorScheme.surface
        }

    val iconTint =
        if (isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    val textColor =
        if (isActive) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onClick)
                .background(backgroundColor)
                .onPointerEvent(PointerEventType.Enter) { isHovered = true }
                .onPointerEvent(PointerEventType.Exit) { isHovered = false }
                .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = if (!expanded) label else null,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(200, delayMillis = 100)),
            exit = fadeOut(tween(100)),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/**
 * A custom feed item in the sidebar.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SidebarFeedItem(
    feed: FeedDefinition,
    isActive: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    var isHovered by remember { mutableStateOf(false) }

    val backgroundColor =
        when {
            isActive -> MaterialTheme.colorScheme.primaryContainer
            isHovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            else -> MaterialTheme.colorScheme.surface
        }

    val iconTint =
        if (isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    val textColor =
        if (isActive) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onClick)
                .background(backgroundColor)
                .onPointerEvent(PointerEventType.Enter) { isHovered = true }
                .onPointerEvent(PointerEventType.Exit) { isHovered = false }
                .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (feed.emoji.isNotEmpty() && expanded) {
            Text(
                text = feed.emoji,
                modifier = Modifier.size(24.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        } else {
            Icon(
                MaterialSymbols.AutoMirrored.Feed,
                contentDescription = if (!expanded) feed.name else null,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(200, delayMillis = 100)),
            exit = fadeOut(tween(100)),
        ) {
            Text(
                text = feed.name.ifEmpty { "Feed" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

private fun npubShortForSidebar(npub: String): String {
    if (npub.length <= 20) return npub
    return "${npub.take(10)}...${npub.takeLast(6)}"
}

private fun resolveDisplayNameForSidebar(
    npub: String,
    localCache: DesktopLocalCache?,
): String? {
    if (localCache == null) return null
    val pubkeyHex =
        com.vitorpamplona.quartz.nip19Bech32
            .decodePublicKeyAsHexOrNull(npub) ?: return null
    val user = localCache.getUserIfExists(pubkeyHex) ?: return null
    val name = user.toBestDisplayName()
    return name.takeIf { it != user.pubkeyDisplayHex() }
}
