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

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarSize
import com.vitorpamplona.amethyst.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.ui.note.timeAgoNoDot
import com.vitorpamplona.amethyst.ui.note.timeAheadNoDot
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
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
    val totalActive by viewModel.totalActive.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var expandedId by remember { mutableStateOf<String?>(null) }

    // Tick once per minute so relative-time strings ("publishes in 2h 13m")
    // refresh on a long-open list instead of being frozen at first composition.
    val nowSec by produceState(initialValue = System.currentTimeMillis() / 1000) {
        while (true) {
            delay(60_000)
            value = System.currentTimeMillis() / 1000
        }
    }

    val dueSoonCount by remember {
        derivedStateOf {
            groups
                .flatMap { it.posts }
                .count { it.publishAtSec - nowSec in 1..URGENT_THRESHOLD_SEC }
        }
    }

    val barHeight = if (totalActive > 0) 64.dp else TopBarSize

    Scaffold(
        topBar = {
            ShorterTopAppBar(
                expandedHeight = barHeight,
                title = {
                    Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                        Text(stringRes(R.string.scheduled_posts))
                        if (totalActive > 0) {
                            val queuedText =
                                pluralStringResource(
                                    id = R.plurals.scheduled_posts_subtitle_queued,
                                    count = totalActive,
                                    totalActive,
                                )
                            val dueText =
                                if (dueSoonCount > 0) {
                                    pluralStringResource(
                                        id = R.plurals.scheduled_posts_subtitle_due_suffix,
                                        count = dueSoonCount,
                                        dueSoonCount,
                                    )
                                } else {
                                    ""
                                }
                            Text(
                                text = queuedText + dueText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) { ArrowBackIcon() }
                },
            )
        },
    ) { padding ->
        if (groups.isEmpty()) {
            EmptyState(onCompose = { nav.nav(Route.NewShortNote()) }, modifier = Modifier.padding(padding))
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
                        val isExpanded = expandedId == post.id
                        val rowAlpha by animateFloatAsState(
                            targetValue = if (expandedId != null && !isExpanded) 0.65f else 1f,
                            label = "row-alpha",
                        )
                        Box(modifier = Modifier.alpha(rowAlpha)) {
                            if (isExpanded) {
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .animateContentSize(),
                                ) {
                                    ScheduledPostCardCollapsed(
                                        post = post,
                                        nowSec = nowSec,
                                        onClick = { expandedId = null },
                                    )
                                    ScheduledPostCardExpandedPanel(
                                        post = post,
                                        onPublishNow = {
                                            viewModel.publishNow(post.id)
                                            ScheduledPostWorker.scheduleCatchUp(context)
                                            expandedId = null
                                        },
                                        onDelete = {
                                            viewModel.cancel(post.id)
                                            expandedId = null
                                        },
                                    )
                                }
                            } else {
                                SwipeToDeleteWithConfirmation(
                                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                                    onDelete = { viewModel.cancel(post.id) },
                                    confirmLabelRes = R.string.quick_action_delete,
                                ) {
                                    ScheduledPostCardCollapsed(
                                        post = post,
                                        nowSec = nowSec,
                                        onClick = { expandedId = post.id },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val URGENT_THRESHOLD_SEC = 3600L

// Tailwind amber-400. Material 3 has no amber slot, but the publishing-pulse
// reads better than `tertiary` against a violet card.
private val PublishingAmber = Color(0xFFFBBF24)

@Composable
private fun Modifier.urgentEdge(enabled: Boolean): Modifier {
    if (!enabled) return this
    val gradientStart = MaterialTheme.colorScheme.primary
    val gradientEnd = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    val brush =
        remember(gradientStart, gradientEnd) {
            Brush.verticalGradient(listOf(gradientStart, gradientEnd))
        }
    return this.drawBehind {
        drawRect(
            brush = brush,
            topLeft = Offset(0f, 8.dp.toPx()),
            size = Size(3.dp.toPx(), size.height - 16.dp.toPx()),
        )
    }
}

@Composable
private fun ScheduledPostCardCollapsed(
    post: ScheduledPost,
    nowSec: Long,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val preview = remember(post.id) { extractContentPreview(post, 200) }
    val media = remember(post.id) { extractFirstMediaUrl(post) }
    val relayCountText =
        pluralStringResource(
            id = R.plurals.scheduled_posts_relay_count,
            count = post.relayUrls.size,
            post.relayUrls.size,
        )

    val isFailed = post.status == ScheduledPostStatus.FAILED
    // Composite the tint over surface so the card is opaque — otherwise the
    // SwipeToDismissBox background ("Delete" / "Cancel") bleeds through at rest.
    val surface = MaterialTheme.colorScheme.surface
    val containerColor =
        if (isFailed) {
            MaterialTheme.colorScheme.error
                .copy(alpha = 0.06f)
                .compositeOver(surface)
        } else {
            MaterialTheme.colorScheme.primary
                .copy(alpha = 0.06f)
                .compositeOver(surface)
        }
    val borderColor =
        if (isFailed) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        }

    Card(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .urgentEdge(!isFailed && post.publishAtSec - nowSec in 1..URGENT_THRESHOLD_SEC),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                StatusPill(post.status)
                Text(
                    text = formatAtTime(post.publishAtSec, nowSec, context),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (media != null) {
                    MediaThumbnail(media)
                }
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = relayCountText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScheduledPostCardExpandedPanel(
    post: ScheduledPost,
    onPublishNow: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val eventId = remember(post.id) { extractEventId(post) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))

        Column {
            SectionLabel(stringRes(R.string.relays))
            post.relayUrls.forEach { url ->
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }

        if (eventId != null) {
            Column {
                SectionLabel(stringRes(R.string.quick_action_copy_note_id))
                Text(
                    text = eventId,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    clipboard.setText(AnnotatedString(eventId))
                                    Toast
                                        .makeText(
                                            context,
                                            stringRes(context, R.string.scheduled_posts_event_id_copied),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                },
                            ),
                )
            }
        }

        val err = post.lastError
        if (post.status == ScheduledPostStatus.FAILED && !err.isNullOrBlank()) {
            Text(
                text = stringRes(R.string.scheduled_posts_error_prefix, err),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onPublishNow,
                enabled = post.status != ScheduledPostStatus.PUBLISHING,
                modifier = Modifier.weight(1f),
            ) {
                val labelRes =
                    when (post.status) {
                        ScheduledPostStatus.FAILED -> R.string.retry
                        ScheduledPostStatus.PUBLISHING -> R.string.scheduled_posts_status_publishing
                        else -> R.string.scheduled_posts_action_send_now
                    }
                Text(stringRes(labelRes))
            }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text(stringRes(R.string.quick_action_delete))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val locale = LocalConfiguration.current.locales[0]
    Text(
        text = text.uppercase(locale),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
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
private fun StatusPill(status: ScheduledPostStatus) {
    val (labelRes, color, pulse) =
        when (status) {
            ScheduledPostStatus.PENDING -> {
                Triple(R.string.scheduled_posts_status_pending, MaterialTheme.colorScheme.primary, false)
            }

            ScheduledPostStatus.PUBLISHING -> {
                Triple(R.string.scheduled_posts_status_publishing, PublishingAmber, true)
            }

            ScheduledPostStatus.FAILED -> {
                Triple(R.string.scheduled_posts_status_failed, MaterialTheme.colorScheme.error, false)
            }

            ScheduledPostStatus.SENT -> {
                Triple(R.string.scheduled_posts_status_sent, MaterialTheme.colorScheme.tertiary, false)
            }

            ScheduledPostStatus.CANCELLED -> {
                Triple(R.string.scheduled_posts_status_cancelled, MaterialTheme.colorScheme.onSurfaceVariant, false)
            }
        }
    val dotAlpha =
        if (pulse) {
            val transition = rememberInfiniteTransition(label = "publishing-pulse")
            transition
                .animateFloat(
                    initialValue = 1f,
                    targetValue = 0.35f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = 1400, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "publishing-pulse-alpha",
                ).value
        } else {
            1f
        }

    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.18f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = dotAlpha)),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringRes(labelRes),
                color = color,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun EmptyState(
    onCompose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    symbol = MaterialSymbols.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = stringRes(R.string.scheduled_posts_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringRes(R.string.scheduled_posts_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onCompose) {
                Text(stringRes(R.string.new_post))
            }
        }
    }
}

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
        today -> stringRes(context, R.string.today)
        today.plusDays(1) -> stringRes(context, R.string.scheduled_posts_day_tomorrow)
        else -> day.format(fullDateFormatter)
    }
