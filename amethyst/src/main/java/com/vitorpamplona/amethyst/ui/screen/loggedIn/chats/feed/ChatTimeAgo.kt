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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.elements.DisplayLocation
import com.vitorpamplona.amethyst.ui.note.elements.DisplayPoW
import com.vitorpamplona.amethyst.ui.note.elements.TimeAgoStyle
import com.vitorpamplona.amethyst.ui.note.elements.ToggleableTimeAgoText
import com.vitorpamplona.amethyst.ui.note.timeAheadNoDot
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.IncognitoBadge
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Font12SP
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geoHashOrScope
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip13Pow.strongPoWOrNull
import com.vitorpamplona.quartz.nip40Expiration.expiration

@Composable
fun ChatTimeAgo(baseNote: Note) {
    ToggleableTimeAgoText(
        timestamp = baseNote.createdAt() ?: 0L,
        style = TimeAgoStyle.Short,
        color = MaterialTheme.colorScheme.placeholderText,
        fontSize = Font12SP,
        // The chat time is wrapped in a tap target that opens the relay/delivery dialog,
        // so it must not steal the tap to toggle relative/absolute. The absolute time is
        // shown in that dialog instead.
        toggleable = false,
    )
}

/**
 * Whether the compact bubble footer would show anything besides the timestamp — a
 * legacy-DM marker, an expiration, a geohash, or a proof-of-work badge. Lets the caller
 * skip the footer entirely on the common no-metadata message (rather than emit an empty
 * row) while still surfacing these per-message details that used to live in the
 * tap-to-expand "complete UI" detail row.
 */
fun chatFooterHasMeta(
    note: Note,
    // The location room's own cell, when open. A message whose only metadata is this geohash carries
    // no footer-worthy detail (the room header already says where we are), so it doesn't force a row.
    suppressGeohash: String? = null,
): Boolean {
    val event = note.event ?: return false
    val geo = event.geoHashOrScope()
    return event is PrivateDmEvent ||
        event.expiration() != null ||
        (geo != null && geo != suppressGeohash) ||
        event.strongPoWOrNull() != null ||
        note.isPinnedInRelayGroup()
}

/** True when this note's NIP-29 group has it in its pinned list (kind 39005). */
fun Note.isPinnedInRelayGroup(): Boolean = inGatherers?.firstNotNullOfOrNull { it as? RelayGroupChannel }?.isPinned(idHex) == true

/**
 * The small row at the bottom of a chat bubble: inline status glyphs (legacy-DM,
 * expiration, geohash, proof-of-work — each shown only when present) followed, on the
 * last message of an author run ([showTime]), by the timestamp and its delivery affordance
 * — relay-acceptance ticks for our own messages, a tappable "seen on relays" for received
 * ones. Replaces the old tap-to-expand detail row.
 */
@Composable
fun ChatMessageFooter(
    note: Note,
    isLoggedInUser: Boolean,
    showTime: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = note.event
    val geoRaw = remember(event) { event?.geoHashOrScope() }
    // Hide the room's own cell (repeated on every message here); keep any other geohash.
    val suppressGeohash = LocalChatSuppressGeohash.current
    val geo = if (geoRaw != null && geoRaw != suppressGeohash) geoRaw else null
    val pow = remember(event) { event?.strongPoWOrNull() }

    Row(verticalAlignment = Alignment.CenterVertically) {
        // Each glyph self-gates and renders nothing when not applicable.
        ChatPinnedBadge(note)
        IncognitoBadge(note)
        ChatExpiration(note)

        if (geo != null) {
            Spacer(StdHorzSpacer)
            DisplayLocation(geo, accountViewModel, nav)
        }
        if (pow != null) {
            Spacer(StdHorzSpacer)
            DisplayPoW(pow, accountViewModel)
        }

        if (showTime) {
            val hasGlyph = note.isPinnedInRelayGroup() || event is PrivateDmEvent || event?.expiration() != null || geo != null || pow != null
            if (hasGlyph) Spacer(StdHorzSpacer)

            // Drafts aren't published, so no relay/delivery detail; everything else gets
            // the tappable timestamp that opens "where did this come from".
            if (note.isDraft()) {
                ChatTimeAgo(note)
            } else {
                ChatTimeWithDelivery(note, isLoggedInUser, accountViewModel, nav)
            }
        }
    }
}

/** A small pin glyph on a bubble whose message is pinned in its NIP-29 group. */
@Composable
fun ChatPinnedBadge(note: Note) {
    if (!note.isPinnedInRelayGroup()) return
    Icon(
        symbol = MaterialSymbols.PushPin,
        contentDescription = stringRes(R.string.relay_group_pinned_content_description),
        modifier = Modifier.size(12.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Spacer(StdHorzSpacer)
}

@Composable
fun ChatExpiration(note: Note) {
    val event = note.event
    if (event != null) {
        val expires = remember(event) { event.expiration() }
        if (expires != null) {
            ChatDisplayExpiration(expires)
        }
    }
}

@Composable
fun ChatDisplayExpiration(expirationDate: Long) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = StdHorzSpacer)
        Icon(
            symbol = MaterialSymbols.Timer,
            contentDescription = stringRes(R.string.expiration_date_label),
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.placeholderText,
        )
        val context = LocalContext.current
        Text(
            text = timeAheadNoDot(expirationDate, context),
            color = MaterialTheme.colorScheme.placeholderText,
            fontSize = Font12SP,
            maxLines = 1,
        )
    }
}
