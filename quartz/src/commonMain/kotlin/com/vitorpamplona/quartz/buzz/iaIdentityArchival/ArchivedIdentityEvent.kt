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
package com.vitorpamplona.quartz.buzz.iaIdentityArchival

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.tags.ConsentTag
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.tags.ReasonTag
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.tags.ReplacedByTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip70ProtectedEvts.protect
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz NIP-IA archived-identity delta (`kind:8002`), **signed by the relay**. Emitted
 * after the relay accepts an [ArchiveRequestEvent] and verifies its consent path. It is
 * NIP-70 protected, `p`-tags the archived [target], records the [ConsentTag] path + actor,
 * `e`-tags the originating [requestId], and echoes the optional [reason] and [replacedBy].
 * `content` carries the request's human-readable reason. Clients never publish this kind;
 * [build] exists for generating fixtures and round-trip tests. Ground truth:
 * `buzz-relay/src/handlers/side_effects.rs::publish_nipia_delta` (kind = `KIND_IA_ARCHIVED`).
 */
@Immutable
class ArchivedIdentityEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The archived target — the single `p` tag. */
    fun target() = tags.archivalTarget()

    /** The relay-written consent record: path + the actor that signed the request. */
    fun consent() = tags.archivalConsent()

    /** The originating archive request's event id — the `e` tag. */
    fun requestId() = tags.archivalRequestId()

    /** The machine-readable `reason` code, if present. */
    fun reason() = tags.archivalReason()

    /** The `replaced-by` rotation pointer, if present. */
    fun replacedBy() = tags.archivalReplacedBy()

    /** Whether the NIP-70 `-` protection marker is present (it always should be). */
    fun isProtected() = tags.isProtected()

    companion object {
        const val KIND = 8002

        fun build(
            target: HexKey,
            consentPath: String,
            actorPubKey: HexKey,
            requestId: HexKey,
            content: String = "",
            reason: String? = null,
            replacedBy: HexKey? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ArchivedIdentityEvent>.() -> Unit = {},
        ) = eventTemplate<ArchivedIdentityEvent>(KIND, content, createdAt) {
            protect()
            addUnique(PTag.assemble(target, null))
            addUnique(ConsentTag.assemble(consentPath, actorPubKey))
            addUnique(ETag.assemble(requestId, null, null))
            reason?.let { addUnique(ReasonTag.assemble(it)) }
            replacedBy?.let { addUnique(ReplacedByTag.assemble(it)) }
            initializer()
        }
    }
}
