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
package com.vitorpamplona.quartz.cordn.spec00Coordinator

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/** A KeyPackage the coordinator is holding, as `kp_list` reports it. */
data class AvailableKeyPackage(
    val pubKey: HexKey,
    val keyPackageRef: String,
    val lastResort: Boolean,
    val at: Long,
)

/**
 * A KeyPackage taken from the coordinator, with the signed publication payload
 * that binds it to its owner.
 *
 * [publicationEvent] is the `kp_publish` request event itself, stored verbatim
 * and served back (`spec/00.md` §7). Verify it with
 * [KeyPackagePublication.verify] before touching [keyPackageBase64]: an
 * unverified pair is a coordinator's word about whose key this is, which §10
 * explicitly refuses to rely on.
 */
data class TakenKeyPackage(
    val pubKey: HexKey,
    val keyPackageRef: String,
    val lastResort: Boolean,
    val at: Long,
    val publicationEvent: Event,
) {
    /**
     * The KeyPackage bytes as base64, read back out of the publication event
     * rather than from a coordinator-supplied field.
     *
     * Deliberately not a stored property: the point of §9 is that the KeyPackage
     * you use is the one inside the signed payload, not one handed over
     * alongside it.
     */
    fun keyPackageBase64(): String? = KeyPackagePublication.keyPackageBase64Of(publicationEvent)
}

/** A Welcome waiting in our inbox. */
data class PendingWelcome(
    val keyPackageRef: String,
    val welcomeBase64: String,
    val at: Long,
    /** The cursor to resume a group's history from, when the inviter set one. */
    val after: Long? = null,
)

/** Someone asking to join a group we administer. */
data class JoinRequest(
    val gid: String,
    val pubKey: HexKey,
    val keyPackageRef: String,
    val at: Long,
)

/** One sealed payload from a group's ordered stream. */
data class GroupMessage(
    val gid: String,
    /**
     * This coordinator's ordering primitive for this group.
     *
     * `spec/00.md` §4-5: scoped to one group on one coordinator, and
     * explicitly NOT a message identity. The canonical id is the envelope's
     * (`spec/02.md` §7); a cursor cannot survive a coordinator change and two
     * coordinators will not agree on one.
     */
    val cursor: Long,
    val sealedBase64: String,
    val at: Long,
)

/** What a welcome the caller has already joined looks like when acknowledging it. */
data class ConsumedWelcomeRef(
    val keyPackageRef: String,
    val at: Long,
)

/** A join request the caller has handled and wants retired. */
data class ConsumedJoinRequestRef(
    val gid: String,
    val pubKey: HexKey,
    val at: Long,
)

/** Result of publishing a KeyPackage. */
data class PublishedKeyPackage(
    val keyPackageRef: String,
    val lastResort: Boolean,
    val at: Long,
)

/** Result of posting a message. */
data class PostedMessage(
    val gid: String,
    val cursor: Long,
    val at: Long,
)

/** Raised when the coordinator answers with a JSON-RPC error or an unusable body. */
class CoordinatorException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
