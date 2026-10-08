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
package com.vitorpamplona.quartz.nip69P2pOrderEvents.robosatsRating

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.fastAny
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.robosatsRating.tags.CoordinatorTokenTag
import com.vitorpamplona.quartz.utils.Secp256k1InstanceKotlin
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A robot's rating of the RoboSats coordinator it just traded with (kind 31986).
 *
 * **No written spec.** The shape is RoboSats' own, read from its code: the frontend publishes it
 * after a successful trade (`frontend/src/components/TradeBox/Prompts/Successful.tsx`), the
 * federation reads it back (`frontend/src/services/RoboPool`, `models/Federation`), coordinators
 * sync it between relays with 38383 orders (`docker/strfry/sync.sh`), and roboauto
 * (`roboauto/nostr.py`) publishes the same four tags:
 *
 * - `d` = `<coordinator short alias>:<order id>` (`lake:138621`): one rating per robot per order;
 * - `p` = the coordinator's Nostr pubkey: who is rated;
 * - `rating` = stars / 5, a 0..1 fraction (`"1"`, `"0.8"`);
 * - `sig` = the coordinator's review token, a Schnorr signature over `<this pubkey><order id>`
 *   that proves the reviewer traded there (see [CoordinatorTokenTag]). Clients average only the
 *   ratings whose token verifies.
 *
 * Content is empty. Signed by the robot's per-trade key. It lives beside NIP-69 because it rates
 * the coordinators that publish NIP-69 orders and travels with them; it is not a general review
 * kind. Machine data: not a SearchableEvent.
 *
 * The order id is RoboSats' internal number, not a Nostr reference: its 38383 orders are
 * addressed by a hash-derived UUID, so no order address can be built from it.
 *
 * **The kind is shared:** Borkstr publishes "NIP compatibility reports" on 31986 with a `rating`
 * and a `p` of their own. Without the coordinator token they are no coordinator rating, so
 * `EventFactory` builds this class only for [isCoordinatorRating] tags and
 * [UnrecognizedKind31986Event] for the rest.
 */
@Immutable
class RoboSatsCoordinatorRatingEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    PubKeyHintProvider {
    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    /** `RATED`: the coordinator this robot rates (`p`). */
    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    /** The rated coordinator's pubkey (`p`). */
    fun coordinator() = tags.coordinator()

    /** The score as a 0..1 fraction; multiply by 5 for RoboSats' stars. */
    fun rating() = tags.coordinatorRating()

    /** The coordinator-signed review token (`sig`). */
    fun coordinatorToken() = tags.coordinatorToken()

    /** The coordinator's short alias, the part of `d` before the first `:` (`lake`, `temple`, ...). */
    fun coordinatorAlias(): String? = dTag().substringBefore(':', "").ifEmpty { null }

    /** The RoboSats order id, the part of `d` after the first `:`. */
    fun orderId(): String? = dTag().substringAfter(':', "").ifEmpty { null }

    /**
     * The text the coordinator signed to make [coordinatorToken]: this event's pubkey followed by
     * the order id, as RoboSats' `verifyCoordinatorToken` rebuilds it. Verify its UTF-8 bytes,
     * unhashed, against [coordinator] with BIP-340.
     */
    fun coordinatorTokenMessage(): String = pubKey + orderId().orEmpty()

    /**
     * True when [coordinatorToken] is the [coordinator]'s BIP-340 signature over
     * [coordinatorTokenMessage]: the coordinator vouches that this robot finished order [orderId]
     * there. RoboSats counts only such ratings. False when any part is missing.
     *
     * Runs on Quartz's pure-Kotlin secp256k1, because the message is longer than 32 bytes and the
     * native binding accepts only 32-byte messages. It is a full signature check: run it once per
     * rating when aggregating, not on every render.
     */
    fun verifyCoordinatorToken(): Boolean {
        val token = coordinatorToken() ?: return false
        val coordinator = coordinator() ?: return false
        if (orderId() == null) return false
        return Secp256k1InstanceKotlin.verifySchnorr(
            token.hexToByteArray(),
            coordinatorTokenMessage().encodeToByteArray(),
            coordinator.hexToByteArray(),
        )
    }

    companion object {
        const val KIND = 31986

        /**
         * True for the RoboSats shape: a well-formed coordinator token (`sig`, 128 hex) and a
         * 64-hex `p`. The token is the part no other publisher of 31986 writes.
         */
        fun isCoordinatorRating(tags: TagArray): Boolean =
            tags.fastAny { CoordinatorTokenTag.parse(it) != null } &&
                tags.fastAny { PTag.parseKey(it) != null }

        /**
         * @param coordinatorAlias the coordinator's short alias, as RoboSats' federation names it.
         * @param orderId the RoboSats order id the token was issued for.
         * @param rating the score as a 0..1 fraction (stars / 5).
         * @param coordinatorToken the token `POST /api/review/` returned for this robot's key.
         */
        fun build(
            coordinatorAlias: String,
            orderId: String,
            coordinator: HexKey,
            rating: Double,
            coordinatorToken: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<RoboSatsCoordinatorRatingEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            coordinatorToken(coordinatorToken)
            dTag("$coordinatorAlias:$orderId")
            coordinator(coordinator)
            coordinatorRating(rating)
            initializer()
        }
    }
}
