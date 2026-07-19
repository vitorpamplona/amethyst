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
package com.vitorpamplona.amethyst.cli

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * Concord plane stream-key AUTH registry (CORD-01 §4b). Concord relays gate a
 * plane's kind-1059 wraps behind NIP-42 and serve them only to a connection
 * authenticated AS the plane's derived *stream key* — the member is neither the
 * wrap's author (the stream key) nor its recipient (a throwaway ephemeral key),
 * so an account AUTH is refused. `amy concord` verbs register their control +
 * channel stream secrets here (scoped to the community's relays, the same scope
 * the plane REQ uses) before draining, and the [Context]'s NIP-42 responder
 * answers a challenge from one of those relays with one kind-22242 per stream
 * key — signed locally from the raw derived key, never the account, so no user
 * identity is exposed.
 */
class ConcordAuth {
    private val streamSecrets = ConcurrentHashMap<NormalizedRelayUrl, MutableSet<HexKey>>()
    private val streamSigners = ConcurrentHashMap<HexKey, NostrSignerSync>()

    /** Registers raw 32-byte Concord stream [secrets] to answer NIP-42 challenges from [relays]. */
    fun register(
        relays: Set<NormalizedRelayUrl>,
        secrets: List<ByteArray>,
    ) {
        if (relays.isEmpty() || secrets.isEmpty()) return
        val hexes = secrets.map { it.toHexKey() }
        for (relay in relays) streamSecrets.getOrPut(relay) { ConcurrentHashMap.newKeySet() }.addAll(hexes)
    }

    /** Signs one kind-22242 per Concord stream key registered for [relay] (empty if none). */
    fun signAuths(
        relay: NormalizedRelayUrl,
        template: EventTemplate<RelayAuthEvent>,
    ): List<RelayAuthEvent> =
        streamSecrets[relay].orEmpty().mapNotNull { hex ->
            runCatching {
                streamSigners.getOrPut(hex) { NostrSignerSync(KeyPair(privKey = hex.hexToByteArray())) }.sign(template)
            }.getOrNull()
        }
}
