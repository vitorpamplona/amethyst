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
package com.vitorpamplona.amethyst.desktop.ui.note

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.moderation.HashtagSpamCheck
import com.vitorpamplona.amethyst.commons.moderation.LocalHashtagSpamSettings
import com.vitorpamplona.amethyst.commons.moderation.LocalSpamExemptKeys
import com.vitorpamplona.amethyst.commons.moderation.displayedEvent
import com.vitorpamplona.amethyst.commons.ui.note.CollapsedSpamNote
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.model.LocalDesktopIAccount
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.countHashtags
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarningReason
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitiveOrNSFW
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Wraps a note render in the hashtag-spam check.
 *
 * Reads [LocalHashtagSpamSettings] and [LocalSpamExemptKeys] from
 * composition. If the check trips and the user hasn't revealed this
 * specific note (per-note [rememberSaveable] keyed by id), renders
 * [CollapsedSpamNote] with author info pulled from [localCache];
 * otherwise calls [normal].
 *
 * @param displayedEvent The event whose body the [normal] block renders.
 *   For repost wrappers, callers should pass the *inner* event (via
 *   [Note.displayedEvent]). For search results that already hold an
 *   [Event] directly, pass it as-is.
 * @param noteIdHex Stable id used as the [rememberSaveable] key for the
 *   reveal flag — must match the id of whatever the [normal] block renders.
 * @param forceReveal When `true`, skips the check entirely and always
 *   renders [normal]. Used by thread-detail screens where the user
 *   explicitly opted into the root note.
 */
@Composable
fun SpamCheckedNoteRender(
    displayedEvent: Event?,
    noteIdHex: String,
    localCache: DesktopLocalCache?,
    forceReveal: Boolean = false,
    normal: @Composable () -> Unit,
) {
    val settings = LocalHashtagSpamSettings.current
    val enabled by settings.enabled.collectAsState()
    val threshold by settings.threshold.collectAsState()
    val exemptKeys = LocalSpamExemptKeys.current

    val isSpam =
        remember(noteIdHex, displayedEvent, enabled, threshold, exemptKeys) {
            HashtagSpamCheck.isHashtagSpam(
                displayedEvent = displayedEvent,
                authorPubkey = displayedEvent?.pubKey,
                enabled = enabled,
                threshold = threshold,
                exemptKeys = exemptKeys,
            )
        }

    // NIP-36 content warning: blur sensitive content unless the account opted
    // into "always show sensitive content".
    val account = LocalDesktopIAccount.current
    val showSensitiveFlow = remember(account) { account?.showSensitiveContentSetting ?: MutableStateFlow(null) }
    val showSensitive by showSensitiveFlow.collectAsState()
    val isSensitive = remember(noteIdHex, displayedEvent) { displayedEvent?.isSensitiveOrNSFW() == true }

    var revealed by rememberSaveable(noteIdHex) { mutableStateOf(false) }

    when {
        forceReveal || revealed -> normal()
        isSpam && displayedEvent != null -> {
            val authorPubkey = displayedEvent.pubKey
            val author = localCache?.getUserIfExists(authorPubkey)
            val displayName = author?.toBestDisplayName() ?: authorPubkey.take(8)
            val avatarUrl = author?.profilePicture()
            val hashtagCount = displayedEvent.tags.countHashtags()
            CollapsedSpamNote(
                authorPubkeyHex = authorPubkey,
                authorDisplayName = displayName,
                authorAvatarUrl = avatarUrl,
                hashtagCount = hashtagCount,
                threshold = threshold,
                onReveal = { revealed = true },
            )
        }
        isSensitive && showSensitive != true && displayedEvent != null -> {
            CollapsedContentWarning(
                reason = displayedEvent.contentWarningReason(),
                onReveal = { revealed = true },
            )
        }
        else -> normal()
    }
}

/**
 * Collapsed placeholder for a NIP-36 content-warning note. Mirrors the
 * spam-collapse reveal affordance: the body stays hidden behind a scrim with the
 * warning reason until the user taps "Show".
 */
@Composable
private fun CollapsedContentWarning(
    reason: String?,
    onReveal: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Sensitive content", style = MaterialTheme.typography.titleSmall)
                if (!reason.isNullOrBlank()) {
                    Text(
                        reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onReveal) { Text("Show") }
        }
    }
}

/**
 * [Note]-shaped convenience overload — resolves the displayed event
 * (unwrapping kind 6 / 16 reposts) and delegates to the primary helper.
 */
@Composable
fun SpamCheckedNoteRender(
    note: Note,
    localCache: DesktopLocalCache?,
    forceReveal: Boolean = false,
    normal: @Composable () -> Unit,
) {
    val displayedEvent = remember(note, note.event) { note.displayedEvent() }
    SpamCheckedNoteRender(
        displayedEvent = displayedEvent,
        noteIdHex = note.idHex,
        localCache = localCache,
        forceReveal = forceReveal,
        normal = normal,
    )
}
