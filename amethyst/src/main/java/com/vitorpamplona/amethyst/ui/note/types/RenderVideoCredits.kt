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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.ui.components.ClickableTextColor
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteEvent
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.LoadUser
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.HalfTopPadding
import com.vitorpamplona.amethyst.ui.theme.lessImportantLink
import com.vitorpamplona.quartz.experimental.videoCollaboration.VideoCollaborationEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip71Video.VideoEvent
import com.vitorpamplona.quartz.nip71Video.credits.CreditTarget
import com.vitorpamplona.quartz.nip71Video.credits.VideoCredit

/**
 * The line of credits under a video: who it features, who inspired it, whose audio it reuses.
 *
 * Every entry is a link, because the credit is only worth printing if it takes you to the person
 * or the work. A credited person who has published a [VideoCollaborationEvent] for this video gets
 * a check beside their name — they are not merely tagged, they took the credit.
 *
 * A `p` tag marked `mention` is kept even though the description may name the same person: on
 * divine.video the description writes a plain `@handle`, which no client can turn into a link, so
 * the tag is the only thing that resolves to an actual profile. The cost is a chip that repeats a
 * `nostr:` mention already rendered inline, which is redundancy rather than a wrong credit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RenderVideoCredits(
    videoEvent: VideoEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val credits = remember(videoEvent) { videoEvent.credits() }
    if (credits.isEmpty()) return

    // Only an addressable video has a coordinate for a collaborator to answer, so only there can
    // an acceptance be looked up at all.
    val videoAddress = remember(videoEvent) { (videoEvent as? AddressableEvent)?.address() }

    FlowRow(modifier = HalfTopPadding) {
        credits.forEach { credit ->
            key(credit.target) {
                RenderVideoCredit(credit, videoAddress, accountViewModel, nav)
            }
        }
    }
}

@Composable
private fun RenderVideoCredit(
    credit: VideoCredit,
    videoAddress: Address?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (val target = credit.target) {
        is CreditTarget.Person ->
            LoadUser(target.pubKey, accountViewModel) { user ->
                // Until the profile arrives there is no name to print, and a hex key in the
                // credits reads as noise rather than as a person.
                user?.let {
                    CreditRow(
                        label = personLabel(credit.label),
                        // toBestDisplayName() is read inside the row so a profile that lands later
                        // still renames the credit.
                        name = "@" + it.toBestDisplayName(),
                        confirmed = {
                            // Only a role-marked credit is an invite someone can accept, and each
                            // lookup costs a LocalCache note plus a relay REQ per credit per
                            // rendered card. Asking for every `mention` and `inspired-by` would
                            // spend a subscription per feed item on an event that cannot exist.
                            if (credit.label.isCollaborationRole()) {
                                AcceptedCollaboration(target.pubKey, videoAddress, accountViewModel)
                            }
                        },
                        onClick = { nav.nav(Route.Profile(target.pubKey)) },
                    )
                }
            }

        is CreditTarget.Video ->
            CreditRow(
                label = workLabel(credit.label),
                name = target.address.dTag.take(SHORT_REF_LENGTH),
                onClick = {
                    nav.nav(Route.Note(target.address.toTag()))
                },
            )

        is CreditTarget.Event ->
            CreditRow(
                label = workLabel(credit.label),
                name = target.eventId.take(SHORT_REF_LENGTH),
                onClick = { nav.nav(Route.Note(target.eventId)) },
            )
    }
}

/**
 * Draws a check when [pubKey] has published an accepted [VideoCollaborationEvent] for this video.
 *
 * The response is addressed by its own coordinate — `34238:<collaborator>:<video coordinate>` —
 * so it can be asked for directly instead of scanning every 34238 on the network for one that
 * `a`-tags this video. A publisher that keys the response by something else (divine-web uses a
 * random `d`) simply never resolves here, and the credit renders without the check rather than
 * with a wrong one.
 */
@Composable
private fun AcceptedCollaboration(
    pubKey: String,
    videoAddress: Address?,
    accountViewModel: AccountViewModel,
) {
    if (videoAddress == null) return

    val responseAddress =
        remember(pubKey, videoAddress) {
            Address(VideoCollaborationEvent.KIND, pubKey, videoAddress.toValue())
        }
    val note = remember(responseAddress) { accountViewModel.getOrCreateAddressableNote(responseAddress) }
    val response by observeNoteEvent<VideoCollaborationEvent>(note, accountViewModel)

    if (response?.isAccepted() == true) {
        Icon(
            symbol = MaterialSymbols.CheckCircle,
            contentDescription = stringRes(R.string.video_credit_accepted),
            modifier = Modifier.padding(start = 2.dp),
            tint = MaterialTheme.colorScheme.lessImportantLink,
        )
    }
}

@Composable
private fun CreditRow(
    label: String,
    name: String,
    confirmed: @Composable () -> Unit = {},
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (label.isNotBlank()) {
            Text(
                text = "$label ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        ClickableTextColor(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            linkColor = MaterialTheme.colorScheme.lessImportantLink,
            onClick = onClick,
        )
        confirmed()
    }
}

// A person tagged without a stated capacity is still in the video — that is what NIP-71's `p`
// means — so the neutral credit, not a blank one, is the honest default.
@Composable
private fun personLabel(marker: String?): String =
    when (marker) {
        INSPIRED_BY_MARKER -> stringRes(R.string.video_credit_inspired_by)
        MENTION_MARKER, null -> stringRes(R.string.video_credit_featuring)
        // Anything else is a role the video's author typed ("Collaborator", "Director"): print it.
        else -> marker
    }

// The same treatment for a credited work. Both reference kinds route through here so an `e` tag
// and an `a` tag carrying the same marker read identically — the `e` path used to print the raw
// protocol token, so a `mention` showed up in the UI as the literal word "mention".
@Composable
private fun workLabel(marker: String?): String =
    when (marker) {
        AUDIO_MARKER -> stringRes(R.string.video_credit_audio_from)
        MENTION_MARKER, null -> stringRes(R.string.video_credit_references)
        else -> marker
    }

/** True for a marker that names a role rather than describing a reference. */
private fun String?.isCollaborationRole(): Boolean = this != null && this != MENTION_MARKER && this != INSPIRED_BY_MARKER && this != AUDIO_MARKER

private const val INSPIRED_BY_MARKER = "inspired-by"
private const val MENTION_MARKER = "mention"
private const val AUDIO_MARKER = "audio"

// Enough of a hex id or `d` tag to tell two references apart without wrapping the row.
private const val SHORT_REF_LENGTH = 8
