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

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.defaults.Constants
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.service.broadcast.BroadcastEvent
import com.vitorpamplona.amethyst.commons.service.broadcast.BroadcastStatus
import com.vitorpamplona.amethyst.commons.service.broadcast.RelayResult
import com.vitorpamplona.amethyst.commons.service.pow.PoWEstimator
import com.vitorpamplona.amethyst.commons.service.pow.PoWJobState
import com.vitorpamplona.amethyst.service.pow.deviceHashesPerSecond
import com.vitorpamplona.amethyst.service.pow.formatTimeLeft
import com.vitorpamplona.amethyst.service.pow.powKindLabelRes
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.ui.theme.warningColor
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.OldBookmarkListEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import java.util.UUID

/**
 * Banner showing active broadcast progress.
 * Displayed above bottom navigation when events are being sent to relays.
 *
 * [miningJobs] is the NIP-13 pre-send phase: posts waiting for (or in the
 * middle of) proof-of-work mining, before their per-relay send states exist.
 */
@Composable
fun BroadcastBanner(
    broadcasts: ImmutableList<BroadcastEvent>,
    miningJobs: ImmutableList<PoWJobState> = persistentListOf(),
    onCancelJob: (String) -> Unit = {},
    onSendWithoutPow: (String) -> Unit = {},
    onTap: () -> Unit = {},
    onRetryAll: () -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = broadcasts.isNotEmpty() || miningJobs.isNotEmpty(),
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(200)),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(150)),
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            shape = RoundedCornerShape(20.dp),
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
                if (miningJobs.isNotEmpty()) {
                    MiningContent(miningJobs, onCancelJob, onSendWithoutPow)

                    if (broadcasts.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                    }
                }

                if (broadcasts.isNotEmpty()) {
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
}

@Composable
private fun MiningContent(
    miningJobs: ImmutableList<PoWJobState>,
    onCancelJob: (String) -> Unit,
    onSendWithoutPow: (String) -> Unit,
) {
    // the job whose × was tapped and is awaiting the send-or-discard choice.
    var confirmJobId by remember { mutableStateOf<String?>(null) }

    // drop the dialog if its job finished (mined and published) while it was open.
    LaunchedEffect(miningJobs) {
        if (confirmJobId != null && miningJobs.none { it.id == confirmJobId }) {
            confirmJobId = null
        }
    }

    confirmJobId?.let { jobId ->
        PoWCancelDialog(
            onSendWithoutPow = {
                onSendWithoutPow(jobId)
                confirmJobId = null
            },
            onDiscard = {
                onCancelJob(jobId)
                confirmJobId = null
            },
            onKeepMining = { confirmJobId = null },
        )
    }

    // one shared pulse for the gear — mining has no measurable progress, so
    // the animation is what says "the app is working right now".
    val pulse = rememberInfiniteTransition(label = "miningPulse")
    val gearAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(700),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "gearAlpha",
    )

    // 1 Hz clock driving the per-job elapsed labels; only ticks while some
    // job actually shows an elapsed time (queued-only banners don't need it).
    var nowSec by remember { mutableLongStateOf(TimeUtils.now()) }
    val anyMiningStarted = miningJobs.any { it.miningStartedAt != null }
    if (anyMiningStarted) {
        LaunchedEffect(Unit) {
            while (true) {
                nowSec = TimeUtils.now()
                delay(1_000)
            }
        }
    }

    // Benchmarked once and cached (~250 ms on a worker): turns each job's
    // difficulty into an expected duration so the bar has a predictable end.
    val context = LocalContext.current
    val hashRate by
        produceState<Double?>(initialValue = null) {
            value = deviceHashesPerSecond()
        }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                symbol = MaterialSymbols.Manufacturing,
                contentDescription = stringRes(R.string.pow_mining_title),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = gearAlpha),
                modifier = Modifier.size(18.dp),
            )

            Text(
                text = pluralStringResource(R.plurals.pow_mining_progress, miningJobs.size, miningJobs.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        miningJobs.forEach { job ->
            val elapsedSec = job.miningStartedAt?.let { (nowSec - it).coerceAtLeast(0) }
            val expectedSec = hashRate?.let { PoWEstimator.estimateSeconds(job.difficulty, it) }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Spacer(Modifier.width(26.dp))

                val base =
                    pluralStringResource(
                        if (job.isMining) R.plurals.pow_mining_job else R.plurals.pow_queued_job,
                        job.difficulty,
                        kindToName(job.kind),
                        job.difficulty,
                    )
                val suffix =
                    buildList {
                        elapsedSec?.let { add(DateUtils.formatElapsedTime(it)) }
                        if (elapsedSec != null && expectedSec != null) {
                            add(formatTimeLeft(context, expectedSec, elapsedSec))
                        }
                    }.joinToString(" • ")

                Text(
                    text = if (suffix.isEmpty()) base else "$base • $suffix",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                // once the nonce is found the job is signing/broadcasting —
                // there is nothing safe to abort anymore.
                if (job.isCancellable) {
                    IconButton(
                        // a template post can still go out un-mined, so ask first;
                        // opaque jobs (reactions, reposts) have no such choice and
                        // just cancel.
                        onClick = {
                            if (job.canSendWithoutPow) {
                                confirmJobId = job.id
                            } else {
                                onCancelJob(job.id)
                            }
                        },
                        modifier = Modifier.size(22.dp),
                    ) {
                        Icon(
                            symbol = MaterialSymbols.Close,
                            contentDescription = stringRes(R.string.pow_notification_cancel_all),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            // Predictable end: the bar fills over the estimated duration for
            // this difficulty. The search is memoryless, so once the mean is
            // passed there is no honest remainder to show — fall back to the
            // indeterminate sweep instead of a bar stuck at 100%.
            if (elapsedSec != null) {
                val fraction = expectedSec?.let { (elapsedSec / it).toFloat() }

                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(26.dp))
                    if (fraction != null && fraction < 1f) {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
            }
        }

        // nothing mining yet (all jobs waiting for a worker): keep the shared
        // activity sweep so the banner still reads as "working".
        if (!anyMiningStarted) {
            Spacer(Modifier.height(4.dp))

            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

/**
 * Asks what to do with a post whose proof of work is still mining: publish it
 * now without PoW, discard it, or keep mining. Only shown for jobs that carry
 * an un-mined fallback (template posts).
 */
@Composable
private fun PoWCancelDialog(
    onSendWithoutPow: () -> Unit,
    onDiscard: () -> Unit,
    onKeepMining: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepMining,
        title = { Text(stringRes(R.string.pow_cancel_dialog_title)) },
        text = { Text(stringRes(R.string.pow_cancel_dialog_message)) },
        confirmButton = {
            TextButton(onClick = onSendWithoutPow) {
                Text(stringRes(R.string.pow_cancel_dialog_send_without_pow))
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text(
                    text = stringRes(R.string.pow_cancel_dialog_discard),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

@Composable
private fun SingleBroadcastContent(broadcast: BroadcastEvent) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            symbol = MaterialSymbols.Sync,
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
            symbol = MaterialSymbols.Sync,
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
        val successColor = MaterialTheme.colorScheme.allGoodColor
        val warningColor = MaterialTheme.colorScheme.warningColor

        val (statusIcon, iconTint) =
            when (broadcast.status) {
                BroadcastStatus.SUCCESS -> MaterialSymbols.CheckCircle to successColor
                BroadcastStatus.PARTIAL -> MaterialSymbols.Error to warningColor
                BroadcastStatus.FAILED -> MaterialSymbols.Error to MaterialTheme.colorScheme.error
                BroadcastStatus.IN_PROGRESS -> MaterialSymbols.CheckCircle to MaterialTheme.colorScheme.primary
            }

        // Small status icon (like BroadcastBanner)
        Icon(
            symbol = statusIcon,
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
                    symbol = MaterialSymbols.Refresh,
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
        val successColor = MaterialTheme.colorScheme.allGoodColor
        val warningColor = MaterialTheme.colorScheme.warningColor

        val totalRelayCount = broadcasts.sumOf { it.totalRelays }
        val failedRelayCount = broadcasts.sumOf { it.failedRelays.size }
        val allSuccess = broadcasts.all { it.status == BroadcastStatus.SUCCESS }
        val allFailed = broadcasts.all { it.status == BroadcastStatus.FAILED }

        val (statusIcon, iconTint) =
            if (allSuccess) {
                MaterialSymbols.CheckCircle to successColor
            } else if (allFailed) {
                MaterialSymbols.Error to MaterialTheme.colorScheme.error
            } else {
                MaterialSymbols.Error to warningColor
            }

        // Small status icon (like BroadcastBanner)
        Icon(
            symbol = statusIcon,
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
                    symbol = MaterialSymbols.Refresh,
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
        is OldBookmarkListEvent -> stringRes(R.string.bookmarks)
        else -> stringRes(R.string.post)
    }

@Composable
fun kindToName(kind: Int): String = stringRes(powKindLabelRes(kind))

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
