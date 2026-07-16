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
package com.vitorpamplona.amethyst.desktop.auth

import com.vitorpamplona.amethyst.commons.relayClient.auth.AuthApprovalScope
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PreferencesAuthApprovalStoreTest {
    // A distinct per-run account so the test never collides with a real user's
    // stored grants under Preferences.userRoot().
    private val accountPubKeyHex = "test${System.nanoTime()}".padEnd(64, '0').take(64)
    private val store = PreferencesAuthApprovalStore(accountPubKeyHex)

    @AfterTest
    fun cleanup() =
        runTest {
            store.clear()
        }

    @Test
    fun shortRelayUrlRoundTrips() =
        runTest {
            val relay = NormalizedRelayUrl("wss://relay.example/")
            store.setScope(relay, AuthApprovalScope.ALWAYS)
            assertEquals(AuthApprovalScope.ALWAYS, store.getScope(relay))
        }

    @Test
    fun overLongRelayUrlPersistsInsteadOfThrowing() =
        runTest {
            // 102 chars — over java.util.prefs.Preferences.MAX_KEY_LENGTH (80).
            // Storing this raw threw "Key too long", the exception was swallowed
            // upstream, and the grant silently never persisted -> the AUTH banner
            // re-appeared on every challenge. Regression guard for that bug.
            val relay = NormalizedRelayUrl("wss://filter.nostr.wine/npub1max2lm5977tkj4zc28djq25g2muzmjgh2jqf83mq7vy539hfs7eqgec4et?broadcast=true")
            assertEquals(102, relay.url.length)

            store.setScope(relay, AuthApprovalScope.ALWAYS)
            assertEquals(AuthApprovalScope.ALWAYS, store.getScope(relay))
        }

    @Test
    fun overLongRelayUrlBlockedPersists() =
        runTest {
            val relay = NormalizedRelayUrl("wss://filter.nostr.wine/npub1max2lm5977tkj4zc28djq25g2muzmjgh2jqf83mq7vy539hfs7eqgec4et?broadcast=true")
            store.setScope(relay, AuthApprovalScope.BLOCKED)
            assertEquals(AuthApprovalScope.BLOCKED, store.getScope(relay))
        }

    @Test
    fun distinctOverLongRelayUrlsDoNotCollide() =
        runTest {
            val a = NormalizedRelayUrl("wss://filter.nostr.wine/npub1max2lm5977tkj4zc28djq25g2muzmjgh2jqf83mq7vy539hfs7eqgec4et?broadcast=true")
            val b = NormalizedRelayUrl("wss://filter.nostr.wine/npub1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq?broadcast=true")
            store.setScope(a, AuthApprovalScope.ALWAYS)
            store.setScope(b, AuthApprovalScope.BLOCKED)
            assertEquals(AuthApprovalScope.ALWAYS, store.getScope(a))
            assertEquals(AuthApprovalScope.BLOCKED, store.getScope(b))
        }

    @Test
    fun onceIsNeverPersisted() =
        runTest {
            val relay = NormalizedRelayUrl("wss://relay.example/")
            store.setScope(relay, AuthApprovalScope.ONCE)
            assertNull(store.getScope(relay))
        }
}
