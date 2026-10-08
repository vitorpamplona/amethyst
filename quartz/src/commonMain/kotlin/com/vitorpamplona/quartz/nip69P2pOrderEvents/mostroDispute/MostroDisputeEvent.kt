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
package com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroDispute

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip69P2pOrderEvents.instanceName
import com.vitorpamplona.quartz.nip69P2pOrderEvents.platform
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.DocumentTypeTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * The public status of a dispute on a Mostro instance (kind 38386): which party opened it, when,
 * and where it stands. Spec: `mostro_separate_kinds.md` ("Dispute Event"), split off kind 38383.
 * Not a NIP.
 *
 * Signed by the instance and addressable on `d` = the dispute's id (a UUID of the instance's
 * database), so each status change replaces the last. Content is empty. The dispute id is not a
 * Nostr reference and the event names neither the order nor the parties, so it carries no graph
 * edges and implements no hint provider. Machine data: not a SearchableEvent.
 *
 * **The kind is shared:** Paygress publishes standby-promotion announcements on 38386 and a
 * "bondtrade" app its settlement transactions. `EventFactory` builds this class only for
 * [isMostroDispute] tags and [UnrecognizedKind38386Event] for the rest.
 */
@Immutable
class MostroDisputeEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The dispute's id (`d`), a UUID in the instance's database. */
    fun disputeId() = dTag()

    fun platform() = tags.platform()

    fun instanceName() = tags.instanceName()

    /** `initiated`, `in-progress`, `settled`, `seller-refunded`, `released`, ... as published. */
    fun status() = tags.disputeStatus()

    /** `buyer` or `seller`. */
    fun initiator() = tags.initiator()

    /** The `published_at` tag: when the dispute was opened. */
    fun publishedAt() = tags.publishedAt()

    /**
     * When the dispute was opened, resolved as the spec orders it: the `published_at` tag, then
     * the `created_at` tag that daemons from v0.18.5 until the rename published, then the event's
     * own [createdAt] — the time of the latest revision, so possibly later than the opening.
     */
    fun openedAt(): Long = publishedAt() ?: tags.legacyCreatedAt() ?: createdAt

    companion object {
        const val KIND = 38386

        /**
         * True for a Mostro dispute shape: a `z` of `dispute`, which Mostro has written on its
         * disputes since before the kind split. What other apps put on 38386 carries none.
         */
        fun isMostroDispute(tags: TagArray): Boolean = DocumentTypeTag.isType(tags, DocumentTypeTag.DISPUTE)

        fun build(
            disputeId: String,
            status: String,
            initiator: String,
            openedAt: Long,
            expiration: Long? = null,
            instanceName: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<MostroDisputeEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            dTag(disputeId)
            expiration?.let { expiration(it) }
            disputeStatus(status)
            initiator(initiator)
            publishedAt(openedAt)
            platform(instanceName)
            documentType()
            initializer()
        }
    }
}
