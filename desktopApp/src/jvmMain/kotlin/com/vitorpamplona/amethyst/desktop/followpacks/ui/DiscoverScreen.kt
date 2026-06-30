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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.followpacks.FollowPackEditor
import com.vitorpamplona.amethyst.desktop.followpacks.FollowPacksState
import com.vitorpamplona.amethyst.desktop.followpacks.subscribeMetadataFor
import com.vitorpamplona.amethyst.desktop.model.DesktopIAccount
import com.vitorpamplona.amethyst.desktop.network.RelayConnectionManager
import com.vitorpamplona.amethyst.desktop.ui.ZapFeedback
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun DiscoverScreen(
    state: FollowPacksState,
    cache: DesktopLocalCache,
    iAccount: DesktopIAccount,
    relayManager: RelayConnectionManager,
    onOpenPack: (String) -> Unit,
    onOpenBrowseAll: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToThread: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER") onZapFeedback: (ZapFeedback) -> Unit,
    modifier: Modifier = Modifier,
) {
    val featured by state.featuredPack.collectAsState()
    val allPacks by state.allPacks.collectAsState()

    var selectedChip by remember { mutableStateOf<String?>(null) }
    var showFollowAll by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val current = featured
    val followedAuthors by iAccount.kind3FollowList.flow.collectAsState()
    val shuffleTick by state.shuffleTick.collectAsState()
    val fullyFollowed =
        remember(current, followedAuthors) {
            current != null && current.followIds().isNotEmpty() &&
                current.followIds().all { it == iAccount.pubKey || it in followedAuthors.authors }
        }
    val galleryPacks =
        remember(allPacks, selectedChip, current, shuffleTick) {
            val others = allPacks.filter { it.id != current?.id }
            val filtered =
                if (selectedChip == null) {
                    others
                } else {
                    others.filter { pack ->
                        pack.hashtags().any { it.equals(selectedChip, ignoreCase = true) }
                    }
                }
            // Reshuffle order on every shuffle tick so the right rail rotates too.
            if (shuffleTick > 0) filtered.shuffled() else filtered
        }

    // Prefetch member metadata for the featured pack so avatars + names show up.
    DisposableEffect(current?.id) {
        val pack = current
        if (pack != null) {
            val handle = relayManager.subscribeMetadataFor(pack.followIds().take(50), cache)
            onDispose { handle() }
        } else {
            onDispose { }
        }
    }

    val onShareCurrent: () -> Unit =
        remember(current) {
            {
                current?.let {
                    copyToClipboardLocal(FollowPackEditor.toShareUri(it))
                    scope.launch {
                        snackbarHostState.showSnackbar("Pack link copied to clipboard")
                    }
                }
            }
        }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            // ---- Top section: Row(Hero | RightRail) ----
            if (current == null) {
                EmptyDiscoverHero(onOpenBrowseAll = onOpenBrowseAll)
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(2f)) {
                        FollowPackHeroCard(
                            pack = current,
                            cache = cache,
                            onFollowAll = { showFollowAll = true },
                            onShare = onShareCurrent,
                            onOpenDetail = { onOpenPack(FollowPackEditor.aTag(current)) },
                            fullyFollowed = fullyFollowed,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.width(220.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        galleryPacks.take(4).forEach { pack ->
                            MiniPackThumb(
                                pack = pack,
                                onClick = { onOpenPack(FollowPackEditor.aTag(pack)) },
                            )
                        }
                        if (galleryPacks.size > 4) {
                            Text(
                                text = "Browse all ${galleryPacks.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier =
                                    Modifier
                                        .padding(top = 4.dp)
                                        .clickable(onClick = onOpenBrowseAll),
                            )
                        }
                    }
                }
            }

            // ---- Topic chips ----
            val chipTags = remember(current) { current?.let { state.hashtagsFor(it) } ?: emptyList() }
            if (chipTags.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "TOPICS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                FollowPackChipRail(
                    tags = chipTags,
                    selected = selectedChip,
                    onSelect = { selectedChip = it },
                )
            }

            // ---- From the pack ----
            if (current != null && current.followIds().isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "From the pack",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { state.shuffle() }) {
                        Icon(
                            symbol = MaterialSymbols.Shuffle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle")
                    }
                }
                Spacer(Modifier.height(8.dp))
                FromThePackFeed(
                    memberHexes = current.followIdSet(),
                    cache = cache,
                    relayManager = relayManager,
                    onNavigateToProfile = onNavigateToProfile,
                    onNavigateToThread = onNavigateToThread,
                )
            }
        }

        // Snackbar host overlay at bottom — confirms Share copy etc.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
        ) { data ->
            Snackbar(snackbarData = data)
        }
    }

    if (showFollowAll && current != null) {
        FollowAllConfirmDialog(
            pack = current,
            iAccount = iAccount,
            cache = cache,
            relayManager = relayManager,
            onDismiss = { showFollowAll = false },
        )
    }
}

@Composable
private fun MiniPackThumb(
    pack: FollowListEvent,
    onClick: () -> Unit,
) {
    val title = pack.title()?.ifBlank { null } ?: "Untitled pack"
    val image = pack.image()?.ifBlank { null }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Color(
                        (title.hashCode() and 0x80FFFFFF.toInt()) or 0xFF608080.toInt(),
                    ),
                ).clickable(onClick = onClick),
    ) {
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(72.dp),
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth().height(72.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(color = MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                symbol = MaterialSymbols.Add,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyDiscoverHero(onOpenBrowseAll: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Discover follow packs",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Curated lists of people you can follow in one tap.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenBrowseAll) { Text("Browse packs") }
    }
}

internal fun copyToClipboardLocal(text: String) {
    runCatching {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(text), null)
    }
}
