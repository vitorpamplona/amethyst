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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.followpacks.BulkFollowAction
import com.vitorpamplona.amethyst.desktop.followpacks.BulkFollowPreview
import com.vitorpamplona.amethyst.desktop.model.DesktopIAccount
import com.vitorpamplona.amethyst.desktop.network.RelayConnectionManager
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Confirm dialog for bulk follow / unfollow. Detects current state and switches
 * the primary action between "Follow N" and "Unfollow M" automatically.
 *
 * Cancel is the safer default.
 */
@Composable
fun FollowAllConfirmDialog(
    pack: FollowListEvent,
    iAccount: DesktopIAccount,
    cache: DesktopLocalCache,
    relayManager: RelayConnectionManager,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit = {},
    onFailure: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var publishing by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<BulkFollowPreview?>(null) }

    // Recompute on every kind-3 change so the dialog reflects current state
    val current by iAccount.kind3FollowList.flow.collectAsState()
    LaunchedEffect(pack.id, current.authors) {
        preview =
            BulkFollowAction.computePreview(pack, cache, current.authors, iAccount.pubKey)
    }

    val p = preview ?: return
    val isFullyFollowed = p.newCount == 0 && p.existingCount > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isFullyFollowed) "Unfollow this pack?" else "Follow this pack?",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column {
                Text(
                    text = pack.title()?.ifBlank { null } ?: "Untitled pack",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text =
                        when {
                            isFullyFollowed ->
                                "Unfollow all ${p.existingCount} ${if (p.existingCount == 1) "person" else "people"} in this pack?"
                            p.newCount == 0 -> "Nothing to do."
                            else ->
                                "Follow ${p.newCount} new ${if (p.newCount == 1) "person" else "people"}" +
                                    if (p.existingCount > 0) " (${p.existingCount} already followed)." else "."
                        },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !publishing && (p.newCount > 0 || isFullyFollowed),
                onClick = {
                    publishing = true
                    scope.launch {
                        val freshCurrent = iAccount.kind3FollowList.flow.value.authors
                        val refreshed =
                            BulkFollowAction.computePreview(pack, cache, freshCurrent, iAccount.pubKey)
                        val fullyFollowedNow = refreshed.newCount == 0 && refreshed.existingCount > 0
                        val ok =
                            if (fullyFollowedNow) {
                                BulkFollowAction.commitUnfollowAll(
                                    pack = pack,
                                    iAccount = iAccount,
                                    relayManager = relayManager,
                                    cache = cache,
                                )
                            } else {
                                BulkFollowAction.commit(
                                    usersToAdd = refreshed.newUsers,
                                    iAccount = iAccount,
                                    relayManager = relayManager,
                                    cache = cache,
                                )
                            }
                        delay(400)
                        publishing = false
                        if (ok) onSuccess() else onFailure()
                        onDismiss()
                    }
                },
            ) {
                Text(
                    when {
                        publishing -> "Publishing…"
                        isFullyFollowed -> "Unfollow ${p.existingCount}"
                        else -> "Follow ${p.newCount}"
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !publishing) {
                Text("Cancel")
            }
        },
    )
}
