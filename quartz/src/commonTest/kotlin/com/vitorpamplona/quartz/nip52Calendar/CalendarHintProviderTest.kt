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
package com.vitorpamplona.quartz.nip52Calendar

import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The broadcaster (EventBroadcaster) resolves a published event's relay set from these two
 * interfaces: `p` tags become inbox relays of everyone tagged, `a` tags get followed into the
 * referenced addressable and recursed. Without them an RSVP reaches only the sender's outbox.
 */
class CalendarHintProviderTest {
    private val host = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val invitee = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val rsvper = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
    private val apptAddress = "31923:$host:my-party"
    private val relay = "wss://relay.damus.io/"

    private fun rsvp(vararg tags: Array<String>) =
        CalendarRSVPEvent(
            id = "00".repeat(32),
            pubKey = rsvper,
            createdAt = 1700000000,
            tags = arrayOf(*tags),
            content = "",
            sig = "00".repeat(64),
        )

    private fun timeSlot(vararg tags: Array<String>) =
        CalendarTimeSlotEvent(
            id = "11".repeat(32),
            pubKey = host,
            createdAt = 1700000000,
            tags = arrayOf(*tags),
            content = "",
            sig = "00".repeat(64),
        )

    private fun dateSlot(vararg tags: Array<String>) =
        CalendarDateSlotEvent(
            id = "22".repeat(32),
            pubKey = host,
            createdAt = 1700000000,
            tags = arrayOf(*tags),
            content = "",
            sig = "00".repeat(64),
        )

    @Test
    fun rsvpExposesTheTargetAppointmentAsALinkedAddress() {
        val event = rsvp(arrayOf("a", apptAddress, relay), arrayOf("status", "accepted"))

        // Typed, not `is`: the point is that the class declares the interface at compile time.
        val provider: AddressHintProvider = event
        assertEquals(listOf(apptAddress), provider.linkedAddressIds())

        val hint = provider.addressHints().single()
        assertEquals(apptAddress, hint.addressId)
        assertEquals(RelayUrlNormalizer.normalizeOrNull(relay), hint.relay)
    }

    @Test
    fun rsvpWithoutARelayHintStillLinksTheAddress() {
        val event = rsvp(arrayOf("a", apptAddress), arrayOf("status", "accepted"))

        assertEquals(listOf(apptAddress), event.linkedAddressIds())
        assertTrue(event.addressHints().isEmpty())
    }

    @Test
    fun rsvpLinksEveryTaggedParticipantNotJustTheHost() {
        val event =
            rsvp(
                arrayOf("a", apptAddress, relay),
                arrayOf("p", host),
                arrayOf("p", invitee, relay),
                arrayOf("status", "accepted"),
            )

        assertEquals(listOf(host, invitee), event.linkedPubKeys())
        assertEquals(listOf(invitee), event.pubKeyHints().map { it.pubkey })
    }

    @Test
    fun timeSlotExposesParticipantsAsLinkedPubKeys() {
        val event = timeSlot(arrayOf("d", "my-party"), arrayOf("p", invitee, relay, "speaker"))

        val provider: PubKeyHintProvider = event
        assertEquals(listOf(invitee), provider.linkedPubKeys())

        val hint = provider.pubKeyHints().single()
        assertEquals(invitee, hint.pubkey)
        assertEquals(RelayUrlNormalizer.normalizeOrNull(relay), hint.relay)
    }

    @Test
    fun dateSlotExposesParticipantsAsLinkedPubKeys() {
        val event = dateSlot(arrayOf("d", "my-party"), arrayOf("p", invitee, relay, "speaker"))

        val provider: PubKeyHintProvider = event
        assertEquals(listOf(invitee), provider.linkedPubKeys())
        assertEquals(invitee, provider.pubKeyHints().single().pubkey)
    }
}
