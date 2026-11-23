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
package com.vitorpamplona.amethyst.commons.account

import com.vitorpamplona.amethyst.commons.defaults.DefaultGlobalRelays
import com.vitorpamplona.amethyst.commons.defaults.DefaultIndexerRelayList
import com.vitorpamplona.amethyst.commons.defaults.DefaultNIP65RelaySet
import com.vitorpamplona.amethyst.commons.defaults.DefaultSearchRelayList
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** An account made with [bootstrapAccountEvents]'s `relays` advertises that set and no default. */
class AccountBootstrapRelaysTest {
    private fun relayUrlsIn(event: Event): Set<String> =
        event.tags
            .filter { it.size > 1 && (it[0] == "r" || it[0] == "relay") }
            .map { it[1] }
            .toSet()

    @Test
    fun givenRelaysReplaceEveryDefaultSet() {
        val local = RelayUrlNormalizer.normalizeOrNull("ws://127.0.0.1:7777")!!
        val events = bootstrapAccountEvents(NostrSignerSync(KeyPair()), "t", setOf(local))
        // The lists other clients read to reach this account carry their relays as plain tags.
        listOf(events.nip65RelayList, events.dmRelayList, events.keyPackageRelayList)
            .forEach { assertEquals(setOf(local.url), relayUrlsIn(it), "kind ${it.kind}") }
        // Search, indexer and favorites are NIP-51 lists with their relays encrypted in the
        // content; none of them may name a public default in the clear.
        val defaults = (DefaultNIP65RelaySet + DefaultSearchRelayList + DefaultIndexerRelayList + DefaultGlobalRelays).map { it.url }.toSet()
        events.all().forEach { event -> assertTrue(relayUrlsIn(event).none { it in defaults }, "kind ${event.kind}") }
    }

    @Test
    fun withoutRelaysTheDefaultsStay() {
        val events = bootstrapAccountEvents(NostrSignerSync(KeyPair()), "t")
        assertTrue(relayUrlsIn(events.nip65RelayList).containsAll(DefaultNIP65RelaySet.map { it.url }))
    }
}
