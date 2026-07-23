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
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.tags.ReasonTag
import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.OwnerAttestation
import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.tags.AuthTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip70ProtectedEvts.protect
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz NIP-IA unarchive request (`kind:9036`): asks the relay to lift a prior archive
 * on a target identity. Same wire shape as [ArchiveRequestEvent] minus `replaced-by`
 * (which has no meaning on unarchive per spec): NIP-70 protected, `p`-tags the [target],
 * optional [reason] code, optional NIP-OA [AuthTag] for the owner-of-agent path, and an
 * optional human-readable `content`. The relay emits the relay-signed
 * [UnarchivedIdentityEvent] delta on success. Ground truth:
 * `buzz-sdk/src/builders.rs::build_unarchive_identity_request`.
 */
@Immutable
class UnarchiveRequestEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The archival target — the single `p` tag. */
    fun target() = tags.archivalTarget()

    /** The machine-readable `reason` code, if present. */
    fun reason() = tags.archivalReason()

    /** The NIP-OA owner attestation from the `auth` tag, if present. */
    fun auth() = tags.archivalAuth()

    /** Whether the NIP-70 `-` protection marker is present (it always should be). */
    fun isProtected() = tags.isProtected()

    companion object {
        const val KIND = 9036

        fun build(
            target: HexKey,
            content: String = "",
            reason: String? = null,
            auth: OwnerAttestation? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<UnarchiveRequestEvent>.() -> Unit = {},
        ) = eventTemplate<UnarchiveRequestEvent>(KIND, content, createdAt) {
            protect()
            addUnique(PTag.assemble(target, null))
            reason?.let { addUnique(ReasonTag.assemble(it)) }
            auth?.let { addUnique(AuthTag.assemble(it)) }
            initializer()
        }
    }
}
