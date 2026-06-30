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
package com.vitorpamplona.amethyst.desktop.followpacks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.followpacks.FollowPackEditor
import com.vitorpamplona.amethyst.desktop.followpacks.FollowPacksState
import com.vitorpamplona.amethyst.desktop.followpacks.subscribeMetadataFor
import com.vitorpamplona.amethyst.desktop.model.DesktopIAccount
import com.vitorpamplona.amethyst.desktop.network.RelayConnectionManager
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent
import kotlinx.coroutines.launch

/**
 * Read-only pack detail screen. Opened as overlay when user clicks a pack
 * in Discover or Browse-all.
 *
 * Shows: pack hero (title, desc, image, member count), Follow all + Share +
 * Open profile actions, scrollable member list.
 */
@Composable
fun FollowPackDetailScreen(
    addressTag: String,
    state: FollowPacksState,
    cache: DesktopLocalCache,
    iAccount: DesktopIAccount,
    relayManager: RelayConnectionManager,
    onBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val allPacks by state.allPacks.collectAsState()

    @Suppress("UNUSED_VARIABLE")
    val metadataVersion by cache.metadataVersion.collectAsState()

    val pack: FollowListEvent? =
        remember(addressTag, allPacks) {
            allPacks.firstOrNull { FollowPackEditor.aTag(it) == addressTag }
        }

    if (pack == null) {
        EmptyOrLoadingDetail(onBack = onBack)
        return
    }

    // Prefetch metadata for members so avatars + names populate.
    DisposableEffect(pack.id) {
        val memberHexes = pack.followIdSet().toList().take(200)
        val handle = relayManager.subscribeMetadataFor(memberHexes, cache)
        onDispose { handle() }
    }

    val followedAuthors by iAccount.kind3FollowList.flow.collectAsState()
    val followedSet = followedAuthors.authors

    var showFollowAll by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        // Top bar with back button
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(
                    MaterialSymbols.AutoMirrored.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = pack.title()?.ifBlank { null } ?: "Untitled pack",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item("hero") {
                val fullyFollowed =
                    pack.followIds().isNotEmpty() &&
                        pack.followIds().all { it == iAccount.pubKey || it in followedSet }
                PackHeroDetail(
                    pack = pack,
                    cache = cache,
                    onFollowAll = { showFollowAll = true },
                    onShare = { copyToClipboardLocal(FollowPackEditor.toShareUri(pack)) },
                    fullyFollowed = fullyFollowed,
                )
            }
            item("members-header") {
                Text(
                    text = "MEMBERS (${pack.follows().size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            items(items = pack.followIds(), key = { it }) { hex ->
                MemberRow(
                    pubkeyHex = hex,
                    cache = cache,
                    isFollowed = hex in followedSet,
                    onOpenProfile = { onNavigateToProfile(hex) },
                    onFollow = {
                        scope.launch {
                            val user = cache.getOrCreateUser(hex)
                            val newCl = iAccount.kind3FollowList.follow(user)
                            cache.consume(newCl, null, wasVerified = false)
                            relayManager.broadcastToAll(newCl)
                        }
                    },
                    onUnfollow = {
                        scope.launch {
                            val user = cache.getOrCreateUser(hex)
                            val newCl = iAccount.kind3FollowList.unfollow(user) ?: return@launch
                            cache.consume(newCl, null, wasVerified = false)
                            relayManager.broadcastToAll(newCl)
                        }
                    },
                )
            }
        }
    }

    if (showFollowAll) {
        FollowAllConfirmDialog(
            pack = pack,
            iAccount = iAccount,
            cache = cache,
            relayManager = relayManager,
            onDismiss = { showFollowAll = false },
        )
    }
}

@Composable
private fun PackHeroDetail(
    pack: FollowListEvent,
    cache: DesktopLocalCache,
    onFollowAll: () -> Unit,
    onShare: () -> Unit,
    fullyFollowed: Boolean,
) {
    val title = pack.title()?.ifBlank { null } ?: "Untitled pack"
    val description = pack.description()?.ifBlank { null }
    val image = pack.image()?.ifBlank { null }
    val memberCount = pack.follows().size

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                ).padding(16.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Color(
                            (title.hashCode() and 0x80FFFFFF.toInt()) or 0xFF608080.toInt(),
                        ),
                    ),
        ) {
            if (image != null) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        if (description != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        FollowPackAvatarStack(
            memberHexes = pack.followIds(),
            cache = cache,
            avatarSize = 36.dp,
            maxVisible = 8,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "$memberCount ${if (memberCount == 1) "member" else "members"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (fullyFollowed) {
                OutlinedButton(onClick = onFollowAll) {
                    Icon(
                        symbol = MaterialSymbols.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Unfollow all")
                }
            } else {
                Button(onClick = onFollowAll, enabled = memberCount > 0) {
                    Icon(
                        symbol = MaterialSymbols.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Follow all")
                }
            }
            OutlinedButton(onClick = onShare) {
                Icon(
                    symbol = MaterialSymbols.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Share")
            }
        }
    }
}

@Composable
private fun MemberRow(
    pubkeyHex: HexKey,
    cache: DesktopLocalCache,
    isFollowed: Boolean,
    onOpenProfile: () -> Unit,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
) {
    @Suppress("UNUSED_VARIABLE")
    val metadataVersion by cache.metadataVersion.collectAsState()

    val user = remember(pubkeyHex, metadataVersion) { cache.getUserIfExists(pubkeyHex) }
    val displayName = user?.toBestDisplayName() ?: (pubkeyHex.take(12) + "…")
    val avatarUrl = user?.profilePicture()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenProfile)
                .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(
            userHex = pubkeyHex,
            pictureUrl = avatarUrl,
            size = 36.dp,
            contentDescription = displayName,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val nip05 = user?.metadataOrNull()?.nip05()
            if (!nip05.isNullOrBlank()) {
                Text(
                    text = nip05,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isFollowed) {
            OutlinedButton(onClick = onUnfollow) {
                Text("Unfollow")
            }
        } else {
            Button(onClick = onFollow) {
                Text("Follow")
            }
        }
    }
}

@Composable
private fun EmptyOrLoadingDetail(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(
                    MaterialSymbols.AutoMirrored.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Pack not found in cache.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
