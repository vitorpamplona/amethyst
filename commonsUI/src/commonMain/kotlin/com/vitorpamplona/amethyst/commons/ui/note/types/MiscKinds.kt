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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.nip64Chess.ui.ChessBoard
import com.vitorpamplona.amethyst.commons.nip69P2pOrderEvents.formatPercent
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNote
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.kc_misc_chess_accept_title
import com.vitorpamplona.amethyst.commons.resources.kc_misc_chess_black
import com.vitorpamplona.amethyst.commons.resources.kc_misc_chess_color
import com.vitorpamplona.amethyst.commons.resources.kc_misc_chess_draw_offer_title
import com.vitorpamplona.amethyst.commons.resources.kc_misc_chess_move
import com.vitorpamplona.amethyst.commons.resources.kc_misc_chess_move_numbered
import com.vitorpamplona.amethyst.commons.resources.kc_misc_chess_move_title
import com.vitorpamplona.amethyst.commons.resources.kc_misc_chess_result
import com.vitorpamplona.amethyst.commons.resources.kc_misc_chess_start_title
import com.vitorpamplona.amethyst.commons.resources.kc_misc_chess_termination
import com.vitorpamplona.amethyst.commons.resources.kc_misc_chess_white
import com.vitorpamplona.amethyst.commons.resources.kc_misc_deletion_count
import com.vitorpamplona.amethyst.commons.resources.kc_misc_deletion_title
import com.vitorpamplona.amethyst.commons.resources.kc_misc_file_size
import com.vitorpamplona.amethyst.commons.resources.kc_misc_file_type
import com.vitorpamplona.amethyst.commons.resources.kc_misc_geocache_verification_subtitle
import com.vitorpamplona.amethyst.commons.resources.kc_misc_geocache_verification_title
import com.vitorpamplona.amethyst.commons.resources.kc_misc_geohash
import com.vitorpamplona.amethyst.commons.resources.kc_misc_hours
import com.vitorpamplona.amethyst.commons.resources.kc_misc_label_namespace
import com.vitorpamplona.amethyst.commons.resources.kc_misc_label_title
import com.vitorpamplona.amethyst.commons.resources.kc_misc_language
import com.vitorpamplona.amethyst.commons.resources.kc_misc_longform_draft_title
import com.vitorpamplona.amethyst.commons.resources.kc_misc_minutes
import com.vitorpamplona.amethyst.commons.resources.kc_misc_monitor_every
import com.vitorpamplona.amethyst.commons.resources.kc_misc_monitor_timeout_all
import com.vitorpamplona.amethyst.commons.resources.kc_misc_monitor_timeout_value
import com.vitorpamplona.amethyst.commons.resources.kc_misc_monitor_timeouts
import com.vitorpamplona.amethyst.commons.resources.kc_misc_more
import com.vitorpamplona.amethyst.commons.resources.kc_misc_mostro_dev_fee_title
import com.vitorpamplona.amethyst.commons.resources.kc_misc_mostro_dispute_id
import com.vitorpamplona.amethyst.commons.resources.kc_misc_mostro_dispute_initiator
import com.vitorpamplona.amethyst.commons.resources.kc_misc_mostro_dispute_opened
import com.vitorpamplona.amethyst.commons.resources.kc_misc_mostro_dispute_title
import com.vitorpamplona.amethyst.commons.resources.kc_misc_mostro_rating_last
import com.vitorpamplona.amethyst.commons.resources.kc_misc_mostro_rating_out_of
import com.vitorpamplona.amethyst.commons.resources.kc_misc_mostro_rating_reviews
import com.vitorpamplona.amethyst.commons.resources.kc_misc_mostro_rating_since
import com.vitorpamplona.amethyst.commons.resources.kc_misc_mostro_rating_title
import com.vitorpamplona.amethyst.commons.resources.kc_misc_ots_pending
import com.vitorpamplona.amethyst.commons.resources.kc_misc_ots_proven_before
import com.vitorpamplona.amethyst.commons.resources.kc_misc_ots_subtitle
import com.vitorpamplona.amethyst.commons.resources.kc_misc_ots_title
import com.vitorpamplona.amethyst.commons.resources.kc_misc_p2p_amount
import com.vitorpamplona.amethyst.commons.resources.kc_misc_p2p_buy
import com.vitorpamplona.amethyst.commons.resources.kc_misc_p2p_destination
import com.vitorpamplona.amethyst.commons.resources.kc_misc_p2p_expires
import com.vitorpamplona.amethyst.commons.resources.kc_misc_p2p_fiat
import com.vitorpamplona.amethyst.commons.resources.kc_misc_p2p_fiat_range
import com.vitorpamplona.amethyst.commons.resources.kc_misc_p2p_maker
import com.vitorpamplona.amethyst.commons.resources.kc_misc_p2p_market_price
import com.vitorpamplona.amethyst.commons.resources.kc_misc_p2p_network
import com.vitorpamplona.amethyst.commons.resources.kc_misc_p2p_order
import com.vitorpamplona.amethyst.commons.resources.kc_misc_p2p_order_id
import com.vitorpamplona.amethyst.commons.resources.kc_misc_p2p_payment_hash
import com.vitorpamplona.amethyst.commons.resources.kc_misc_p2p_platform
import com.vitorpamplona.amethyst.commons.resources.kc_misc_p2p_premium
import com.vitorpamplona.amethyst.commons.resources.kc_misc_p2p_sats
import com.vitorpamplona.amethyst.commons.resources.kc_misc_p2p_sell
import com.vitorpamplona.amethyst.commons.resources.kc_misc_p2p_status_canceled
import com.vitorpamplona.amethyst.commons.resources.kc_misc_p2p_status_expired
import com.vitorpamplona.amethyst.commons.resources.kc_misc_p2p_status_in_progress
import com.vitorpamplona.amethyst.commons.resources.kc_misc_p2p_status_pending
import com.vitorpamplona.amethyst.commons.resources.kc_misc_p2p_status_success
import com.vitorpamplona.amethyst.commons.resources.kc_misc_poll_response_title
import com.vitorpamplona.amethyst.commons.resources.kc_misc_rating
import com.vitorpamplona.amethyst.commons.resources.kc_misc_robosats_rating_title
import com.vitorpamplona.amethyst.commons.resources.kc_misc_seconds
import com.vitorpamplona.amethyst.commons.resources.kc_misc_source
import com.vitorpamplona.amethyst.commons.resources.kc_misc_status
import com.vitorpamplona.amethyst.commons.resources.kc_misc_story_progress_title
import com.vitorpamplona.amethyst.commons.resources.kc_misc_story_status_done
import com.vitorpamplona.amethyst.commons.resources.kc_misc_story_status_new
import com.vitorpamplona.amethyst.commons.resources.kc_misc_story_status_reading
import com.vitorpamplona.amethyst.commons.resources.kc_misc_text_track_title
import com.vitorpamplona.amethyst.commons.resources.kc_misc_url
import com.vitorpamplona.amethyst.commons.resources.kc_misc_vanish_everywhere
import com.vitorpamplona.amethyst.commons.resources.kc_misc_vanish_relays
import com.vitorpamplona.amethyst.commons.resources.kc_misc_vanish_title
import com.vitorpamplona.amethyst.commons.resources.kc_misc_video_view_loops
import com.vitorpamplona.amethyst.commons.resources.kc_misc_video_view_started
import com.vitorpamplona.amethyst.commons.resources.kc_misc_video_view_title
import com.vitorpamplona.amethyst.commons.resources.kc_misc_video_view_watched
import com.vitorpamplona.amethyst.commons.resources.kc_misc_workout_difficulty
import com.vitorpamplona.amethyst.commons.resources.kc_misc_workout_duration
import com.vitorpamplona.amethyst.commons.resources.kc_misc_workout_interval
import com.vitorpamplona.amethyst.commons.resources.kc_misc_workout_rest_rounds
import com.vitorpamplona.amethyst.commons.resources.kc_misc_workout_rest_sets
import com.vitorpamplona.amethyst.commons.resources.kc_misc_workout_rounds
import com.vitorpamplona.amethyst.commons.resources.kc_misc_workout_sets
import com.vitorpamplona.amethyst.commons.resources.kc_misc_workout_template_title
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.LoadOts
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindName
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.workouts.labelRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.workouts.symbol
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.formatDecimal
import com.vitorpamplona.amethyst.commons.util.formatGrouped
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.fitness.workout.WorkoutTemplateEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.DurationTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseType
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryReadingStateEvent
import com.vitorpamplona.quartz.experimental.nip95.data.FileStorageEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip01Core.tags.kinds.kinds
import com.vitorpamplona.quartz.nip03Timestamp.OtsEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip23LongContent.draft.LongFormDraftEvent
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip32Labeling.LabelEvent
import com.vitorpamplona.quartz.nip62RequestToVanish.RequestToVanishEvent
import com.vitorpamplona.quartz.nip64Chess.ChessEngine
import com.vitorpamplona.quartz.nip64Chess.ChessPosition
import com.vitorpamplona.quartz.nip64Chess.challenge.accept.LiveChessGameAcceptEvent
import com.vitorpamplona.quartz.nip64Chess.draw.LiveChessDrawOfferEvent
import com.vitorpamplona.quartz.nip64Chess.jester.JesterEvent
import com.vitorpamplona.quartz.nip64Chess.move.LiveChessMoveEvent
import com.vitorpamplona.quartz.nip66RelayMonitor.monitor.RelayMonitorEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.P2POrderEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroDevFee.MostroDevFeePaymentEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroDispute.MostroDisputeEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroRating.MostroUserRatingEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.robosatsRating.RoboSatsCoordinatorRatingEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.OrderStatus
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.OrderType
import com.vitorpamplona.quartz.nip71Video.textTrack.TextTrackEvent
import com.vitorpamplona.quartz.nip71Video.textTrack.WebVttText
import com.vitorpamplona.quartz.nip71Video.views.VideoViewEvent
import com.vitorpamplona.quartz.nip71Video.views.tags.ViewPhase
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import com.vitorpamplona.quartz.nip94FileMetadata.tags.MimeTypeTag
import com.vitorpamplona.quartz.nipCCGeocaching.verification.GeocacheVerificationEvent
import com.vitorpamplona.quartz.nip64Chess.Color as ChessColor

/*
 * Cards for a grab bag of kinds that used to fall through to the text renderer and print their
 * raw content (empty, JSON, base64 or WebVTT): deletion and vanish requests, labels, timestamps,
 * NIP-95 blobs, live chess, P2P trading records, poll votes, relay monitors, video side records and
 * workout templates. Each one says what the event does and quotes what it acts on.
 */

/** How many target events a card quotes before it just counts the rest. */
private const val MISC_MAX_QUOTES = 3

private fun Long.toIntCount(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

/** "and 4 more" under a list that was cut at [MISC_MAX_QUOTES]. */
@Composable
private fun MoreCount(count: Int) {
    if (count <= 0) return
    Text(
        text = pluralStringRes(Res.plurals.kc_misc_more, count, count),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A length of time in the biggest unit that divides it evenly: "6 hours", "90 seconds". */
@Composable
private fun durationLabel(seconds: Long): String =
    when {
        seconds > 0 && seconds % 3600 == 0L -> (seconds / 3600).toIntCount().let { pluralStringRes(Res.plurals.kc_misc_hours, it, it) }
        seconds > 0 && seconds % 60 == 0L -> (seconds / 60).toIntCount().let { pluralStringRes(Res.plurals.kc_misc_minutes, it, it) }
        else -> seconds.toIntCount().let { pluralStringRes(Res.plurals.kc_misc_seconds, it, it) }
    }

private fun formatSeconds(epochSeconds: Long): String = formatDateTime(epochSeconds * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.SHORT)

/** Plain human text (caption cues) that is not worth the rich-text pipeline. */
@Composable
private fun PlainPreview(
    text: String?,
    maxLines: Int = 4,
) {
    if (text.isNullOrBlank()) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

// ---------------------------------------------------------------------------------------------
// NIP-09 deletion request (kind 5)
// ---------------------------------------------------------------------------------------------

@Immutable
private class MiscDeletionTargets(
    val eventIds: List<HexKey>,
    val addresses: List<Address>,
    val kinds: List<Int>,
) {
    // A replaceable event is usually deleted by both its `e` and its `a`, so the ids alone are the
    // count; the addresses only stand on their own in an address-only request.
    val count get() = if (eventIds.isNotEmpty()) eventIds.size else addresses.size

    companion object {
        fun from(event: DeletionRequestEvent): MiscDeletionTargets {
            val ids = event.deleteEventIds().distinct()
            val addresses = if (ids.isEmpty()) event.deleteAddresses().distinctBy { it.toValue() } else emptyList()
            return MiscDeletionTargets(ids, addresses, event.kinds().distinct())
        }
    }
}

/** A deletion request: how many events it asks to remove, of which kinds, why, and the events. */
@Composable
fun RenderDeletionRequest(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? DeletionRequestEvent ?: return
    val targets = remember(event) { MiscDeletionTargets.from(event) }
    val kindLabels = targets.kinds.map { kindName(it) }

    KindCard(
        icon = MaterialSymbols.Delete,
        title = stringRes(Res.string.kc_misc_deletion_title),
        subtitle = pluralStringRes(Res.plurals.kc_misc_deletion_count, targets.count, targets.count),
        iconTint = MaterialTheme.colorScheme.error,
    ) {
        KindCardChips(kindLabels)
        KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav, quotesLeft)
        targets.eventIds.take(MISC_MAX_QUOTES).forEach {
            KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav)
        }
        targets.addresses.take(MISC_MAX_QUOTES).forEach {
            KindCardQuoteAddress(it, quotesLeft, backgroundColor, accountViewModel, nav)
        }
        MoreCount(targets.count - MISC_MAX_QUOTES)
    }
}

// ---------------------------------------------------------------------------------------------
// NIP-62 request to vanish (kind 62)
// ---------------------------------------------------------------------------------------------

/** A request to vanish: which relays (or all of them) should erase the author, and why. */
@Composable
fun RenderRequestToVanish(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? RequestToVanishEvent ?: return
    val everywhere = remember(event) { event.vanishFromAllRelays() }
    val relays = remember(event) { event.vanishFromRelays().map { it.displayUrl() }.distinct() }

    KindCard(
        icon = MaterialSymbols.DeleteForever,
        title = stringRes(Res.string.kc_misc_vanish_title),
        subtitle =
            if (everywhere) {
                stringRes(Res.string.kc_misc_vanish_everywhere)
            } else {
                pluralStringRes(Res.plurals.kc_misc_vanish_relays, relays.size, relays.size)
            },
        iconTint = MaterialTheme.colorScheme.error,
    ) {
        if (!everywhere) KindCardChips(relays)
        KindCardText(baseNote, event.reason(), backgroundColor, accountViewModel, nav)
    }
}

// ---------------------------------------------------------------------------------------------
// NIP-32 labels (kind 1985)
// ---------------------------------------------------------------------------------------------

@Immutable
private class MiscLabelInfo(
    val namespaces: List<String>,
    val labels: List<String>,
    val pubKeys: List<HexKey>,
    val eventIds: List<HexKey>,
    val addresses: List<Address>,
    val otherTargets: List<String>,
) {
    companion object {
        fun from(event: LabelEvent): MiscLabelInfo {
            val labelTags = event.labels()
            val namespaces = (event.namespaces().map { it.namespace } + labelTags.map { it.namespace }).distinct()
            val labels =
                labelTags
                    .map { if (it.namespace == LabelEvent.HASHTAG_NAMESPACE) "#${it.label}" else it.label }
                    .distinct()
            return MiscLabelInfo(
                namespaces = namespaces,
                labels = labels,
                pubKeys = event.labeledPubKeys().distinct(),
                eventIds = event.labeledEvents().distinct(),
                addresses = event.labeledAddresses().distinct().mapNotNull { Address.parse(it) },
                otherTargets = event.labeledHashtags().map { "#$it" } + event.labeledRelayUrls(),
            )
        }
    }
}

/** A label event: the labels as chips under their namespace, then the people and events labelled. */
@Composable
fun RenderLabel(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? LabelEvent ?: return
    val info = remember(event) { MiscLabelInfo.from(event) }

    KindCard(
        icon = MaterialSymbols.AutoMirrored.Label,
        title = stringRes(Res.string.kc_misc_label_title),
        subtitle = info.namespaces.takeIf { it.isNotEmpty() }?.let { stringRes(Res.string.kc_misc_label_namespace, it.joinToString(", ")) },
    ) {
        KindCardChips(info.labels)
        KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav, quotesLeft)
        KindCardUsers(info.pubKeys, accountViewModel, nav)
        KindCardChips(info.otherTargets)
        info.eventIds.take(MISC_MAX_QUOTES).forEach {
            KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav)
        }
        info.addresses.take(MISC_MAX_QUOTES).forEach {
            KindCardQuoteAddress(it, quotesLeft, backgroundColor, accountViewModel, nav)
        }
        MoreCount(info.eventIds.size + info.addresses.size - info.eventIds.size.coerceAtMost(MISC_MAX_QUOTES) - info.addresses.size.coerceAtMost(MISC_MAX_QUOTES))
    }
}

// ---------------------------------------------------------------------------------------------
// NIP-03 OpenTimestamps attestation (kind 1040)
// ---------------------------------------------------------------------------------------------

/** A timestamp proof: the date the Bitcoin chain anchors the event to, and the event itself. */
@Composable
fun RenderOts(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? OtsEvent ?: return
    val targetId = remember(event) { event.digestEventId() }

    KindCard(
        icon = MaterialSymbols.OpenTimestamps,
        title = stringRes(Res.string.kc_misc_ots_title),
        subtitle = stringRes(Res.string.kc_misc_ots_subtitle),
    ) {
        if (targetId != null) {
            LoadNote(targetId) { target ->
                if (target != null) {
                    LoadOts(
                        target,
                        accountViewModel,
                        whenConfirmed = { time ->
                            KindCardField(stringRes(Res.string.kc_misc_ots_proven_before), formatSeconds(time))
                        },
                        whenPending = {
                            KindCardField(stringRes(Res.string.kc_misc_status), stringRes(Res.string.kc_misc_ots_pending))
                        },
                    )
                    KindCardQuote(target, quotesLeft, backgroundColor, accountViewModel, nav)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// NIP-95 file storage (kind 1064)
// ---------------------------------------------------------------------------------------------

@Immutable
private class MiscBlobInfo(
    val alt: String?,
    val mimeType: String?,
    val bytes: Long,
)

/** A NIP-95 blob: its type and size, never the base64 payload (its 1065 header draws the media). */
@Composable
fun RenderFileStorage(baseNote: Note) {
    val event = baseNote.event as? FileStorageEvent ?: return
    val info =
        remember(event) {
            val chars = event.content.count { !it.isWhitespace() }.toLong()
            val padding =
                event.content
                    .trimEnd()
                    .takeLast(2)
                    .count { it == '=' }
            MiscBlobInfo(
                alt = event.alt(),
                mimeType = event.tags.firstNotNullOfOrNull(MimeTypeTag::parse),
                bytes = (chars * 3 / 4 - padding).coerceAtLeast(0),
            )
        }

    KindCard(
        icon = MaterialSymbols.AutoMirrored.InsertDriveFile,
        title = kindName(event.kind),
        subtitle = info.alt,
    ) {
        KindCardField(stringRes(Res.string.kc_misc_file_type), info.mimeType)
        KindCardField(stringRes(Res.string.kc_misc_file_size), formatBytes(info.bytes))
    }
}

// ---------------------------------------------------------------------------------------------
// NIP-CC geocache verification (kind 7517)
// ---------------------------------------------------------------------------------------------

/** A geocache's proof of presence: who it was issued to and the cache it was issued at. */
@Composable
fun RenderGeocacheVerification(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? GeocacheVerificationEvent ?: return
    val finderCache = remember(event) { event.finderCache() }

    KindCard(
        icon = MaterialSymbols.LocationOn,
        title = stringRes(Res.string.kc_misc_geocache_verification_title),
        subtitle = stringRes(Res.string.kc_misc_geocache_verification_subtitle),
    ) {
        if (finderCache != null) {
            KindCardUsers(listOf(finderCache.finderPubKey), accountViewModel, nav)
            KindCardQuoteAddress(finderCache.cache, quotesLeft, backgroundColor, accountViewModel, nav)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Interactive story reading state (kind 30298)
// ---------------------------------------------------------------------------------------------

/** Where a reader is in an interactive story: the story, how far along, and the current scene. */
@Composable
fun RenderInteractiveStoryReadingState(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? InteractiveStoryReadingStateEvent ?: return
    val title = remember(event) { event.title() }
    val status = remember(event) { event.status() }
    val scene = remember(event) { event.currentScene() ?: event.rootAddressId()?.let { Address.parse(it) } }

    val statusLabel =
        when (status) {
            "new" -> stringRes(Res.string.kc_misc_story_status_new)
            "reading" -> stringRes(Res.string.kc_misc_story_status_reading)
            "done" -> stringRes(Res.string.kc_misc_story_status_done)
            else -> status
        }

    KindCard(
        icon = MaterialSymbols.MenuBook,
        title = title ?: stringRes(Res.string.kc_misc_story_progress_title),
        subtitle = statusLabel,
    ) {
        scene?.let { KindCardQuoteAddress(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

// ---------------------------------------------------------------------------------------------
// NIP-23 long-form draft (kind 30024)
// ---------------------------------------------------------------------------------------------

/** An article draft, drawn with the article header under a "draft" banner. */
@Composable
fun RenderLongFormDraft(
    baseNote: Note,
    accountViewModel: AccountViewModel,
) {
    val event = baseNote.event as? LongFormDraftEvent ?: return
    // The draft carries the article's tags and text verbatim; the header only reads those, so the
    // draft is shown through the article class instead of a second copy of the header. Drafts kept
    // private are encrypted, and their ciphertext must not become the header's summary.
    val asArticle =
        remember(event) {
            val content = if (looksLikeOpaquePayload(event.content)) "" else event.content
            LongFormContentEvent(event.id, event.pubKey, event.createdAt, event.tags, content, event.sig)
        }

    KindCard(
        icon = MaterialSymbols.Drafts,
        title = stringRes(Res.string.kc_misc_longform_draft_title),
    ) {
        LongFormHeader(asArticle, baseNote, accountViewModel)
    }
}

// ---------------------------------------------------------------------------------------------
// NIP-64 live chess: accept (30065), move (30066), draw offer (30068), Jester (kind 30)
// ---------------------------------------------------------------------------------------------

/** A board for [fen], or nothing when the position does not parse on this platform. */
@Composable
private fun FenBoard(fen: String?) {
    if (fen.isNullOrBlank()) return
    val position: ChessPosition? =
        remember(fen) {
            runCatching { ChessEngine().apply { loadFen(fen) }.getPosition() }.getOrNull()
        }
    position?.let { ChessBoard(position = it, boardSize = 200.dp, showCoordinates = false) }
}

@Composable
private fun chessColorLabel(color: ChessColor?): String? =
    when (color) {
        ChessColor.WHITE -> stringRes(Res.string.kc_misc_chess_white)
        ChessColor.BLACK -> stringRes(Res.string.kc_misc_chess_black)
        null -> null
    }

/** A player accepting a live chess challenge: the challenger and the challenge itself. */
@Composable
fun RenderLiveChessAccept(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? LiveChessGameAcceptEvent ?: return
    val opponent = remember(event) { event.opponentPubkey() }
    val challengeId = remember(event) { event.challengeEventId()?.eventId }

    KindCard(
        icon = MaterialSymbols.ChessKnight,
        title = stringRes(Res.string.kc_misc_chess_accept_title),
    ) {
        opponent?.let { KindCardUsers(listOf(it), accountViewModel, nav) }
        challengeId?.let { KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

/** One move of a live chess game: its number and notation, the board after it, the comment. */
@Composable
fun RenderLiveChessMove(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? LiveChessMoveEvent ?: return
    val san = remember(event) { event.san() }
    val number = remember(event) { event.moveNumber() }
    val fen = remember(event) { event.fen() }
    val opponent = remember(event) { event.opponentPubkey() }

    val title =
        when {
            san != null && number != null -> stringRes(Res.string.kc_misc_chess_move_numbered, number, san)
            san != null -> stringRes(Res.string.kc_misc_chess_move, san)
            else -> stringRes(Res.string.kc_misc_chess_move_title)
        }

    KindCard(icon = MaterialSymbols.ChessKnight, title = title) {
        opponent?.let { KindCardUsers(listOf(it), accountViewModel, nav) }
        FenBoard(fen)
        KindCardText(baseNote, event.comment(), backgroundColor, accountViewModel, nav)
    }
}

/** A draw offer in a live chess game: to whom, and the offer's message. */
@Composable
fun RenderLiveChessDrawOffer(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? LiveChessDrawOfferEvent ?: return
    val opponent = remember(event) { event.opponentPubkey() }

    KindCard(
        icon = MaterialSymbols.ChessKnight,
        title = stringRes(Res.string.kc_misc_chess_draw_offer_title),
    ) {
        opponent?.let { KindCardUsers(listOf(it), accountViewModel, nav) }
        KindCardText(baseNote, event.message(), backgroundColor, accountViewModel, nav)
    }
}

/** A Jester (kind 30) chess event: a game start with the starter's colour, or a move with its board. */
@Composable
fun RenderJesterChess(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? JesterEvent ?: return
    val isStart = remember(event) { event.isStartEvent() }
    val move = remember(event) { event.move() }
    val fen = remember(event) { event.fen() }
    val opponent = remember(event) { event.opponentPubkey() }

    KindCard(
        icon = MaterialSymbols.ChessKnight,
        title =
            when {
                isStart -> stringRes(Res.string.kc_misc_chess_start_title)
                move != null -> stringRes(Res.string.kc_misc_chess_move, move)
                else -> stringRes(Res.string.kc_misc_chess_move_title)
            },
    ) {
        opponent?.let { KindCardUsers(listOf(it), accountViewModel, nav) }
        if (isStart) {
            KindCardField(stringRes(Res.string.kc_misc_chess_color), chessColorLabel(event.playerColor()))
        } else {
            KindCardField(stringRes(Res.string.kc_misc_chess_result), event.result())
            KindCardField(stringRes(Res.string.kc_misc_chess_termination), event.termination()?.replace('_', ' '))
            FenBoard(fen)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// NIP-69 P2P orders (38383), Mostro ratings (38384), disputes (38386), dev fees (8383), RoboSats
// coordinator ratings (31986)
// ---------------------------------------------------------------------------------------------

@Composable
private fun orderStatusLabel(status: OrderStatus?): String? =
    when (status) {
        OrderStatus.PENDING -> stringRes(Res.string.kc_misc_p2p_status_pending)
        OrderStatus.CANCELED -> stringRes(Res.string.kc_misc_p2p_status_canceled)
        OrderStatus.IN_PROGRESS -> stringRes(Res.string.kc_misc_p2p_status_in_progress)
        OrderStatus.SUCCESS -> stringRes(Res.string.kc_misc_p2p_status_success)
        OrderStatus.EXPIRED -> stringRes(Res.string.kc_misc_p2p_status_expired)
        null -> null
    }

/** A P2P order: buy or sell, the fiat amount, the sats, premium, payment methods and status. */
@Composable
fun RenderP2POrder(baseNote: Note) {
    val event = baseNote.event as? P2POrderEvent ?: return
    val type = remember(event) { event.orderType() }
    val currency = remember(event) { event.currency() }
    val fiat = remember(event) { event.fiatAmount() }
    val sats = remember(event) { event.amount() }
    val methods = remember(event) { event.paymentMethods().orEmpty() }
    val platform = remember(event) { event.platform() }
    val network = remember(event) { listOfNotNull(event.network(), event.layer()).joinToString(" / ").ifBlank { null } }

    val fiatText =
        fiat?.let {
            val max = it.max
            if (max != null && max != it.min) {
                stringRes(Res.string.kc_misc_p2p_fiat_range, it.min, max, currency.orEmpty())
            } else {
                stringRes(Res.string.kc_misc_p2p_fiat, it.min, currency.orEmpty())
            }
        }

    KindCard(
        icon = MaterialSymbols.SwapHoriz,
        title =
            when (type) {
                OrderType.BUY -> stringRes(Res.string.kc_misc_p2p_buy)
                OrderType.SELL -> stringRes(Res.string.kc_misc_p2p_sell)
                null -> stringRes(Res.string.kc_misc_p2p_order)
            },
        subtitle = fiatText,
    ) {
        KindCardField(stringRes(Res.string.kc_misc_status), orderStatusLabel(event.status()))
        KindCardField(
            stringRes(Res.string.kc_misc_p2p_amount),
            sats?.let { if (it == 0L) stringRes(Res.string.kc_misc_p2p_market_price) else stringRes(Res.string.kc_misc_p2p_sats, formatGrouped(it)) },
        )
        KindCardField(stringRes(Res.string.kc_misc_p2p_premium), event.premium()?.let { "$it%" })
        KindCardChips(methods)
        KindCardField(stringRes(Res.string.kc_misc_p2p_maker), event.makerName())
        KindCardField(stringRes(Res.string.kc_misc_p2p_platform), platform)
        KindCardField(stringRes(Res.string.kc_misc_p2p_network), network)
        KindCardField(stringRes(Res.string.kc_misc_p2p_expires), event.expiresAt()?.let { formatSeconds(it) })
        KindCardField(stringRes(Res.string.kc_misc_url), event.source(), maxLines = 1)
    }
}

/** A Mostro instance's rating of a trader: who, the average, how many reviews, since when. */
@Composable
fun RenderMostroUserRating(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? MostroUserRatingEvent ?: return
    val rated = remember(event) { event.ratedPubKey() }
    val total = remember(event) { event.totalRating() }
    val max = remember(event) { event.maxRate() }
    val reviews = remember(event) { event.totalReviews() }
    val since = remember(event) { event.firstTradeDay() }

    KindCard(
        icon = MaterialSymbols.Star,
        title = stringRes(Res.string.kc_misc_mostro_rating_title),
        subtitle = event.platform(),
    ) {
        rated?.let { KindCardUsers(listOf(it), accountViewModel, nav) }
        KindCardField(
            stringRes(Res.string.kc_misc_rating),
            total?.let { stringRes(Res.string.kc_misc_mostro_rating_out_of, formatDecimal(it, 2), (max ?: 5L).toString()) },
        )
        KindCardField(stringRes(Res.string.kc_misc_mostro_rating_reviews), reviews?.let { formatGrouped(it) })
        KindCardField(stringRes(Res.string.kc_misc_mostro_rating_last), event.lastRating()?.toString())
        KindCardField(stringRes(Res.string.kc_misc_mostro_rating_since), since?.let { formatDateTime(it * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.NONE) })
    }
}

/** A dispute on a Mostro trade: its status, who opened it and when. */
@Composable
fun RenderMostroDispute(baseNote: Note) {
    val event = baseNote.event as? MostroDisputeEvent ?: return

    KindCard(
        icon = MaterialSymbols.Gavel,
        title = stringRes(Res.string.kc_misc_mostro_dispute_title),
        subtitle = event.platform(),
        iconTint = MaterialTheme.colorScheme.error,
    ) {
        KindCardField(stringRes(Res.string.kc_misc_status), event.status()?.replace('-', ' ')?.replace('_', ' '))
        KindCardField(stringRes(Res.string.kc_misc_mostro_dispute_initiator), event.initiator())
        KindCardField(stringRes(Res.string.kc_misc_mostro_dispute_opened), formatSeconds(event.openedAt()))
        KindCardField(stringRes(Res.string.kc_misc_mostro_dispute_id), event.disputeId(), monospace = true, maxLines = 1)
    }
}

/** A Mostro instance's receipt for the development fee it paid on an order. */
@Composable
fun RenderMostroDevFee(baseNote: Note) {
    val event = baseNote.event as? MostroDevFeePaymentEvent ?: return

    KindCard(
        icon = MaterialSymbols.Paid,
        title = stringRes(Res.string.kc_misc_mostro_dev_fee_title),
        subtitle = event.platform(),
    ) {
        KindCardField(stringRes(Res.string.kc_misc_p2p_amount), event.amount()?.let { stringRes(Res.string.kc_misc_p2p_sats, formatGrouped(it)) })
        KindCardField(stringRes(Res.string.kc_misc_p2p_order_id), event.orderId(), monospace = true, maxLines = 1)
        KindCardField(stringRes(Res.string.kc_misc_p2p_network), event.network())
        KindCardField(stringRes(Res.string.kc_misc_p2p_destination), event.destination(), monospace = true, maxLines = 1)
        KindCardField(stringRes(Res.string.kc_misc_p2p_payment_hash), event.paymentHash(), monospace = true, maxLines = 1)
    }
}

/** A RoboSats trader's rating of the coordinator that ran their order. */
@Composable
fun RenderRoboSatsCoordinatorRating(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? RoboSatsCoordinatorRatingEvent ?: return
    val coordinator = remember(event) { event.coordinator() }

    KindCard(
        icon = MaterialSymbols.Star,
        title = stringRes(Res.string.kc_misc_robosats_rating_title),
        subtitle = event.coordinatorAlias(),
    ) {
        KindCardField(stringRes(Res.string.kc_misc_rating), event.rating()?.let { formatPercent(it * 100) })
        coordinator?.let { KindCardUsers(listOf(it), accountViewModel, nav) }
        KindCardField(stringRes(Res.string.kc_misc_p2p_order_id), event.orderId(), monospace = true, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------
// NIP-88 poll response (kind 1018)
// ---------------------------------------------------------------------------------------------

/** A vote: the options picked, by label once the poll loads, and the poll itself. */
@Composable
fun RenderPollResponse(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? PollResponseEvent ?: return
    val pollId = remember(event) { event.poll()?.eventId }
    val codes = remember(event) { event.responses() }

    KindCard(
        icon = MaterialSymbols.Poll,
        title = stringRes(Res.string.kc_misc_poll_response_title),
    ) {
        if (pollId == null) {
            KindCardChips(codes)
        } else {
            LoadNote(pollId) { pollNote ->
                if (pollNote == null) {
                    KindCardChips(codes)
                } else {
                    // Checked cast through the note: the `e` tag is the voter's word, not a promise
                    // that it points at a poll.
                    val noteState by observeNote(pollNote, accountViewModel)
                    val poll = noteState.note.event as? PollEvent
                    val labels =
                        remember(poll, codes) {
                            val options = poll?.options()
                            if (options == null) codes else codes.map { code -> options.firstOrNull { it.code == code }?.label ?: code }
                        }
                    KindCardChips(labels)
                    KindCardQuote(pollNote, quotesLeft, backgroundColor, accountViewModel, nav)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// NIP-66 relay monitor announcement (kind 10166)
// ---------------------------------------------------------------------------------------------

/** A relay monitor's announcement: how often it checks, which checks, its timeouts and region. */
@Composable
fun RenderRelayMonitor(baseNote: Note) {
    val event = baseNote.event as? RelayMonitorEvent ?: return
    val frequency = remember(event) { event.frequency() }
    val checks = remember(event) { event.checks().map { it.code } }
    val timeouts = remember(event) { event.timeouts() }
    val geohash = remember(event) { event.geohashes().firstOrNull() }

    val allChecks = stringRes(Res.string.kc_misc_monitor_timeout_all)
    val timeoutText =
        timeouts
            .map { stringRes(Res.string.kc_misc_monitor_timeout_value, it.checkType ?: allChecks, formatGrouped(it.milliseconds)) }
            .joinToString(", ")

    KindCard(
        icon = MaterialSymbols.Sensors,
        title = kindName(event.kind),
        subtitle = frequency?.let { stringRes(Res.string.kc_misc_monitor_every, durationLabel(it)) },
    ) {
        KindCardChips(checks)
        KindCardField(stringRes(Res.string.kc_misc_monitor_timeouts), timeoutText)
        KindCardField(stringRes(Res.string.kc_misc_geohash), geohash, monospace = true)
    }
}

// ---------------------------------------------------------------------------------------------
// NIP-71 text track (39307) and video view (22236)
// ---------------------------------------------------------------------------------------------

/** A caption track: its language and file, a few cue lines, and the video it belongs to. */
@Composable
fun RenderTextTrack(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? TextTrackEvent ?: return
    val language = remember(event) { event.language() }
    val video = remember(event) { event.video() }
    val preview =
        remember(event) {
            val text = if (WebVttText.hasCues(event.content)) WebVttText.cueText(event.content) else event.content
            text.take(500)
        }

    KindCard(
        icon = MaterialSymbols.ClosedCaption,
        title = stringRes(Res.string.kc_misc_text_track_title),
        subtitle = language?.let { stringRes(Res.string.kc_misc_language, it) },
    ) {
        KindCardField(stringRes(Res.string.kc_misc_file_type), event.mimeType())
        KindCardField(stringRes(Res.string.kc_misc_url), event.url(), maxLines = 1)
        PlainPreview(preview)
        video?.let { KindCardQuoteAddress(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

/** A view report: how much of which video was watched, and from where in the app. */
@Composable
fun RenderVideoView(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? VideoViewEvent ?: return
    val phase = remember(event) { event.phase() }
    val viewed = remember(event) { event.viewed() }
    val loops = remember(event) { event.loops() }
    val source = remember(event) { event.source() }
    val video = remember(event) { event.video() }
    val version = remember(event) { event.videoVersion() }

    KindCard(
        icon = MaterialSymbols.PlayCircle,
        title =
            if (phase == ViewPhase.START) {
                stringRes(Res.string.kc_misc_video_view_started)
            } else {
                stringRes(Res.string.kc_misc_video_view_title)
            },
    ) {
        KindCardField(stringRes(Res.string.kc_misc_video_view_watched), viewed?.let { durationLabel(it.seconds) })
        KindCardField(stringRes(Res.string.kc_misc_video_view_loops), loops?.let { formatDecimal(it, 1) })
        KindCardField(stringRes(Res.string.kc_misc_source), source?.let { listOfNotNull(it.category, it.detail).joinToString(" · ") })
        when {
            video != null -> KindCardQuoteAddress(video, quotesLeft, backgroundColor, accountViewModel, nav)
            version != null -> KindCardQuoteEvent(version, quotesLeft, backgroundColor, accountViewModel, nav)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// NIP-101e workout template (kind 33402)
// ---------------------------------------------------------------------------------------------

@Immutable
private class MiscTemplateExercise(
    val name: String,
    val sets: Int,
    val detail: String?,
)

/** A workout plan: its type, structure (rounds, timing, rest), the exercises and the notes. */
@Composable
fun RenderWorkoutTemplate(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? WorkoutTemplateEvent ?: return
    val type = remember(event) { event.workoutTypeCode()?.let { ExerciseType.parse(it) } }
    val typeRaw = remember(event) { event.workoutTypeCode() }
    val topics = remember(event) { event.topics().distinct().map { "#$it" } }
    val exercises =
        remember(event) {
            event.exerciseGroups().map { group ->
                val reps = group.sets.mapNotNull { it.reps }.distinct()
                val weight = group.topWeightKg()
                val detail =
                    listOfNotNull(
                        reps.singleOrNull()?.let { "× $it" },
                        weight?.let { "${formatDecimal(it, 1)} kg" },
                    ).joinToString(" · ").ifBlank { null }
                MiscTemplateExercise(group.displayName() ?: group.reference, group.sets.size, detail)
            }
        }

    val typeLabel = type?.let { stringRes(it.labelRes()) } ?: typeRaw?.replaceFirstChar { it.uppercaseChar() }

    KindCard(
        icon = type.symbol(),
        title = event.title() ?: stringRes(Res.string.kc_misc_workout_template_title),
        subtitle = listOfNotNull(typeLabel, stringRes(Res.string.kc_misc_workout_template_title).takeIf { event.title() != null }).joinToString(" · ").ifBlank { null },
    ) {
        KindCardField(stringRes(Res.string.kc_misc_workout_rounds), event.rounds()?.toString())
        KindCardField(stringRes(Res.string.kc_misc_workout_duration), event.durationSeconds()?.let { DurationTag.formatTime(it) })
        KindCardField(stringRes(Res.string.kc_misc_workout_interval), event.intervalSeconds()?.let { durationLabel(it.toLong()) })
        KindCardField(stringRes(Res.string.kc_misc_workout_rest_rounds), event.restBetweenRoundsSeconds()?.let { durationLabel(it.toLong()) })
        KindCardField(stringRes(Res.string.kc_misc_workout_rest_sets), event.restBetweenSetsSeconds()?.let { durationLabel(it.toLong()) })
        KindCardField(stringRes(Res.string.kc_misc_workout_difficulty), event.difficulty())
        exercises.forEach {
            val sets = pluralStringRes(Res.plurals.kc_misc_workout_sets, it.sets, it.sets)
            KindCardField(it.name, listOfNotNull(sets, it.detail).joinToString(" "))
        }
        KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav)
        KindCardChips(topics)
    }
}
