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
package com.vitorpamplona.quartz.concord.cord04Roles

import com.vitorpamplona.quartz.concord.events.ConcordKinds
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler

/**
 * Builds unsigned kind-3308 Control Plane edition rumors (the inverse of
 * [ControlEdition.fromRumor]). Seal these with a plaintext (20014) seal and wrap
 * them on the community's Control Plane so the author's signature survives
 * re-encryption across epochs.
 */
object ControlEditionBuilder {
    /**
     * Assembles a control edition rumor for [entityKind]/[entityId] at [version].
     * Pass [prevHash] to chain onto the previous edition (null for genesis) and
     * [authorityCitation] to pin the Grant the [authorPubKey] acts under.
     */
    fun rumor(
        authorPubKey: HexKey,
        entityKind: ControlEntityKind,
        entityId: ByteArray,
        version: Long,
        prevHash: ByteArray?,
        content: String,
        createdAt: Long,
        authorityCitation: AuthorityCitation? = null,
    ): Event {
        val tags = ArrayList<Array<String>>(5)
        tags.add(arrayOf(ControlEdition.TAG_VSK, entityKind.wire))
        tags.add(arrayOf(ControlEdition.TAG_EID, entityId.toHexKey()))
        tags.add(arrayOf(ControlEdition.TAG_EV, version.toString()))
        if (prevHash != null) tags.add(arrayOf(ControlEdition.TAG_EP, prevHash.toHexKey()))
        if (authorityCitation != null) {
            tags.add(
                arrayOf(
                    ControlEdition.TAG_VAC,
                    authorityCitation.grantId.toHexKey(),
                    authorityCitation.grantVersion.toString(),
                    authorityCitation.grantHash.toHexKey(),
                ),
            )
        }
        return RumorAssembler.assembleRumor(authorPubKey, createdAt, ConcordKinds.CONTROL, tags.toTypedArray(), content)
    }
}
