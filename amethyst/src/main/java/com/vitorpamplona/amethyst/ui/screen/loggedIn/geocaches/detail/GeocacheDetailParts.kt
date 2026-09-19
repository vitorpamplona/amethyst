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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.geocaches.detail

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.geocache_archived
import com.vitorpamplona.amethyst.commons.resources.geocache_found_it
import com.vitorpamplona.amethyst.commons.resources.geocache_ftf_won_by
import com.vitorpamplona.amethyst.commons.resources.geocache_invalid_proof
import com.vitorpamplona.amethyst.commons.resources.geocache_log_type_dnf
import com.vitorpamplona.amethyst.commons.resources.geocache_log_type_maintenance
import com.vitorpamplona.amethyst.commons.resources.geocache_log_type_note
import com.vitorpamplona.amethyst.commons.resources.geocache_verified_find
import com.vitorpamplona.amethyst.commons.ui.note.GeocacheChip
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNote
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.LoadUser
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nipCCGeocaching.comment.declaredGeocacheLogType
import com.vitorpamplona.quartz.nipCCGeocaching.comment.tags.GeocacheLogType
import com.vitorpamplona.quartz.nipCCGeocaching.firstToFind.FirstToFindResolver
import com.vitorpamplona.quartz.nipCCGeocaching.foundLog.GeocacheFoundLogEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import com.vitorpamplona.quartz.nipCCGeocaching.verification.GeocacheVerificationValidator
import org.jetbrains.compose.resources.stringResource

/**
 * Every log filed under a cache, split the way the screen needs it.
 *
 * [winner] is the pubkey holding the exclusive first-to-find claim, which is the owner's `F` tag
 * when they have published one and the earliest *verified* log otherwise — never simply the
 * earliest log, because an unverified "I was first" is somebody's word.
 */
@Immutable
data class GeocacheLogs(
    val all: List<Note>,
    val finds: List<GeocacheFoundLogEvent>,
    val winner: String?,
)

/**
 * The cache's logs, recomputed whenever the note's reply list changes.
 *
 * `Note.replies` is a plain `var` with no snapshot behind it, so it cannot drive a recomposition
 * on its own. Observing the cache note does: a log arriving is filed under this note by
 * `LocalCache.computeReplyTo` and bumps the note's own state, which is what this reads through.
 */
@Composable
fun rememberGeocacheLogs(
    cacheNote: Note,
    accountViewModel: AccountViewModel,
): GeocacheLogs {
    val noteState by observeNote(cacheNote, accountViewModel)
    val listing = noteState.note.event as? GeocacheListingEvent

    return remember(noteState, listing) {
        val replies = noteState.note.replies
        val sorted = replies.sortedByDescending { it.createdAt() ?: 0L }
        val finds = replies.mapNotNull { it.event as? GeocacheFoundLogEvent }

        GeocacheLogs(
            all = sorted,
            finds = finds,
            winner = listing?.let { FirstToFindResolver.winnerPubKey(it, finds) },
        )
    }
}

/** Who hid it, with a tap through to their profile. */
@Composable
fun GeocacheOwnerRow(
    listing: GeocacheListingEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadUser(listing.pubKey, accountViewModel) { user ->
        if (user != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClickableUserPicture(user, Size25dp, accountViewModel)
                UsernameDisplay(user, accountViewModel = accountViewModel)
            }
        }
    }
}

/** The first-to-find status line, naming the holder rather than printing a hex key at them. */
@Composable
fun GeocacheWinnerStrip(
    winner: String,
    accountViewModel: AccountViewModel,
) {
    LoadUser(winner, accountViewModel) { user ->
        Text(
            text = stringResource(Res.string.geocache_ftf_won_by, user?.toBestDisplayName() ?: winner.take(8)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Shares the cache as `nostr:naddr…`, which is how it opens in another NIP-CC client. */
@Composable
fun GeocacheShareAction(address: Address) {
    val context = LocalContext.current
    IconButton(onClick = {
        val naddr = NAddress.create(address.kind, address.pubKeyHex, address.dTag, null)
        val intent =
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "nostr:$naddr")
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }) {
        Icon(
            symbol = MaterialSymbols.AutoMirrored.Send,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * One row of the cache's history: a kind 7516 find, or a kind 1111 comment carrying a
 * `dnf`/`note`/`maintenance` type.
 *
 * A find's proof badge is computed, never assumed — the same rule the feed card follows. The
 * validation runs here rather than in a background state because the thread is short and already
 * behind a scroll; a cache with hundreds of verified logs would want the [produceState] treatment
 * the feed card uses.
 */
@Composable
fun GeocacheLogRow(
    log: Note,
    listing: GeocacheListingEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = log.event ?: return
    val author = log.author?.pubkeyHex ?: event.pubKey

    Column(
        Modifier
            .fillMaxWidth()
            .clickable { nav.nav(Route.Note(log.idHex)) }
            .padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LoadUser(author, accountViewModel) { user ->
                if (user != null) {
                    ClickableUserPicture(user, Size25dp, accountViewModel)
                    UsernameDisplay(user, accountViewModel = accountViewModel)
                }
            }

            Spacer(Modifier.weight(1f))

            when (event) {
                is GeocacheFoundLogEvent -> GeocacheFindBadge(event, listing)
                is CommentEvent -> GeocacheCommentBadge(event)
            }
        }

        if (event.content.isNotBlank()) {
            Text(
                text = event.content.trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun GeocacheFindBadge(
    log: GeocacheFoundLogEvent,
    listing: GeocacheListingEvent,
) {
    val green = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    when {
        !log.hasVerificationAttached() -> GeocacheChip(stringResource(Res.string.geocache_found_it), muted)
        GeocacheVerificationValidator.isValid(log, listing) ->
            GeocacheChip("🔐  " + stringResource(Res.string.geocache_verified_find), green, strong = true)
        else -> GeocacheChip("⚠️  " + stringResource(Res.string.geocache_invalid_proof), muted, dim = true)
    }
}

@Composable
private fun GeocacheCommentBadge(comment: CommentEvent) {
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    val label =
        when (comment.tags.declaredGeocacheLogType()) {
            GeocacheLogType.DNF -> stringResource(Res.string.geocache_log_type_dnf)
            GeocacheLogType.NOTE -> stringResource(Res.string.geocache_log_type_note)
            GeocacheLogType.MAINTENANCE -> stringResource(Res.string.geocache_log_type_maintenance)
            // The owner's own retirement note. The listing's `archived` type is what actually
            // takes the cache out of play, and the screen already says so at the top, so
            // repeating it on the comment row would be noise.
            GeocacheLogType.ARCHIVED -> stringResource(Res.string.geocache_archived)
            null -> return
        }

    GeocacheChip(label, muted, dim = true)
}
