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
package com.vitorpamplona.amethyst.commons.feeds

import com.vitorpamplona.quartz.experimental.decoupling.transfer.request.EncryptionKeyRequestEvent
import com.vitorpamplona.quartz.experimental.decoupling.transfer.response.EncryptionKeyTransferEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.WorkoutTemplateEvent
import com.vitorpamplona.quartz.experimental.postingStreak.PostingStreakEvent
import com.vitorpamplona.quartz.experimental.profileTheme.active.ActiveProfileThemeEvent
import com.vitorpamplona.quartz.experimental.profileTheme.definition.ThemeDefinitionEvent
import com.vitorpamplona.quartz.experimental.topEight.TopEightEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip18Reposts.BaseRepostEvent
import com.vitorpamplona.quartz.nip23LongContent.draft.LongFormDraftEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.stickers.StickerPackEvent
import com.vitorpamplona.quartz.nip34Git.ci.coordinatorAdvertisement.CiCoordinatorAdvertisementEvent
import com.vitorpamplona.quartz.nip34Git.ci.jobResult.CiJobResultEvent
import com.vitorpamplona.quartz.nip34Git.ci.manualTrigger.CiManualTriggerEvent
import com.vitorpamplona.quartz.nip34Git.ci.repositoryStatus.CiRepositoryStatusEvent
import com.vitorpamplona.quartz.nip34Git.ci.requestReadiness.CiRequestReadinessListEvent
import com.vitorpamplona.quartz.nip34Git.ci.secretUpdate.CiSecretUpdateEvent
import com.vitorpamplona.quartz.nip34Git.ci.serviceRequest.CiServiceRequestEvent
import com.vitorpamplona.quartz.nip34Git.ci.serviceStop.CiServiceStopEvent
import com.vitorpamplona.quartz.nip34Git.ci.workflowProgress.CiWorkflowProgressEvent
import com.vitorpamplona.quartz.nip34Git.ci.workflowResult.CiWorkflowResultEvent
import com.vitorpamplona.quartz.nip34Git.coverNote.GitCoverNoteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.SiteSnapshotEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroDevFee.MostroDevFeePaymentEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroDispute.MostroDisputeEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.MostroInfoEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroRating.MostroUserRatingEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.robosatsRating.RoboSatsCoordinatorRatingEvent
import com.vitorpamplona.quartz.nipXXPrivateNoteStorage.PnsEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * A repost (kind 6 / kind 16) wraps another event. When that inner kind has no
 * typed Quartz class — e.g. a kind some app invented that Quartz does not model,
 * wrapped in a generic repost — Amethyst can neither parse nor render it, so the repost would
 * show as a permanently blank card. Feeds use this predicate in their acceptance
 * allow-list to drop such reposts, mirroring how regular unknown-kind events are
 * never displayed (no UI component renders a bare [Event]).
 *
 * Returns true only for reposts whose boosted content is displayable, so it can
 * replace the `is RepostEvent || is GenericRepostEvent` clause in a feed's
 * acceptance allow-list. Non-reposts return false (they are admitted by the
 * other clauses).
 *
 * A kind Quartz types but Amethyst has no card for ([TYPED_WITHOUT_A_CARD]) is hidden too: it would
 * fall through to the plain-text card and show its raw `content` — a CI log tail, an empty theme.
 *
 * Conservative: a repost that declares no boosted `k` kind is assumed renderable
 * — we only hide when we can positively prove the inner kind is unknown.
 *
 * The `returns(true) implies non-null` contract lets it stand in for the two
 * `is` checks in an allow-list without losing the chain's non-null smart-cast
 * (the `&& filterParams.match(noteEvent, …)` tail relies on it).
 */
@OptIn(ExperimentalContracts::class)
fun Event?.isRenderableRepost(): Boolean {
    contract { returns(true) implies (this@isRenderableRepost != null) }
    if (this !is BaseRepostEvent) return false
    val boostedKind = boostedKind()
    return boostedKind == null || (EventFactory.isKnownKind(boostedKind) && boostedKind !in TYPED_WITHOUT_A_CARD)
}

/**
 * Kinds Quartz parses but Amethyst draws no card for, so a repost of one stays hidden exactly as it
 * was while the kind was untyped. These were typed after the 2026-10-08 relay census so that the
 * stores, search and graph understand them; typing a kind must not, by itself, put it in a feed.
 *
 * Remove a kind from here in the change that gives it a card.
 */
val TYPED_WITHOUT_A_CARD: Set<Int> =
    setOf(
        // NIP-34 cover notes and the Nostr CI family: shown on their repository's pages, not alone.
        GitCoverNoteEvent.KIND,
        CiManualTriggerEvent.KIND,
        CiJobResultEvent.KIND,
        CiWorkflowResultEvent.KIND,
        CiServiceRequestEvent.KIND,
        CiServiceStopEvent.KIND,
        CiWorkflowProgressEvent.KIND,
        CiCoordinatorAdvertisementEvent.KIND,
        CiRequestReadinessListEvent.KIND,
        CiRepositoryStatusEvent.KIND,
        CiSecretUpdateEvent.KIND,
        // P2P-trading side traffic: ratings, instance terms, disputes, fee receipts.
        MostroUserRatingEvent.KIND,
        MostroInfoEvent.KIND,
        MostroDisputeEvent.KIND,
        MostroDevFeePaymentEvent.KIND,
        RoboSatsCoordinatorRatingEvent.KIND,
        // Encrypted or key-transfer traffic: nothing to show.
        PnsEvent.KIND,
        EncryptionKeyRequestEvent.KIND,
        EncryptionKeyTransferEvent.KIND,
        // Siblings of kinds that have cards, which the cards do not handle yet.
        SiteSnapshotEvent.KIND,
        LongFormDraftEvent.KIND,
        StickerPackEvent.KIND,
        WorkoutTemplateEvent.KIND,
        // Profile customisation: belongs on a profile, not in a feed.
        ActiveProfileThemeEvent.KIND,
        ThemeDefinitionEvent.KIND,
        PostingStreakEvent.KIND,
        TopEightEvent.KIND,
    )
