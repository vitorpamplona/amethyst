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
import com.vitorpamplona.quartz.nip01Core.core.BaseReplaceableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip70ProtectedEvts.protect
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz NIP-IA archived-identities list snapshot (`kind:13535`), **signed by the relay**
 * and replaceable by convention. Carries the NIP-70 `-` protection marker and one bare `p`
 * tag per currently-archived identity; `content` is empty. Republished by the relay after
 * every archive/unarchive so the latest event is always the authoritative archived set.
 * Clients never publish this kind; [build] exists for fixtures/tests. Ground truth:
 * `buzz-relay/src/handlers/side_effects.rs::publish_nipia_archival_list`.
 */
@Immutable
class ArchivedIdentitiesListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseReplaceableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** Every archived-identity pubkey listed as a bare `p` tag. */
    fun archivedIdentities() = tags.archivedIdentities()

    /** Whether the NIP-70 `-` protection marker is present (it always should be). */
    fun isProtected() = tags.isProtected()

    companion object {
        const val KIND = 13535

        fun build(
            archivedIdentities: List<HexKey>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ArchivedIdentitiesListEvent>.() -> Unit = {},
        ) = eventTemplate<ArchivedIdentitiesListEvent>(KIND, "", createdAt) {
            protect()
            archivedIdentities.forEach { addUniqueValueIfNew(PTag.assemble(it, null)) }
            initializer()
        }
    }
}
