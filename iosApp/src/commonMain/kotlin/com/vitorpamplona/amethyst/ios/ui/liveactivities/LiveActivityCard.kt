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
package com.vitorpamplona.amethyst.ios.ui.liveactivities

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.StatusTag

@Composable
fun LiveActivityCard(
    activity: LiveActivityDisplayData,
    onClick: () -> Unit = {},
    onHostClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Image banner if available
            if (activity.image != null) {
                AsyncImage(
                    model = activity.image,
                    contentDescription = activity.title,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(8.dp))
            }

            // Status badge + title
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(activity.status)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = activity.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(6.dp))

            // Host info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.clickable(enabled = onHostClick != null) {
                        onHostClick?.invoke(activity.hostPubKeyHex)
                    },
            ) {
                UserAvatar(
                    userHex = activity.hostPubKeyHex,
                    pictureUrl = activity.hostProfilePicture,
                    size = 24.dp,
                    contentDescription = "Host",
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = activity.hostDisplayName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Participant count
            val participants = activity.currentParticipants ?: activity.participantCount
            if (participants > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$participants participant${if (participants != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
fun StatusBadge(
    status: StatusTag.STATUS?,
    modifier: Modifier = Modifier,
) {
    val (text, bgColor) =
        when (status) {
            StatusTag.STATUS.LIVE -> "LIVE" to Color(0xFFE53935)
            StatusTag.STATUS.PLANNED -> "PLANNED" to Color(0xFFFFA726)
            StatusTag.STATUS.ENDED -> "ENDED" to Color(0xFF757575)
            null -> "UNKNOWN" to Color(0xFF9E9E9E)
        }

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier =
            modifier
                .background(bgColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
