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
package com.vitorpamplona.amethyst.commons.model.marmot.devices

import com.vitorpamplona.marmot.OutboundGroupEvent
import com.vitorpamplona.marmot.WelcomeDelivery
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent

/**
 * What [MarmotDevices] needs from the front end's relay wiring: the Android/desktop `Account`
 * and `amy` each implement it over their own client. Every call returns whether a relay
 * accepted the event (an ack), so a heartbeat or KeyPackage is only counted as sent when it was.
 */
interface MarmotDevicesTransport {
    /** A kind 445 to its group's relays. */
    suspend fun publishGroupEvent(event: OutboundGroupEvent): Boolean

    /**
     * A gift wrap from this account to itself, to the account's own kind 10050 inbox relays:
     * pairing messages (452-456) and the device group's kind 444 Welcome for a new device.
     */
    suspend fun publishToSelf(wrap: GiftWrapEvent): Boolean

    /** An invitee's Welcome, to its inbox relays. */
    suspend fun publishWelcome(delivery: WelcomeDelivery): Boolean

    /** Publish (and wait for a relay to accept) a KeyPackage this device just made. */
    suspend fun publishKeyPackage(event: KeyPackageEvent): Boolean

    /** This account's KeyPackage event [eventId], from the relays it was published to, or null. */
    suspend fun fetchOwnKeyPackage(eventId: HexKey): KeyPackageEvent?

    /** Every live kind 30443 of this account (one per publication slot), or null when unknown. */
    suspend fun ownKeyPackages(): List<KeyPackageEvent>?

    /** A signed event of this account (a kind 5) to its outbox and KeyPackage relays. */
    suspend fun publishOwnEvent(event: Event): Boolean
}
