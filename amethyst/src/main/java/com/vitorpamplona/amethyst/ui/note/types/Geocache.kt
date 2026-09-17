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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.note.FoundLogProof
import com.vitorpamplona.amethyst.commons.ui.note.GeocacheCard
import com.vitorpamplona.amethyst.commons.ui.note.GeocacheFoundLogCard
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteEvent
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.note.creators.location.LocationPreviewMap
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nipCCGeocaching.foundLog.GeocacheFoundLogEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import com.vitorpamplona.quartz.nipCCGeocaching.verification.GeocacheVerificationValidator

/**
 * Entry for a NIP-CC geocache listing (kind 37516): decodes the [Note] and renders the shared
 * commons [GeocacheCard], supplying the native map hero as its slot.
 *
 * "Claimed" is read off the listing's own `F` tag rather than computed from the cache's found
 * logs. NIP-CC makes `F` the authoritative answer once the owner publishes it ("clients MUST
 * attribute the exclusive claim to the pubkey in the `F` tag"), and it travels inside the event,
 * so a card can state it without holding the logs. The provisional, timestamp-ordered winner
 * needs every verified log for the cache and belongs on a screen that subscribes to them —
 * deriving it from whatever replies happen to be in memory would render a claim that flickers.
 */
@Composable
fun RenderGeocache(baseNote: Note) {
    val noteEvent = baseNote.event as? GeocacheListingEvent ?: return

    val claimed = remember(noteEvent) { noteEvent.firstToFindWinner() != null }

    GeocacheCard(noteEvent, claimed) { latitude, longitude, pinColor, pinEmoji, pinAlpha ->
        LocationPreviewMap(
            latitude = latitude,
            longitude = longitude,
            pinColor = pinColor,
            pinEmoji = pinEmoji,
            pinAlpha = pinAlpha,
        )
    }
}

/**
 * Entry for a NIP-CC found log (kind 7516).
 *
 * The proof badge is decided here rather than in the card because deciding it needs the cache
 * listing: a `verification` tag is only a string until it has been checked against the listing's
 * verification key, this log's own author, and the cache the log claims. So the listing is
 * loaded and observed — which also asks the relays for it — and until it arrives the answer is
 * [FoundLogProof.UNKNOWN], which the card renders as no badge at all rather than an optimistic
 * one.
 */
@Composable
fun RenderGeocacheFoundLog(
    baseNote: Note,
    accountViewModel: AccountViewModel,
) {
    val noteEvent = baseNote.event as? GeocacheFoundLogEvent ?: return
    val address = remember(noteEvent) { noteEvent.geocache() }

    if (!noteEvent.isVerified() || address == null) {
        GeocacheFoundLogCard(noteEvent, FoundLogProof.NONE)
        return
    }

    LoadAddressableNote(address, accountViewModel) { cacheNote ->
        if (cacheNote == null) {
            GeocacheFoundLogCard(noteEvent, FoundLogProof.UNKNOWN)
        } else {
            val listing by observeNoteEvent<GeocacheListingEvent>(cacheNote, accountViewModel)

            val proof =
                remember(noteEvent, listing) {
                    val cache = listing
                    when {
                        cache == null -> FoundLogProof.UNKNOWN
                        GeocacheVerificationValidator.isValid(noteEvent, cache) -> FoundLogProof.VALID
                        else -> FoundLogProof.INVALID
                    }
                }

            GeocacheFoundLogCard(noteEvent, proof)
        }
    }
}
