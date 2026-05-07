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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.scheduledposts

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.service.scheduledposts.ScheduledPost
import com.vitorpamplona.amethyst.service.scheduledposts.ScheduledPostStatus
import com.vitorpamplona.amethyst.service.scheduledposts.ScheduledPostWorker
import com.vitorpamplona.amethyst.ui.components.SwipeToDeleteWithConfirmation
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.ui.note.timeAgoNoDot
import com.vitorpamplona.amethyst.ui.note.timeAheadNoDot
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScheduledPostsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val accountPubkey = accountViewModel.account.signer.pubKey
    val viewModel: ScheduledPostsViewModel =
        viewModel(key = "scheduled-posts-$accountPubkey") {
            ScheduledPostsViewModel.create(accountPubkey)
        }
    val groups by viewModel.groupedPosts.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var pendingPublishId by remember { mutableStateOf<String?>(null) }

    // Tick once per minute so relative-time strings ("publishes in 2h 13m")
    // refresh on a long-open list instead of being frozen at first composition.
    val nowSec by produceState(initialValue = System.currentTimeMillis() / 1000) {
        while (true) {
            delay(60_000)
            value = System.currentTimeMillis() / 1000
        }
    }

    Scaffold(
        topBar = {
            ShorterTopAppBar(
                title = { Text(stringRes(R.string.scheduled_posts)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) { ArrowBackIcon() }
                },
            )
        },
    ) { padding ->
        if (groups.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding))
        } else {
            val today = remember(nowSec) { LocalDate.now(ZoneId.systemDefault()) }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                groups.forEach { group ->
                    stickyHeader(key = group.day) {
                        DayHeader(group.day, today, context)
                    }
                    items(group.posts, key = { it.id }) { post ->
                        SwipeToDeleteWithConfirmation(
                            modifier = Modifier.fillMaxWidth().animateContentSize(),
                            onDelete = { viewModel.cancel(post.id) },
                        ) {
                            ScheduledPostRow(
                                post = post,
                                nowSec = nowSec,
                                onPublishNow = { pendingPublishId = post.id },
                            )
                        }
                    }
                }
            }
        }
    }

    pendingPublishId?.let { id ->
        ConfirmDialog(
            title = stringRes(R.string.scheduled_posts_send_now_title),
            message = stringRes(R.string.scheduled_posts_send_now_message),
            confirmLabel = stringRes(R.string.scheduled_posts_send_now_confirm),
            onConfirm = {
                viewModel.publishNow(id)
                ScheduledPostWorker.scheduleCatchUp(context)
                pendingPublishId = null
            },
            onDismiss = { pendingPublishId = null },
        )
    }
}

@Composable
private fun ScheduledPostRow(
    post: ScheduledPost,
    nowSec: Long,
    onPublishNow: () -> Unit,
) {
    val context = LocalContext.current
    val preview = remember(post) { extractPreview(post) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                StatusChip(post.status)
                Text(
                    text = formatAtTime(post.publishAtSec, nowSec, context),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = preview,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            if (post.status == ScheduledPostStatus.FAILED && post.lastError != null) {
                Text(
                    text = stringRes(R.string.scheduled_posts_error_prefix, post.lastError),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = onPublishNow) {
                    Icon(
                        symbol = MaterialSymbols.AutoMirrored.Send,
                        contentDescription = stringRes(R.string.scheduled_posts_action_send_now),
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(
    day: LocalDate,
    today: LocalDate,
    context: android.content.Context,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = formatDayHeader(day, today, context),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun StatusChip(status: ScheduledPostStatus) {
    val (labelRes, tint) =
        when (status) {
            ScheduledPostStatus.PENDING -> R.string.scheduled_posts_status_pending to MaterialTheme.colorScheme.primary
            ScheduledPostStatus.PUBLISHING -> R.string.scheduled_posts_status_publishing to MaterialTheme.colorScheme.tertiary
            ScheduledPostStatus.FAILED -> R.string.scheduled_posts_status_failed to MaterialTheme.colorScheme.error
            ScheduledPostStatus.SENT -> R.string.scheduled_posts_status_sent to MaterialTheme.colorScheme.tertiary
            ScheduledPostStatus.CANCELLED -> R.string.scheduled_posts_status_cancelled to MaterialTheme.colorScheme.onSurfaceVariant
        }
    AssistChip(
        onClick = {},
        label = { Text(stringRes(labelRes), fontWeight = FontWeight.Medium) },
        colors =
            AssistChipDefaults.assistChipColors(
                labelColor = tint,
            ),
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                symbol = MaterialSymbols.Schedule,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringRes(R.string.scheduled_posts_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringRes(R.string.scheduled_posts_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(R.string.cancel)) }
        },
    )
}

private fun extractPreview(post: ScheduledPost): String =
    runCatching {
        Event
            .fromJson(post.signedEventJson)
            .content
            .take(200)
            .trim()
    }.getOrDefault("")

private val shortTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val fullDateFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

private fun formatAtTime(
    publishAtSec: Long,
    nowSec: Long,
    context: android.content.Context,
): String {
    val absolute =
        Instant
            .ofEpochSecond(publishAtSec)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(shortTimeFormatter)
    return if (publishAtSec > nowSec) {
        stringRes(context, R.string.scheduled_posts_at_time, absolute, timeAheadNoDot(publishAtSec, context))
    } else {
        stringRes(context, R.string.scheduled_posts_at_time_past, absolute, timeAgoNoDot(publishAtSec, context).trim())
    }
}

private fun formatDayHeader(
    day: LocalDate,
    today: LocalDate,
    context: android.content.Context,
): String =
    when (day) {
        today -> stringRes(context, R.string.scheduled_posts_day_today)
        today.plusDays(1) -> stringRes(context, R.string.scheduled_posts_day_tomorrow)
        else -> day.format(fullDateFormatter)
    }
