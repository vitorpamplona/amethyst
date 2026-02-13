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
package com.vitorpamplona.amethyst.ui.broadcast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Constants
import com.vitorpamplona.amethyst.service.broadcast.BroadcastEvent
import com.vitorpamplona.amethyst.service.broadcast.BroadcastStatus
import com.vitorpamplona.amethyst.service.broadcast.RelayResult
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.util.UUID

/**
 * Banner showing active broadcast progress.
 * Displayed above bottom navigation when events are being sent to relays.
 */
@Composable
fun BroadcastBanner(
    broadcasts: ImmutableList<BroadcastEvent>,
    onTap: () -> Unit = {},
    onRetryAll: () -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = broadcasts.isNotEmpty(),
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(200)),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(150)),
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onTap),
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .animateContentSize(),
            ) {
                val isAllFinished = broadcasts.all { it.status != BroadcastStatus.IN_PROGRESS }

                if (isAllFinished) {
                    if (broadcasts.size == 1) {
                        CompletedBroadcastContent(broadcasts.first(), onRetryAll, onDismiss)
                    } else {
                        MultipleCompletedBroadcastContent(broadcasts, onRetryAll, onDismiss)
                    }
                } else {
                    if (broadcasts.size == 1) {
                        SingleBroadcastContent(broadcasts.first())
                    } else {
                        MultipleBroadcastsContent(broadcasts)
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleBroadcastContent(broadcast: BroadcastEvent) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = Icons.Default.Sync,
            contentDescription = stringRes(R.string.broadcasting),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringRes(R.string.broadcasting_name, broadcast.event.toKindName()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    text = stringRes(R.string.share_of, broadcast.results.size, broadcast.totalRelays),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(4.dp))

            val animatedProgress by animateFloatAsState(
                targetValue = broadcast.progress,
                animationSpec = tween(300),
                label = "progress",
            )

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun MultipleBroadcastsContent(broadcasts: ImmutableList<BroadcastEvent>) {
    val totalRelays = broadcasts.sumOf { it.totalRelays }
    val completedResponses = broadcasts.sumOf { it.results.size }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = Icons.Default.Sync,
            contentDescription = stringRes(R.string.broadcasting),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringRes(R.string.broadcasting_number_events, broadcasts.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = stringRes(R.string.share_of, completedResponses, totalRelays),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(4.dp))

            val progress = if (totalRelays > 0) completedResponses.toFloat() / totalRelays else 0f
            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = tween(300),
                label = "progress",
            )

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
fun CompletedBroadcastContent(
    broadcast: BroadcastEvent,
    onRetryAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Status colors (for icon tint only)
        val successColor = Color(0xFF22C55E)
        val warningColor = Color(0xFFF59E0B)

        val (statusIcon, iconTint) =
            when (broadcast.status) {
                BroadcastStatus.SUCCESS -> Icons.Default.CheckCircle to successColor
                BroadcastStatus.PARTIAL -> Icons.Default.Error to warningColor
                BroadcastStatus.FAILED -> Icons.Default.Error to MaterialTheme.colorScheme.error
                BroadcastStatus.IN_PROGRESS -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
            }

        // Small status icon (like BroadcastBanner)
        Icon(
            imageVector = statusIcon,
            contentDescription = broadcast.status.name,
            tint = iconTint,
            modifier = Modifier.size(18.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringRes(R.string.event_sent, broadcast.event.toKindName()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            if (broadcast.failedRelays.isNotEmpty()) {
                Text(
                    text = stringRes(R.string.tap_to_view_details),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = stringRes(R.string.share_of, broadcast.successCount, broadcast.totalRelays),
            style = MaterialTheme.typography.labelMedium,
            color = iconTint,
        )

        // Retry button for failures
        if (broadcast.failedRelays.isNotEmpty()) {
            IconButton(
                onClick = onRetryAll,
                modifier = Modifier.size(22.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringRes(R.string.retry_failed),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // Dismiss X
        Text(
            text = "×",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .clickable(onClick = onDismiss)
                    .padding(start = 2.dp),
        )
    }
}

@Composable
fun MultipleCompletedBroadcastContent(
    broadcasts: ImmutableList<BroadcastEvent>,
    onRetryAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Status colors (for icon tint only)
        val successColor = Color(0xFF22C55E)
        val warningColor = Color(0xFFF59E0B)

        val totalRelayCount = broadcasts.sumOf { it.totalRelays }
        val failedRelayCount = broadcasts.sumOf { it.failedRelays.size }
        val allSuccess = broadcasts.all { it.status == BroadcastStatus.SUCCESS }
        val allFailed = broadcasts.all { it.status == BroadcastStatus.FAILED }

        val (statusIcon, iconTint) =
            if (allSuccess) {
                Icons.Default.CheckCircle to successColor
            } else if (allFailed) {
                Icons.Default.Error to MaterialTheme.colorScheme.error
            } else {
                Icons.Default.Error to warningColor
            }

        // Small status icon (like BroadcastBanner)
        Icon(
            imageVector = statusIcon,
            contentDescription =
                if (allSuccess) {
                    stringRes(R.string.bradcasting_result_success)
                } else if (allFailed) {
                    stringRes(R.string.bradcasting_result_failure)
                } else {
                    stringRes(R.string.bradcasting_result_partial)
                },
            tint = iconTint,
            modifier = Modifier.size(18.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringRes(R.string.sent_number_events, broadcasts.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            if (failedRelayCount > 0) {
                Text(
                    text = stringRes(R.string.tap_to_view_details),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = stringRes(R.string.share_of, (totalRelayCount - failedRelayCount), totalRelayCount),
            style = MaterialTheme.typography.labelMedium,
            color = iconTint,
        )

        // Retry button for failures
        if (failedRelayCount > 0) {
            IconButton(
                onClick = onRetryAll,
                modifier = Modifier.size(22.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringRes(R.string.retry_failed),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // Dismiss X
        Text(
            text = "×",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .clickable(onClick = onDismiss)
                    .padding(start = 2.dp),
        )
    }
}

@Composable
fun Event.toKindName(): String =
    when (this) {
        is ReactionEvent -> stringRes(R.string.reaction)
        is RepostEvent -> stringRes(R.string.boost)
        is GenericRepostEvent -> stringRes(R.string.boost)
        is VoiceEvent -> stringRes(R.string.voice_post)
        is VoiceReplyEvent -> stringRes(R.string.voice_reply)
        is BookmarkListEvent -> stringRes(R.string.bookmarks)
        else -> stringRes(R.string.post)
    }

@Preview
@Composable
fun BroadcastBannerSingleEventPreview() {
    val repost =
        RepostEvent(
            id = "",
            pubKey = "",
            createdAt = TimeUtils.now(),
            tags = emptyArray(),
            content = "",
            sig = "",
        )
    ThemeComparisonColumn {
        Column {
            BroadcastBanner(
                persistentListOf(
                    BroadcastEvent(
                        id = UUID.randomUUID().toString(),
                        event = repost,
                        targetRelays = listOf(Constants.antiprimal, Constants.mom, Constants.nos),
                    ),
                ),
            )
            BroadcastBanner(
                persistentListOf(
                    BroadcastEvent(
                        id = UUID.randomUUID().toString(),
                        event = repost,
                        targetRelays = listOf(Constants.antiprimal, Constants.mom, Constants.nos),
                        results = mapOf(Constants.mom to RelayResult.Success),
                    ),
                ),
            )
            BroadcastBanner(
                persistentListOf(
                    BroadcastEvent(
                        id = UUID.randomUUID().toString(),
                        event = repost,
                        targetRelays = listOf(Constants.antiprimal, Constants.mom, Constants.nos),
                        results =
                            mapOf(
                                Constants.antiprimal to RelayResult.Success,
                                Constants.mom to RelayResult.Success,
                                Constants.nos to RelayResult.Success,
                            ),
                    ),
                ),
            )
            BroadcastBanner(
                persistentListOf(
                    BroadcastEvent(
                        id = UUID.randomUUID().toString(),
                        event = repost,
                        targetRelays = listOf(Constants.antiprimal, Constants.mom, Constants.nos),
                        results =
                            mapOf(
                                Constants.antiprimal to RelayResult.Success,
                                Constants.mom to RelayResult.Success,
                                Constants.nos to RelayResult.Success,
                            ),
                        status = BroadcastStatus.SUCCESS,
                    ),
                ),
            )
            BroadcastBanner(
                persistentListOf(
                    BroadcastEvent(
                        id = UUID.randomUUID().toString(),
                        event = repost,
                        targetRelays = listOf(Constants.antiprimal, Constants.mom, Constants.nos),
                        results =
                            mapOf(
                                Constants.antiprimal to RelayResult.Error("code"),
                                Constants.mom to RelayResult.Success,
                                Constants.nos to RelayResult.Success,
                            ),
                        status = BroadcastStatus.PARTIAL,
                    ),
                ),
            )
            BroadcastBanner(
                persistentListOf(
                    BroadcastEvent(
                        id = UUID.randomUUID().toString(),
                        event = repost,
                        targetRelays = listOf(Constants.antiprimal, Constants.mom, Constants.nos),
                        results =
                            mapOf(
                                Constants.antiprimal to RelayResult.Error("code"),
                                Constants.mom to RelayResult.Error("code"),
                                Constants.nos to RelayResult.Error("code"),
                            ),
                        status = BroadcastStatus.FAILED,
                    ),
                ),
            )
        }
    }
}

@Preview
@Composable
fun BroadcastBannerDoubleEventPreview() {
    val repost =
        RepostEvent(
            id = "",
            pubKey = "",
            createdAt = TimeUtils.now(),
            tags = emptyArray(),
            content = "",
            sig = "",
        )
    ThemeComparisonColumn {
        Column {
            BroadcastBanner(
                persistentListOf(
                    BroadcastEvent(
                        id = UUID.randomUUID().toString(),
                        event = repost,
                        targetRelays = listOf(Constants.antiprimal, Constants.mom, Constants.nos),
                    ),
                    BroadcastEvent(
                        id = UUID.randomUUID().toString(),
                        event = repost,
                        targetRelays = listOf(Constants.mom, Constants.nos),
                    ),
                ),
            )
            BroadcastBanner(
                persistentListOf(
                    BroadcastEvent(
                        id = UUID.randomUUID().toString(),
                        event = repost,
                        targetRelays = listOf(Constants.antiprimal, Constants.mom, Constants.nos),
                        results = mapOf(Constants.mom to RelayResult.Success),
                    ),
                    BroadcastEvent(
                        id = UUID.randomUUID().toString(),
                        event = repost,
                        targetRelays = listOf(Constants.mom, Constants.nos),
                        results = mapOf(Constants.mom to RelayResult.Success),
                    ),
                ),
            )
            BroadcastBanner(
                persistentListOf(
                    BroadcastEvent(
                        id = UUID.randomUUID().toString(),
                        event = repost,
                        targetRelays = listOf(Constants.antiprimal, Constants.mom, Constants.nos),
                        results =
                            mapOf(
                                Constants.antiprimal to RelayResult.Success,
                                Constants.mom to RelayResult.Success,
                                Constants.nos to RelayResult.Success,
                            ),
                    ),
                    BroadcastEvent(
                        id = UUID.randomUUID().toString(),
                        event = repost,
                        targetRelays = listOf(Constants.mom, Constants.nos),
                        results =
                            mapOf(
                                Constants.mom to RelayResult.Success,
                                Constants.nos to RelayResult.Success,
                            ),
                    ),
                ),
            )
            BroadcastBanner(
                persistentListOf(
                    BroadcastEvent(
                        id = UUID.randomUUID().toString(),
                        event = repost,
                        targetRelays = listOf(Constants.antiprimal, Constants.mom, Constants.nos),
                        results =
                            mapOf(
                                Constants.antiprimal to RelayResult.Success,
                                Constants.mom to RelayResult.Success,
                                Constants.nos to RelayResult.Success,
                            ),
                        status = BroadcastStatus.SUCCESS,
                    ),
                    BroadcastEvent(
                        id = UUID.randomUUID().toString(),
                        event = repost,
                        targetRelays = listOf(Constants.mom, Constants.nos),
                        results =
                            mapOf(
                                Constants.mom to RelayResult.Success,
                                Constants.nos to RelayResult.Success,
                            ),
                        status = BroadcastStatus.SUCCESS,
                    ),
                ),
            )

            BroadcastBanner(
                persistentListOf(
                    BroadcastEvent(
                        id = UUID.randomUUID().toString(),
                        event = repost,
                        targetRelays = listOf(Constants.antiprimal, Constants.mom, Constants.nos),
                        results =
                            mapOf(
                                Constants.antiprimal to RelayResult.Success,
                                Constants.mom to RelayResult.Success,
                                Constants.nos to RelayResult.Success,
                            ),
                        status = BroadcastStatus.SUCCESS,
                    ),
                    BroadcastEvent(
                        id = UUID.randomUUID().toString(),
                        event = repost,
                        targetRelays = listOf(Constants.mom, Constants.nos),
                        results =
                            mapOf(
                                Constants.mom to RelayResult.Error("code"),
                                Constants.nos to RelayResult.Success,
                            ),
                        status = BroadcastStatus.PARTIAL,
                    ),
                ),
            )

            BroadcastBanner(
                persistentListOf(
                    BroadcastEvent(
                        id = UUID.randomUUID().toString(),
                        event = repost,
                        targetRelays = listOf(Constants.antiprimal, Constants.mom, Constants.nos),
                        results =
                            mapOf(
                                Constants.antiprimal to RelayResult.Success,
                                Constants.mom to RelayResult.Error("code"),
                                Constants.nos to RelayResult.Success,
                            ),
                        status = BroadcastStatus.FAILED,
                    ),
                    BroadcastEvent(
                        id = UUID.randomUUID().toString(),
                        event = repost,
                        targetRelays = listOf(Constants.mom, Constants.nos),
                        results =
                            mapOf(
                                Constants.mom to RelayResult.Timeout,
                                Constants.nos to RelayResult.Success,
                            ),
                        status = BroadcastStatus.FAILED,
                    ),
                ),
            )
        }
    }
}
