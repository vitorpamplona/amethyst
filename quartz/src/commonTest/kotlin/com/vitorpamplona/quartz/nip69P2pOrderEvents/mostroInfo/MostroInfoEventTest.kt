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
package com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo

import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip50Search.IndexableFields
import com.vitorpamplona.quartz.nip50Search.SearchFieldExtractor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.P2PKindFixtures
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.BondApplyToTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.EscrowModeTag
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MostroInfoEventTest {
    private fun parse(json: String): Event = Event.fromJson(json)

    private fun build(vararg tags: Array<String>): Event = EventFactory.create("0".repeat(64), "1".repeat(64), 1L, MostroInfoEvent.KIND, arrayOf(*tags), "", "")

    @Test
    fun kindIsKnownAndProbesAsTheSearchableInfo() {
        assertTrue(EventFactory.isKnownKind(MostroInfoEvent.KIND))
        assertIs<MostroInfoEvent>(EventFactory.probe(MostroInfoEvent.KIND))
    }

    @Test
    fun lightningInstanceAccessors() {
        val info = assertIs<MostroInfoEvent>(parse(P2PKindFixtures.REAL_MOSTRO_INFO_LIGHTNING))
        assertEquals("000018b160c81819d864aa994003b29cdacd6027def22bb5c5002c6a68a3df4b", info.instancePubKey())
        assertEquals("mostro", info.platform())
        assertEquals("CharroNegro", info.instanceName())
        assertEquals("0.18.5", info.mostroVersion())
        assertEquals("d49064fd4afe32d327d432b88b7a5c2d03606410", info.mostroCommitHash())
        assertEquals(800000L, info.maxOrderAmount())
        assertEquals(100L, info.minOrderAmount())
        assertEquals(24L, info.expirationHours())
        assertEquals(900L, info.expirationSeconds())
        assertEquals(listOf("USD", "EUR", "MXN", "CUP"), info.fiatCurrenciesAccepted())
        assertEquals(10L, info.maxOrdersPerResponse())
        assertEquals(0.005, info.fee())
        assertEquals(0L, info.pow())
        assertEquals(0L, info.powFirstContact())
        assertEquals(2L, info.protocolVersion())
        assertEquals(300L, info.holdInvoiceExpirationWindow())
        assertEquals(144L, info.holdInvoiceCltvDelta())
        assertEquals(300L, info.invoiceExpirationWindow())
        assertEquals("0.20.3-beta commit=v0.20.3-beta", info.lndVersion())
        assertEquals("03309907d8d965d930db0aaad834360ce5f9b10a74dac52ea132ea871f809c3c81", info.lndNodePubKey())
        assertEquals("05407c84eed9035452c73f4f025efa9f4041ae96", info.lndCommitHash())
        assertEquals("CharroNegro", info.lndNodeAlias())
        assertEquals(listOf("bitcoin"), info.lndChains())
        assertEquals(listOf("mainnet"), info.lndNetworks())
        assertEquals(1, info.lndUris()?.size)
        assertEquals(true, info.bondEnabled())
        assertEquals(0.1, info.bondAmountPct())
        assertEquals(1000L, info.bondBaseAmountSats())
        assertEquals(BondApplyToTag.MAKE, info.bondApplyTo())
        assertEquals(false, info.bondSlashOnWaitingTimeout())
        assertEquals(0.5, info.bondSlashNodeSharePct())
        assertEquals(15L, info.bondPayoutClaimWindowDays())

        // Daemons that predate a tag: spec-defined defaults.
        assertNull(info.escrowMode())
        assertEquals(EscrowModeTag.LIGHTNING, info.escrowModeOrDefault())
        assertNull(info.maintenanceMode())
        assertFalse(info.isInMaintenance())
        assertNull(info.serbero())
        assertNull(info.cashuMintUrls())
        assertNull(info.reputationImportIssuers())
        assertEquals(emptyList(), info.linkedPubKeys())
    }

    @Test
    fun cashuInstanceAccessorsAndEdges() {
        val info = assertIs<MostroInfoEvent>(parse(P2PKindFixtures.REAL_MOSTRO_INFO_CASHU))
        assertEquals("Mostro local", info.instanceName())
        assertEquals(EscrowModeTag.CASHU, info.escrowMode())
        assertEquals(listOf("https://mint.cubabitcoin.org"), info.cashuMintUrls())
        assertEquals(15L, info.cashuEscrowLocktimeDays())
        assertEquals(false, info.bondEnabled())
        assertEquals(false, info.maintenanceMode())
        assertNull(info.lndNodePubKey())
        assertEquals("000005ee0a1a2b3033d2908bb2fc7e29de803d5ac55a249cdebc11d50fca7fc0", info.serbero())

        // SOLVER; never the instance's own `d`, never the Lightning node key.
        assertEquals(listOf("000005ee0a1a2b3033d2908bb2fc7e29de803d5ac55a249cdebc11d50fca7fc0"), info.linkedPubKeys())
        assertEquals(emptyList(), info.pubKeyHints())
    }

    @Test
    fun reputationIssuersAreEdges() {
        val issuer = "b".repeat(64)
        val imported = "c".repeat(64)
        val info =
            assertIs<MostroInfoEvent>(
                build(
                    arrayOf("z", "info"),
                    arrayOf("reputation_issuer", issuer),
                    arrayOf("reputation_import_issuers", imported, "not-a-key"),
                    arrayOf("serbero", "short"),
                ),
            )
        assertEquals(issuer, info.reputationIssuer())
        assertEquals(listOf(imported), info.reputationImportIssuers())
        assertNull(info.serbero())
        assertEquals(listOf(issuer, imported), info.linkedPubKeys())

        // Import on with an empty trust list: present but empty, unlike absent.
        val empty = assertIs<MostroInfoEvent>(build(arrayOf("z", "info"), arrayOf("reputation_import_issuers")))
        assertEquals(emptyList(), empty.reputationImportIssuers())
    }

    @Test
    fun emptyCurrencyListMeansEveryCurrency() {
        val info = assertIs<MostroInfoEvent>(build(arrayOf("z", "info"), arrayOf("fiat_currencies_accepted", "")))
        assertEquals(emptyList(), info.fiatCurrenciesAccepted())
        assertNull(assertIs<MostroInfoEvent>(build(arrayOf("z", "info"))).fiatCurrenciesAccepted())
    }

    @Test
    fun otherAppsOn38385AreNotMostroInfo() {
        listOf(P2PKindFixtures.REAL_BONDTRADE_ASSIGNMENT, P2PKindFixtures.SYNTHETIC_PAYGRESS_REVOCATION).forEach {
            val event = parse(it)
            assertIs<UnrecognizedKind38385Event>(event)
            assertIs<AddressableEvent>(event)
            assertFalse(event is SearchableEvent)
        }
    }

    @Test
    fun discriminatorNeedsTheInfoZOrAMostroVersion() {
        assertIs<MostroInfoEvent>(build(arrayOf("z", "info")))
        assertIs<MostroInfoEvent>(build(arrayOf("mostro_version", "0.12.8")))
        assertEquals(UnrecognizedKind38385Event::class, build(arrayOf("z", "assignment"))::class)
        assertEquals(UnrecognizedKind38385Event::class, build(arrayOf("mostro_version", ""))::class)
        assertEquals(UnrecognizedKind38385Event::class, build()::class)
    }

    @Test
    fun malformedTagsReadAsNull() {
        val info =
            assertIs<MostroInfoEvent>(
                build(
                    arrayOf("d", "not-hex"),
                    arrayOf("z", "info"),
                    arrayOf("y", "mostro", ""),
                    arrayOf("fee", "free"),
                    arrayOf("fee", "Infinity"),
                    arrayOf("max_order_amount", "1e6"),
                    arrayOf("maintenance_mode", "yes"),
                    arrayOf("protocol_version"),
                    arrayOf("cashu_mint_url", " "),
                ),
            )
        assertNull(info.instancePubKey())
        assertNull(info.instanceName())
        assertNull(info.fee())
        assertNull(info.maxOrderAmount())
        assertNull(info.maintenanceMode())
        assertNull(info.protocolVersion())
        assertNull(info.cashuMintUrls())
        assertEquals("", info.indexableContent())
    }

    @Test
    fun indexesNameAndCurrenciesOnBothPaths() {
        val info = assertIs<MostroInfoEvent>(parse(P2PKindFixtures.REAL_MOSTRO_INFO_LIGHTNING))
        assertEquals("CharroNegro USD EUR MXN CUP", info.indexableContent())

        val fields = mutableListOf<String>()
        info.forEachIndexableField {
            if (it != null) fields.add(it)
            true
        }
        assertEquals(info.indexableContent(), fields.joinToString(info.indexableSeparator()))

        val tiered = assertIs<IndexableFields.Tiered>(SearchFieldExtractor.extract(info))
        assertEquals(listOf("CharroNegro"), tiered.primary)
        assertEquals(listOf("USD", "EUR", "MXN", "CUP"), tiered.secondary)
    }

    @Test
    fun buildRoundTrips() {
        val me = "d".repeat(64)
        val template =
            MostroInfoEvent.build(
                mostroPubKey = me,
                mostroVersion = "0.19.2",
                fee = 0.006,
                minOrderAmount = 100,
                maxOrderAmount = 1_000_000,
                fiatCurrenciesAccepted = listOf("USD", "EUR"),
                protocolVersion = 2,
                instanceName = "Test Instance",
                createdAt = 1791429549L,
            ) {
                maintenanceMode(true)
                escrowMode(EscrowModeTag.CASHU)
                cashuMintUrls(listOf("https://mint.example.com", "https://mint2.example.com"))
                bondEnabled(false)
                serbero("e".repeat(64))
            }
        val info = assertIs<MostroInfoEvent>(EventFactory.create<Event>("0".repeat(64), me, template.createdAt, template.kind, template.tags, template.content, ""))
        assertEquals(me, info.instancePubKey())
        assertEquals("0.19.2", info.mostroVersion())
        assertEquals(0.006, info.fee())
        assertEquals(100L, info.minOrderAmount())
        assertEquals(1_000_000L, info.maxOrderAmount())
        assertEquals(listOf("USD", "EUR"), info.fiatCurrenciesAccepted())
        assertEquals(2L, info.protocolVersion())
        assertEquals("Test Instance", info.instanceName())
        assertTrue(info.isInMaintenance())
        assertEquals(EscrowModeTag.CASHU, info.escrowMode())
        assertEquals(listOf("https://mint.example.com", "https://mint2.example.com"), info.cashuMintUrls())
        assertEquals(false, info.bondEnabled())
        assertEquals(listOf("e".repeat(64)), info.linkedPubKeys())
        assertEquals("info", template.tags.first { it[0] == "z" }[1])
    }
}
