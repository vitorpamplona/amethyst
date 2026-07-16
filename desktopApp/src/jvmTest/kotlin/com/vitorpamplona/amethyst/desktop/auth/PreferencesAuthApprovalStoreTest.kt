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

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Regression tests for the AUTH-banner-repeats bug.
 *
 * `PreferencesAuthApprovalStore` used the raw relay URL as the
 * `java.util.prefs.Preferences` key. Preferences caps keys at
 * [Preferences.MAX_KEY_LENGTH] (80 chars) and `put()` throws
 * `IllegalArgumentException` for anything longer. Outbox-proxy relay URLs that
 * embed an npub and a query string routinely exceed that, so the `ALWAYS` /
 * `BLOCKED` grant silently failed to persist and the banner re-appeared on
 * every AUTH challenge.
 *
 * These assert the key-derivation invariant directly (no OS Preferences I/O, so
 * they run deterministically on every platform): every key stays within the
 * cap, which is exactly the condition `Preferences.put` enforces.
 */
class PreferencesAuthApprovalStoreTest {
    // 102 chars — the outbox-proxy URL from the bug report, over the 80 cap.
    private val longUrl = "wss://filter.nostr.wine/npub1max2lm5977tkj4zc28djq25g2muzmjgh2jqf83mq7vy539hfs7eqgec4et?broadcast=true"

    @Test
    fun shortUrlStoredVerbatim() {
        val url = "wss://relay.example/"
        assertEquals(url, authApprovalPreferenceKey(NormalizedRelayUrl(url)))
    }

    @Test
    fun overLongUrlIsHashedUnderTheCap() {
        assertEquals(102, longUrl.length)
        val key = authApprovalPreferenceKey(NormalizedRelayUrl(longUrl))

        assertTrue(key.startsWith("sha256:"), "over-long URLs must fold to a hashed key")
        assertTrue(
            key.length <= Preferences.MAX_KEY_LENGTH,
            "key length ${key.length} must stay within Preferences.MAX_KEY_LENGTH so put() never throws",
        )
    }

    @Test
    fun everyKeyStaysWithinThePreferencesCap() {
        // Boundary + well over: whatever the URL length, the derived key must be
        // storable (this is the invariant Preferences.put enforces).
        listOf(
            "wss://relay.example/",
            "wss://" + "a".repeat(Preferences.MAX_KEY_LENGTH), // 86 chars
            "wss://" + "a".repeat(500),
            longUrl,
        ).forEach { url ->
            val key = authApprovalPreferenceKey(NormalizedRelayUrl(url))
            assertTrue(
                key.length <= Preferences.MAX_KEY_LENGTH,
                "key for a ${url.length}-char URL was ${key.length} chars, over the ${Preferences.MAX_KEY_LENGTH} cap",
            )
        }
    }

    @Test
    fun distinctOverLongUrlsProduceDistinctKeys() {
        val a = "wss://filter.nostr.wine/npub1max2lm5977tkj4zc28djq25g2muzmjgh2jqf83mq7vy539hfs7eqgec4et?broadcast=true"
        val b = "wss://filter.nostr.wine/npub1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq?broadcast=true"
        assertNotEquals(
            authApprovalPreferenceKey(NormalizedRelayUrl(a)),
            authApprovalPreferenceKey(NormalizedRelayUrl(b)),
        )
    }
}
