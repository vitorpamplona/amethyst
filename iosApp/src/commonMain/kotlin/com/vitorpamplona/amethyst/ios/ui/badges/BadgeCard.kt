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
package com.vitorpamplona.amethyst.ios.ui.badges

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar

/**
 * A compact badge chip for display in a horizontal gallery row on profiles.
 */
@Composable
fun BadgeChip(
    badge: BadgeDisplayData,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(8.dp).width(80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (badge.thumbUrl != null || badge.imageUrl != null) {
                AsyncImage(
                    model = badge.thumbUrl ?: badge.imageUrl,
                    contentDescription = badge.name,
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                // Placeholder badge icon
                Text(
                    "🏅",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                badge.name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * Expanded badge card showing full details.
 */
@Composable
fun BadgeDetailCard(
    badge: BadgeDisplayData,
    onIssuerClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Badge image
            if (badge.imageUrl != null) {
                AsyncImage(
                    model = badge.imageUrl,
                    contentDescription = badge.name,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(8.dp))
            }

            Text(
                badge.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            if (!badge.description.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    badge.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Issuer info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.let {
                        if (onIssuerClick != null) {
                            it.clickable { onIssuerClick(badge.issuerPubKeyHex) }
                        } else {
                            it
                        }
                    },
            ) {
                UserAvatar(
                    userHex = badge.issuerPubKeyHex,
                    pictureUrl = badge.issuerProfilePicture,
                    size = 20.dp,
                    contentDescription = "Issuer",
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Issued by ${badge.issuerDisplayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Horizontal scrollable gallery of badges for display on a profile.
 */
@Composable
fun BadgeGallery(
    badges: List<BadgeDisplayData>,
    onBadgeClick: ((BadgeDisplayData) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (badges.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "🏅 Badges (${badges.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            items(badges, key = { it.definitionAddressId }) { badge ->
                BadgeChip(
                    badge = badge,
                    onClick = onBadgeClick?.let { { it(badge) } },
                )
            }
        }
    }
}
