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
package com.vitorpamplona.geode

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchFirst
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * NIP-40 expiration:
 *  - The relay rejects EVENTs whose `expiration` tag is in the past
 *    (the SQLite store's `insertEvent` raises on `event.isExpired()`).
 *  - Events with a future expiration are stored and queryable until
 *    the operator calls `deleteExpiredEvents()` (or sufficient time
 *    passes that `isExpired()` returns true on read).
 */
class Nip40ExpirationTest {
    private lateinit var hub: InProcessRelays
    private lateinit var scope: CoroutineScope
    private lateinit var client: NostrClient
    private val relayUrl: NormalizedRelayUrl = RelayUrlNormalizer.normalize("ws://127.0.0.1:7770/")

    @BeforeTest
    fun setup() {
        hub = InProcessRelays()
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        client = NostrClient(hub, scope)
    }

    @AfterTest
    fun teardown() {
        client.disconnect()
        scope.cancel()
        hub.close()
    }

    @Test
    fun expiredEventOnArrivalIsRejectedWithOkFalse() =
        runBlocking {
            val signer = NostrSignerSync(KeyPair())
            // expiration set to a past timestamp.
            val past = TimeUtils.now() - 60
            val event =
                signer.sign(
                    TextNoteEvent.build("expired") {
                        expiration(past)
                    },
                )

            val ok = client.publishAndConfirm(event, setOf(relayUrl))
            assertEquals(false, ok, "expired-on-arrival event must be rejected")
        }

    @Test
    fun nonExpiredEventIsStoredAndRetrievable() =
        runBlocking {
            val signer = NostrSignerSync(KeyPair())
            val future = TimeUtils.now() + 3600
            val event =
                signer.sign(
                    TextNoteEvent.build("not-yet-expired") {
                        expiration(future)
                    },
                )

            assertEquals(true, client.publishAndConfirm(event, setOf(relayUrl)))

            val fetched =
                client.fetchFirst(
                    relay = relayUrl,
                    filter = Filter(ids = listOf(event.id)),
                )
            assertNotNull(fetched)
            assertEquals(event.id, fetched.id)
        }

    @Test
    fun deleteExpiredEventsRemovesThemFromStore() =
        runBlocking {
            val signer = NostrSignerSync(KeyPair())
            val now = TimeUtils.now()

            // `insertEvent` rejects an event once `expiration <= now()`, so an
            // already-expired event can't be stored to test the sweep. Instead
            // publish a long-lived event and a short-lived one, wait out the
            // short one on the clock, then sweep.
            //
            // The short-lived one needs a few seconds of margin: `now()` is
            // whole seconds, so with `now + 1` the second could tick over
            // before the publish reaches the store (anywhere from 0 to 1 s
            // later) and the relay would reject it on arrival.
            val shortExpiration = now + SHORT_LIVED_SECS
            val longLived =
                signer.sign(TextNoteEvent.build("keep-me") { expiration(now + 3600) })
            val shortLived =
                signer.sign(TextNoteEvent.build("sweep-me") { expiration(shortExpiration) })

            assertEquals(true, client.publishAndConfirm(longLived, setOf(relayUrl)), "long-lived event must be accepted")
            assertEquals(true, client.publishAndConfirm(shortLived, setOf(relayUrl)), "short-lived event must be accepted before it expires")

            // Both currently in the store.
            assertNotNull(
                client.fetchFirst(relay = relayUrl, filter = Filter(ids = listOf(longLived.id))),
            )
            assertNotNull(
                client.fetchFirst(relay = relayUrl, filter = Filter(ids = listOf(shortLived.id))),
            )

            // The sweep deletes `expiration < unixepoch()` (whole seconds), so
            // wait on the clock until now() is past the expiration rather
            // than for a fixed time that a slow runner could fall short of.
            while (TimeUtils.now() <= shortExpiration) delay(100)
            hub.getOrCreate(relayUrl).store.deleteExpiredEvents()

            // Long-lived survives.
            assertNotNull(
                client.fetchFirst(relay = relayUrl, filter = Filter(ids = listOf(longLived.id))),
            )
            // Short-lived is gone.
            assertEquals(
                null,
                client.fetchFirst(relay = relayUrl, filter = Filter(ids = listOf(shortLived.id))),
                "deleteExpiredEvents() must purge the short-lived event",
            )
        }

    private companion object {
        /** How far out the swept event expires: room for a slow publish, short enough to keep the test quick. */
        const val SHORT_LIVED_SECS = 3L
    }
}
