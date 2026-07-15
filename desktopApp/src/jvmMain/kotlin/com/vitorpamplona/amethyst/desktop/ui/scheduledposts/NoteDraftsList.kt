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
package com.vitorpamplona.amethyst.desktop.ui.scheduledposts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.service.drafts.LocalNoteDraftStore
import com.vitorpamplona.amethyst.desktop.subscriptions.FilterBuilders
import com.vitorpamplona.amethyst.desktop.subscriptions.SubscriptionConfig
import com.vitorpamplona.amethyst.desktop.subscriptions.rememberSubscription
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip37Drafts.DraftEventCache
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One row in the merged drafts list.
 *
 * @param dTag NIP-37 identifier — the dedup key across the local store and synced events.
 * @param content the plaintext body to preview / re-open.
 * @param updatedAt epoch-second used for ordering.
 * @param synced true if this draft has a kind-31234 twin on relays.
 */
private data class MergedDraft(
    val dTag: String,
    val content: String,
    val updatedAt: Long,
    val synced: Boolean,
)

/**
 * The short-note Drafts list: local drafts written by the composer merged with the
 * user's synced NIP-37 (kind-31234) drafts. Rows are deduped by `dTag` (a local row and
 * its synced twin collapse to one); synced rows get a small cloud badge.
 *
 * Subscription and decryption are scoped to this composable's lifecycle via
 * [rememberSubscription]. Decryption failures are swallowed per-event so one bad draft
 * doesn't blank the list.
 */
@Composable
fun NoteDraftsList(
    relayManager: DesktopRelayConnectionManager,
    account: AccountState.LoggedIn,
    onOpenDraft: (dTag: String, content: String) -> Unit,
) {
    val noteDraftStore = LocalNoteDraftStore.current
    val localDrafts by noteDraftStore.drafts.collectAsState()
    val scope = rememberCoroutineScope()

    val connectedRelays by relayManager.connectedRelays.collectAsState()

    // Decrypt cache keyed on the signer identity; re-created if the account changes.
    val draftCache = remember(account.pubKeyHex) { DraftEventCache(account.signer) }

    // dTag -> decrypted synced draft. mutableStateMap so async decrypt results recompose.
    val syncedByDTag = remember(account.pubKeyHex) { mutableStateMapOf<String, MergedDraft>() }

    // Subscribe to the account's own kind-31234 drafts.
    rememberSubscription(connectedRelays, account.pubKeyHex, relayManager = relayManager) {
        if (connectedRelays.isNotEmpty()) {
            SubscriptionConfig(
                subId = "note-drafts-${account.pubKeyHex.take(8)}",
                filters =
                    listOf(
                        FilterBuilders.byAuthors(
                            authors = listOf(account.pubKeyHex),
                            kinds = listOf(DraftWrapEvent.KIND),
                            limit = 100,
                        ),
                    ),
                relays = connectedRelays,
                onEvent = { event, _, _, _ ->
                    if (event is DraftWrapEvent) {
                        val wrap: DraftWrapEvent = event
                        val dTag = wrap.dTag()
                        if (wrap.isDeleted()) {
                            syncedByDTag.remove(dTag)
                        } else {
                            scope.launch {
                                try {
                                    val inner = draftCache.cachedDraft(wrap)
                                    if (inner != null) {
                                        val existing = syncedByDTag[dTag]
                                        // Keep the newest for a given dTag.
                                        if (existing == null || wrap.createdAt >= existing.updatedAt) {
                                            syncedByDTag[dTag] =
                                                MergedDraft(
                                                    dTag = dTag,
                                                    content = inner.content,
                                                    updatedAt = wrap.createdAt,
                                                    synced = true,
                                                )
                                        }
                                    }
                                } catch (_: Exception) {
                                    // Undecryptable draft (e.g. signer offline) — skip it.
                                }
                            }
                        }
                    }
                },
            )
        } else {
            null
        }
    }

    // Merge local + synced by dTag. A dTag present in both shows once, flagged synced.
    val merged: List<MergedDraft> =
        remember(localDrafts, syncedByDTag.toMap(), account.pubKeyHex) {
            val byTag = LinkedHashMap<String, MergedDraft>()
            localDrafts
                // Only show drafts owned by the current account. Legacy rows with a blank
                // pubkey are scoped out entirely so account A's plaintext never leaks to B.
                .filter { it.accountPubkey == account.pubKeyHex }
                .forEach { d ->
                    byTag[d.dTag] =
                        MergedDraft(
                            dTag = d.dTag,
                            content = d.content,
                            updatedAt = d.updatedAt,
                            synced = d.synced,
                        )
                }
            syncedByDTag.values.forEach { s ->
                val existing = byTag[s.dTag]
                if (existing == null) {
                    byTag[s.dTag] = s
                } else {
                    // Local wins for content/updatedAt; mark synced true.
                    byTag[s.dTag] = existing.copy(synced = true)
                }
            }
            byTag.values.sortedByDescending { it.updatedAt }
        }

    if (merged.isEmpty()) {
        EmptyState(
            title = "No note drafts yet",
            description = "Write a note and choose \"Save as draft\" to keep it here.",
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(merged, key = { it.dTag }) { draft ->
                NoteDraftCard(
                    draft = draft,
                    onClick = { onOpenDraft(draft.dTag, draft.content) },
                    onSend = {
                        scope.launch {
                            try {
                                publishDraftNow(
                                    content = draft.content,
                                    account = account,
                                    relayManager = relayManager,
                                )
                                // Only drop the draft once publish has succeeded.
                                noteDraftStore.delete(draft.dTag)
                                syncedByDTag.remove(draft.dTag)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to publish draft ${draft.dTag}", e)
                            }
                        }
                    },
                    onDelete = {
                        scope.launch { noteDraftStore.delete(draft.dTag) }
                        syncedByDTag.remove(draft.dTag)
                    },
                )
            }
        }
    }
}

@Composable
private fun NoteDraftCard(
    draft: MergedDraft,
    onClick: () -> Unit,
    onSend: () -> Unit,
    onDelete: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = draft.content.ifBlank { "Empty draft" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (draft.synced) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            MaterialSymbols.CloudSync,
                            contentDescription = "Synced draft",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = "Synced",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            IconButton(onClick = onSend) {
                Icon(
                    MaterialSymbols.AutoMirrored.Send,
                    contentDescription = "Publish draft now",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    MaterialSymbols.Delete,
                    contentDescription = "Delete draft",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private const val TAG = "NoteDraftsList"

/**
 * Build, sign, and immediately publish a plain text note from a draft's body — the same
 * template/signer/relay resolution the composer's publish path uses (see
 * `ComposeNoteDialog.publishNote`). Publishing goes to the currently connected relays.
 *
 * Signing is a suspend call that can fail when a remote NIP-46 signer is offline; the
 * exception propagates so the caller keeps the draft on failure and only deletes it on
 * success.
 */
private suspend fun publishDraftNow(
    content: String,
    account: AccountState.LoggedIn,
    relayManager: DesktopRelayConnectionManager,
) {
    withContext(Dispatchers.IO) {
        if (account.isReadOnly) {
            throw IllegalStateException("Cannot post in read-only mode")
        }

        val template =
            TextNoteEvent.build(content) {
                hashtags(findHashtags(content))
                references(findURLs(content))
            }

        val signedEvent = account.signer.sign(template)
        val relays = relayManager.connectedRelays.value
        relayManager.publish(signedEvent, relays)
    }
}
