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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent

/**
 * Stack of overlapping circular avatars for pack members.
 * Shows up to [maxVisible] avatars; remainder is "+N" badge.
 */
@Composable
fun FollowPackAvatarStack(
    memberHexes: List<HexKey>,
    cache: DesktopLocalCache,
    modifier: Modifier = Modifier,
    avatarSize: androidx.compose.ui.unit.Dp = 32.dp,
    maxVisible: Int = 5,
) {
    @Suppress("UNUSED_VARIABLE")
    val metadataVersion by cache.metadataVersion.collectAsState()

    val visible = remember(memberHexes, maxVisible) { memberHexes.take(maxVisible) }
    val remaining = (memberHexes.size - visible.size).coerceAtLeast(0)
    val overlap = (avatarSize.value * 0.35f).dp

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visible.forEachIndexed { index, hex ->
            val pictureUrl = remember(hex, metadataVersion) { cache.getUserIfExists(hex)?.profilePicture() }
            Box(
                modifier =
                    Modifier
                        .offset(x = if (index == 0) 0.dp else (-overlap) * index)
                        .size(avatarSize + 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                UserAvatar(
                    userHex = hex,
                    pictureUrl = pictureUrl,
                    size = avatarSize,
                    contentDescription = "Pack member",
                )
            }
        }
        if (remaining > 0) {
            Box(
                modifier =
                    Modifier
                        .offset(x = (-overlap) * visible.size)
                        .size(avatarSize + 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(avatarSize)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+$remaining",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * Big hero card for the featured pack. Title + description + avatar stack + Follow-all CTA.
 *
 * [fullyFollowed]: when true, the action button flips to "Unfollow all".
 */
@Composable
fun FollowPackHeroCard(
    pack: FollowListEvent,
    cache: DesktopLocalCache,
    onFollowAll: () -> Unit,
    onShare: () -> Unit,
    onOpenDetail: () -> Unit,
    fullyFollowed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val title = pack.title()?.ifBlank { null } ?: "Untitled pack"
    val description = pack.description()?.ifBlank { null }
    val image = pack.image()?.ifBlank { null }
    val memberCount = pack.follows().size

    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpenDetail),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
    ) {
        // Image header (AsyncImage when available, gradient fallback)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
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
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                )
            }
        }
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (description != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            FollowPackAvatarStack(
                memberHexes = pack.followIds(),
                cache = cache,
                avatarSize = 32.dp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$memberCount ${if (memberCount == 1) "member" else "members"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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
                    Button(onClick = onFollowAll) {
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
}

/**
 * Horizontal chip rail for hashtags pulled from a featured pack.
 * Click toggles a filter on the gallery below.
 */
@Composable
fun FollowPackChipRail(
    tags: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(horizontal = 4.dp),
    ) {
        items(tags) { tag ->
            FilterChip(
                selected = tag == selected,
                onClick = { onSelect(if (tag == selected) null else tag) },
                label = { Text("#$tag") },
            )
        }
    }
}

/**
 * Single thumbnail row used in galleries and browse-all lists.
 */
@Composable
fun FollowPackRow(
    pack: FollowListEvent,
    cache: DesktopLocalCache,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = pack.title()?.ifBlank { null } ?: "Untitled pack"
    val description = pack.description()?.ifBlank { null }
    val image = pack.image()?.ifBlank { null }
    val memberCount = pack.follows().size

    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
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
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FollowPackAvatarStack(
                        memberHexes = pack.followIds(),
                        cache = cache,
                        avatarSize = 20.dp,
                        maxVisible = 4,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$memberCount ${if (memberCount == 1) "member" else "members"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
