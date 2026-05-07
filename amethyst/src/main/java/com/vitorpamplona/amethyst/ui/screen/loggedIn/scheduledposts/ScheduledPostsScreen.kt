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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.service.scheduledposts.ScheduledPost
import com.vitorpamplona.amethyst.service.scheduledposts.ScheduledPostStatus
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.ui.note.timeAheadNoDot
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
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
    val posts by viewModel.posts.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var pendingPublishId by remember { mutableStateOf<String?>(null) }
    var pendingCancelId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            ShorterTopAppBar(
                title = { Text("Scheduled posts") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) { ArrowBackIcon() }
                },
            )
        },
    ) { padding ->
        if (posts.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(posts, key = { it.id }) { post ->
                    ScheduledPostRow(
                        post = post,
                        onPublishNow = { pendingPublishId = post.id },
                        onCancel = { pendingCancelId = post.id },
                    )
                }
            }
        }
    }

    pendingPublishId?.let { id ->
        ConfirmDialog(
            title = "Send now?",
            message = "This post will publish to relays immediately. The original schedule will be discarded.",
            confirmLabel = "Send",
            onConfirm = {
                viewModel.publishNow(id, context)
                pendingPublishId = null
            },
            onDismiss = { pendingPublishId = null },
        )
    }

    pendingCancelId?.let { id ->
        ConfirmDialog(
            title = "Delete scheduled post?",
            message = "The post will not be published. This cannot be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                viewModel.cancel(id)
                pendingCancelId = null
            },
            onDismiss = { pendingCancelId = null },
        )
    }
}

@Composable
private fun ScheduledPostRow(
    post: ScheduledPost,
    onPublishNow: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
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
                    text = formatPublishMoment(post.publishAtSec, context),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = extractPreview(post),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            if (post.status == ScheduledPostStatus.FAILED && post.lastError != null) {
                Text(
                    text = "Error: ${post.lastError}",
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
                IconButton(onClick = onCancel) {
                    Icon(
                        symbol = MaterialSymbols.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                IconButton(onClick = onPublishNow) {
                    Icon(
                        symbol = MaterialSymbols.AutoMirrored.Send,
                        contentDescription = "Send now",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: ScheduledPostStatus) {
    val (label, tint) =
        when (status) {
            ScheduledPostStatus.PENDING -> "Scheduled" to Color(0xFF1E88E5)
            ScheduledPostStatus.PUBLISHING -> "Sending…" to Color(0xFFFFA000)
            ScheduledPostStatus.FAILED -> "Failed" to MaterialTheme.colorScheme.error
            ScheduledPostStatus.SENT -> "Sent" to Color(0xFF43A047)
            ScheduledPostStatus.CANCELLED -> "Cancelled" to MaterialTheme.colorScheme.onSurfaceVariant
        }
    AssistChip(
        onClick = {},
        label = { Text(label, fontWeight = FontWeight.Medium) },
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
                text = "No scheduled posts",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Compose a note and tap the clock icon to schedule it for later.",
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
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun extractPreview(post: ScheduledPost): String {
    val json = post.signedEventJson
    val needle = "\"content\":\""
    val start = json.indexOf(needle)
    if (start < 0) return ""
    val from = start + needle.length
    val sb = StringBuilder()
    var i = from
    while (i < json.length) {
        val c = json[i]
        if (c == '\\' && i + 1 < json.length) {
            when (json[i + 1]) {
                'n' -> sb.append('\n')
                't' -> sb.append('\t')
                '\\' -> sb.append('\\')
                '"' -> sb.append('"')
                else -> sb.append(json[i + 1])
            }
            i += 2
        } else if (c == '"') {
            break
        } else {
            sb.append(c)
            i++
        }
        if (sb.length > 200) break
    }
    return sb.toString().trim()
}

private fun formatPublishMoment(
    publishAtSec: Long,
    context: android.content.Context,
): String {
    val nowSec = System.currentTimeMillis() / 1000
    val deltaSec = publishAtSec - nowSec
    return when {
        deltaSec > 0 -> {
            "Publishes in ${timeAheadNoDot(publishAtSec, context)}"
        }

        else -> {
            val ago = -deltaSec
            val mins = TimeUnit.SECONDS.toMinutes(ago)
            when {
                mins < 1 -> "Due now"
                mins < 60 -> "Was due ${mins}m ago"
                else -> "Was due ${TimeUnit.SECONDS.toHours(ago)}h ago"
            }
        }
    }
}
