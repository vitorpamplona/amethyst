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
package com.vitorpamplona.quartz.nip56Reports

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip56Reports.tags.ReportedAddressTag
import com.vitorpamplona.quartz.nip56Reports.tags.ReportedAuthorTag
import com.vitorpamplona.quartz.nip56Reports.tags.ReportedEventTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportHintProviderTest {
    private val pubkey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val other = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val eventId = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val addressId = "30402:$other:7e5fec50-9add-48c6-985c-eb593e6c14cd"
    private val relay = "wss://relay.damus.io/"

    private fun report(vararg tags: Array<String>) =
        ReportEvent(
            id = "00".repeat(32),
            pubKey = pubkey,
            createdAt = 1700000000,
            tags = arrayOf(*tags),
            content = "",
            sig = "00".repeat(64),
        )

    // ---------------------------------------------------------------
    // Layout disambiguation: legacy [name, id, type] vs modern
    // [name, id, relay, type]. See ReportTagLayout.
    // ---------------------------------------------------------------

    @Test
    fun legacyLayoutReadsTypeFromSlotTwo() {
        val tag = ReportedAuthorTag.parse(arrayOf("p", other, "nudity"))!!
        assertEquals(other, tag.pubKey)
        assertEquals(ReportType.NUDITY, tag.type)
        assertNull(tag.relayHint)
    }

    @Test
    fun modernLayoutReadsHintFromTwoAndTypeFromThree() {
        val tag = ReportedAuthorTag.parse(arrayOf("p", other, relay, "impersonation"))!!
        assertEquals(other, tag.pubKey)
        assertEquals(ReportType.IMPERSONATION, tag.type)
        assertEquals(relay, tag.relayHint?.url)
    }

    /**
     * Regression: a relay URL used to be fed to ReportType.parseOrNull,
     * which maps anything unrecognized to OTHER — so a hint-carrying tag
     * with no per-tag type silently became an `OTHER` report and masked
     * the event-level default.
     */
    @Test
    fun relayHintWithoutTypeFallsBackToDefaultNotOther() {
        val tag = ReportedAuthorTag.parse(arrayOf("p", other, relay), ReportType.SPAM)!!
        assertEquals(relay, tag.relayHint?.url)
        assertEquals(ReportType.SPAM, tag.type)
    }

    @Test
    fun emptyHintSlotStillReadsTypeFromSlotThree() {
        val tag = ReportedAuthorTag.parse(arrayOf("p", other, "", "nudity"))!!
        assertEquals(ReportType.NUDITY, tag.type)
        assertNull(tag.relayHint)
    }

    @Test
    fun blankTypeSlotFallsBackToDefault() {
        val tag = ReportedAuthorTag.parse(arrayOf("p", other, ""), ReportType.MALWARE)!!
        assertEquals(ReportType.MALWARE, tag.type)
    }

    @Test
    fun eventAndAddressTagsShareTheSameLayoutRules() {
        val e = ReportedEventTag.parse(arrayOf("e", eventId, relay, "illegal"))!!
        assertEquals(eventId, e.eventId)
        assertEquals(relay, e.relay?.url)
        assertEquals(ReportType.ILLEGAL, e.type)
        assertNull(e.author)

        val a = ReportedAddressTag.parse(arrayOf("a", addressId, relay, "spam"))!!
        assertEquals(addressId, a.address.toValue())
        assertEquals(relay, a.relay?.url)
        assertEquals(ReportType.SPAM, a.type)
    }

    // ---------------------------------------------------------------
    // Round trip
    // ---------------------------------------------------------------

    @Test
    fun assembleRoundTripsBothLayouts() {
        val legacy = ReportedAuthorTag.assemble(other, ReportType.SPAM)
        assertEquals(listOf("p", other, "spam"), legacy.toList())
        assertEquals(ReportType.SPAM, ReportedAuthorTag.parse(legacy)!!.type)

        val modern = ReportedAuthorTag(other, RelayUrlNormalizer.normalizeOrNull(relay), ReportType.SPAM).toTagArray()
        assertEquals(listOf("p", other, relay, "spam"), modern.toList())

        val reparsed = ReportedAuthorTag.parse(modern)!!
        assertEquals(ReportType.SPAM, reparsed.type)
        assertEquals(relay, reparsed.relayHint?.url)
    }

    // ---------------------------------------------------------------
    // Hint providers
    // ---------------------------------------------------------------

    @Test
    fun reportEventExposesEveryPointerItCarries() {
        val event =
            report(
                arrayOf("p", other, relay, "impersonation"),
                arrayOf("e", eventId, "nudity"),
                arrayOf("a", addressId, relay, "spam"),
            )

        assertEquals(listOf(other), event.linkedPubKeys())
        assertEquals(listOf(eventId), event.linkedEventIds())
        assertEquals(listOf(addressId), event.linkedAddressIds())

        // hints only where a relay is actually present
        assertEquals(listOf(other), event.pubKeyHints().map { it.pubkey })
        assertEquals(listOf(relay), event.pubKeyHints().map { it.relay.url })
        assertTrue(event.eventHints().isEmpty())
        assertEquals(listOf(addressId), event.addressHints().map { it.addressId })
    }

    @Test
    fun linkedPubKeysMatchesTypedAccessorsSoTheGraphCannotDrift() {
        val event =
            report(
                arrayOf("p", other, "nudity"),
                arrayOf("p", pubkey, relay, "spam"),
                arrayOf("e", eventId, "nudity"),
            )

        // The completeness invariant: every pubkey the typed accessor
        // reports is also reported by the generic hint provider.
        assertEquals(
            event.reportedAuthor().map { it.pubKey }.toSet(),
            event.linkedPubKeys().toSet(),
        )
        assertEquals(
            event.reportedPost().map { it.eventId }.toSet(),
            event.linkedEventIds().toSet(),
        )
    }

    @Test
    fun malformedPointersAreDropped() {
        val event =
            report(
                arrayOf("p", "tooshort", "nudity"),
                arrayOf("p"),
                arrayOf("e", eventId),
                arrayOf("a", "not-an-address"),
            )

        assertTrue(event.linkedPubKeys().isEmpty())
        assertEquals(listOf(eventId), event.linkedEventIds())
        assertTrue(event.linkedAddressIds().isEmpty())
    }
}
