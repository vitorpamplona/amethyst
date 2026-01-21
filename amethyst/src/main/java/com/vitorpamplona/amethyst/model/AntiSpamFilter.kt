/**
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
package com.vitorpamplona.amethyst.model

import android.util.LruCache
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.ui.note.njumpLink
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.MutableStateFlow

data class Spammer(
    val pubkeyHex: HexKey,
    var duplicatedEventIds: Set<HexKey>,
    var duplicatedEventAddresses: Set<Address>,
) {
    fun shouldHide() = duplicatedEventIds.size >= 5 || duplicatedEventAddresses.size >= 5
}

class AntiSpamFilter {
    val recentEventIds = LruCache<Int, String>(2000)
    val recentAddressables = LruCache<Int, Address>(2000)
    val spamMessages = LruCache<Int, Spammer>(1000)

    var active: Boolean = true

    fun spamHashCode(event: Event): Int = 31 * event.content.hashCode() + event.tags.contentDeepHashCode()

    fun isSpam(
        event: Event,
        relay: NormalizedRelayUrl?,
    ): Boolean {
        if (!active) return false

        // if short message, ok
        // The idea here is to avoid considering repeated "GM" messages spam.
        if (event.content.length < 60) return false

        // if the message is actually short but because it cites a user/event, the nostr: string is
        // really long, make it ok.
        // The idea here is to avoid considering repeated "@Bot, command" messages spam, while still
        // blocking repeated "lnbc..." invoices or fishing urls
        if (event.content.length < 180 && event.content.startsWith("nostr:") && Nip19Parser.hasAny(event.content)) return false

        // double list strategy:
        // if duplicated, it goes into spam. 1000 spam messages are saved into the spam list.

        // Considers tags so that same replies to different people don't count.
        val hash = spamHashCode(event)

        // ignores multiple versions of the same addressable.
        if (event is AddressableEvent) {
            val address = event.address()

            // normal event
            if (
                (recentAddressables[hash] != null && recentAddressables[hash] != address) ||
                (spamMessages[hash] != null && !spamMessages[hash].duplicatedEventAddresses.contains(address))
            ) {
                val existingAddress = recentAddressables[hash]

                val link1 = njumpLink(NAddress.create(existingAddress.kind, existingAddress.pubKeyHex, existingAddress.dTag, relay))
                val link2 = njumpLink(NAddress.create(event.kind, event.pubKey, event.dTag(), relay))

                Log.w("Duplicated/SPAM", "${relay?.url} $link1 $link2")

                // Log down offenders
                val spammer = logOffender(hash, event)

                if (spammer.shouldHide() && relay != null) {
                    Amethyst.instance.relayStats
                        .get(relay)
                        .newSpam(link1, link2)
                }

                flowSpam.tryEmit(AntiSpamState(this))

                return true
            }

            recentAddressables.put(hash, address)
        } else {
            // normal event
            val existingEvent = recentEventIds[hash]
            if (
                (existingEvent != null && existingEvent != event.id) ||
                (spamMessages[hash] != null && !spamMessages[hash].duplicatedEventIds.contains(event.id))
            ) {
                val link1 = njumpLink(NEvent.create(existingEvent, null, null, relay))
                val link2 = njumpLink(NEvent.create(event.id, null, null, relay))

                Log.w("Duplicated/SPAM", "${relay?.url} $link1 $link2")

                // Log down offenders
                val spammer = logOffender(hash, event)

                if (spammer.shouldHide() && relay != null) {
                    Amethyst.instance.relayStats
                        .get(relay)
                        .newSpam(link1, link2)
                }

                flowSpam.tryEmit(AntiSpamState(this))

                return true
            }

            recentEventIds.put(hash, event.id)
        }

        return false
    }

    @Synchronized
    private fun logOffender(
        hashCode: Int,
        event: Event,
    ): Spammer {
        val spammer = spamMessages.get(hashCode)

        if (spammer == null) {
            val newSpammer =
                if (event is AddressableEvent) {
                    Spammer(
                        pubkeyHex = event.pubKey,
                        duplicatedEventIds = setOf(),
                        duplicatedEventAddresses = setOf(recentAddressables[hashCode], event.address()),
                    )
                } else {
                    Spammer(
                        pubkeyHex = event.pubKey,
                        duplicatedEventIds = setOf(recentEventIds[hashCode], event.id),
                        duplicatedEventAddresses = setOf(),
                    )
                }
            spamMessages.put(hashCode, newSpammer)
            return newSpammer
        } else {
            if (event is AddressableEvent) {
                spammer.duplicatedEventAddresses += event.address()
            } else {
                spammer.duplicatedEventIds += event.id
            }
            return spammer
        }
    }

    val flowSpam = MutableStateFlow<AntiSpamState>(AntiSpamState(this))
}

class AntiSpamState(
    val cache: AntiSpamFilter,
)
