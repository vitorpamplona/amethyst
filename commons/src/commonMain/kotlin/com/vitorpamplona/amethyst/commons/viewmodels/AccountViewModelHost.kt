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
package com.vitorpamplona.amethyst.commons.viewmodels

import com.vitorpamplona.amethyst.commons.service.pow.PoWJobFailure
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayStats
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import kotlinx.coroutines.flow.Flow

/**
 * What the account's ViewModel needs from the process it runs in: app-wide services that outlive
 * any one account, and the few actions only the platform can perform. Android implements it over
 * its app modules; previews pass a no-op.
 */
interface AccountViewModelHost {
    /** Fires when memory is tight enough that feeds should drop the Notes they hold. */
    val memoryPressure: Flow<Unit>

    /** Whether the local Blossom cache answers its probe. */
    val localBlossomCacheAvailable: Flow<Boolean>

    /** Background proof-of-work posts that could not be signed or sent. */
    val powPublishFailures: Flow<PoWJobFailure>

    /** Per-relay connection statistics, which order the relays a crawl visits. */
    val relayStats: RelayStats

    /** Opens sockets for the throwaway clients crawls use (Event Sync, Cashu discovery). */
    val websocketBuilder: WebsocketBuilder

    /** Public keys of every account saved on this device. */
    val savedAccounts: Flow<Set<HexKey>>

    /** Removes the system notification posted for [eventId], if there is one. */
    fun dismissNotificationFor(eventId: HexKey)

    /** Hands a BOLT11 [invoice] to an installed wallet app. False when none could take it. */
    fun openLightningWallet(invoice: String): Boolean
}
