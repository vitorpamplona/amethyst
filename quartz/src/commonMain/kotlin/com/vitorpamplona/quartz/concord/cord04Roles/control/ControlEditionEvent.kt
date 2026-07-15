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
package com.vitorpamplona.quartz.concord.cord04Roles.control

import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityCitation
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A kind-3308 Control Plane edition (CORD-02/04) — versioned, chainable community
 * state (metadata, roles, channels, grants, banlist, invites, …). Authored as an
 * unsigned rumor, plaintext-sealed and wrapped on the community's Control Plane so
 * the author signature survives re-encryption across epochs.
 *
 * The wire shape is the entity content plus the `vsk`/`eid`/`ev`/`ep`/`vac`
 * binding tags (see this package's `tags/`). The folded domain view — with the
 * derived [com.vitorpamplona.quartz.concord.crypto.EditionHash] chain — is
 * [com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition], which reads these
 * accessors.
 */
class ControlEditionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun entityKind() = tags.vsk()

    fun entityId() = tags.eid()

    fun version() = tags.ev()

    fun prevHash() = tags.ep()

    fun authorityCitation() = tags.vac()

    companion object {
        const val KIND = 3308

        /**
         * Builds the edition template for [entityKind]/[entityId] at [version].
         * Tags are emitted in the fixed `vsk, eid, ev, ep?, vac?` order the chain's
         * rumor ids depend on. Assemble it into a rumor with the author pubkey via
         * `RumorAssembler`.
         */
        fun build(
            entityKind: ControlEntityKind,
            entityId: ByteArray,
            version: Long,
            content: String,
            prevHash: ByteArray? = null,
            authorityCitation: AuthorityCitation? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ControlEditionEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, content, createdAt) {
            vsk(entityKind)
            eid(entityId)
            ev(version)
            prevHash?.let { ep(it) }
            authorityCitation?.let { vac(it) }
            initializer()
        }
    }
}
