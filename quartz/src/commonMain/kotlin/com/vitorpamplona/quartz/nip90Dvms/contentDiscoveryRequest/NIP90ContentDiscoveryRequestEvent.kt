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
package com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryRequest

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip90Dvms.tags.InputTag
import com.vitorpamplona.quartz.nip90Dvms.tags.dvmParam
import com.vitorpamplona.quartz.nip90Dvms.tags.inputs
import com.vitorpamplona.quartz.utils.TimeUtils

@Stable
@Immutable
class NIP90ContentDiscoveryRequestEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun inputs(): List<InputTag> = tags.inputs()

    fun dvmPubKey(): HexKey? = tags.firstOrNull { it.size >= 2 && it[0] == "p" }?.get(1)

    fun relays() = tags.relays()

    fun params() = tags.params()

    fun user(): String? = tags.dvmParam("user")

    fun maxResults(): String? = tags.dvmParam("max_results")

    companion object {
        const val KIND = 5300
        const val ALT = "NIP90 Content Discovery request"

        fun build(
            dvmPublicKey: HexKey,
            forUser: HexKey,
            relays: Set<NormalizedRelayUrl>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<NIP90ContentDiscoveryRequestEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            alt(ALT)
            dvmPubKey(dvmPublicKey)
            relays(relays)
            param("max_results", "200")
            param("user", forUser)
            initializer()
        }
    }
}
