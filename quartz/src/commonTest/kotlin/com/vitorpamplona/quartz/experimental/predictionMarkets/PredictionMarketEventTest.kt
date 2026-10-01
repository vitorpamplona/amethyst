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
package com.vitorpamplona.quartz.experimental.predictionMarkets

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.Kind38000Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PredictionMarketEventTest {
    private fun market(json: String) = assertIs<PredictionMarketEvent>(Event.fromJson(json))

    private fun market(
        tags: Array<Array<String>>,
        content: String = "",
    ) = PredictionMarketEvent("0".repeat(64), "1".repeat(64), 1L, tags, content, "")

    @Test
    fun currentShapeResolvedBinary() {
        val event = market(Kind38000Fixtures.MARKET_RESOLVED_BINARY)

        assertEquals("caec08eca65442725a5bd8f77e6d2666", event.marketId())
        assertEquals("Will an AI coding tool appear in GitHub top trending repos today? (UTC 2,May,2026)", event.title())
        assertTrue(event.description()!!.startsWith("At least one repository in GitHub's daily trending page"))
        assertEquals(listOf("YES", "NO"), event.outcomes())
        assertEquals("NO", event.resolution())
        assertEquals(PredictionMarketStatus.RESOLVED, event.status())
        assertEquals(1777680000L, event.endsAt())
        assertEquals("ai-llm", event.category())
        assertEquals("demo", event.network())
        assertTrue(event.isDemo())
        assertEquals("github.com/trending daily repos", event.resolutionSource())
        assertEquals(100L, event.minBetSats())
        assertEquals(100000L, event.maxBetSats())
        assertEquals(4.21, event.feePercent())
        assertNull(event.cancelReason())
        assertEquals("BAO Markets", event.client())
        assertEquals("2eaf781b3cabef3cbf17313bc43580761748651bc09273d50646651dd06c6349", event.oracle())
        assertEquals("${event.title()}\n${event.description()}", event.indexableContent())
    }

    @Test
    fun currentShapeActiveMarketClosesAtItsEnd() {
        val event = market(Kind38000Fixtures.MARKET_ACTIVE_BINARY)
        val end = 1777939200L

        assertEquals(end, event.endsAt())
        assertEquals("bitcoin", event.category())
        assertNull(event.resolution())
        // `status` says "active" (it wins over `state` = "ended")…
        assertEquals(PredictionMarketStatus.OPEN, event.status(now = end - 1))
        // …but once `end` passes, betting is over.
        assertEquals(PredictionMarketStatus.CLOSED, event.status(now = end))
        assertEquals(PredictionMarketStatus.CLOSED, event.status())
        assertEquals("https://mempool.space/api/v1/blocks", event.resolutionSource())
    }

    @Test
    fun currentShapeVoidedMultiOutcome() {
        val event = market(Kind38000Fixtures.MARKET_VOIDED_MULTI_OUTCOME)

        assertEquals(listOf("Foundry USA", "AntPool", "F2Pool", "ViaBTC", "Other"), event.outcomes())
        assertEquals(PredictionMarketStatus.CANCELLED, event.status(now = 0))
        assertEquals("mining", event.category())
        assertTrue(event.title()!!.startsWith("Which mining pool will mine the most Bitcoin blocks today?"))
    }

    @Test
    fun currentShapeCancelled() {
        val event = market(Kind38000Fixtures.MARKET_CANCELLED)

        assertEquals("Will BTC price be above \$60,000 at 2026-08-13 13:00 UTC?", event.title())
        assertNull(event.description())
        assertEquals(emptyList(), event.outcomes())
        assertEquals(PredictionMarketStatus.CANCELLED, event.status())
        assertEquals("bridge-test junk", event.cancelReason())
        assertEquals(1786626000L, event.endsAt())
        assertEquals("bitcoin", event.category())
        assertTrue(event.isDemo())
        assertEquals(event.title(), event.indexableContent())
    }

    @Test
    fun baoFundShape() {
        val event = market(Kind38000Fixtures.MARKET_BAO_FUND)
        val end = 1790727795L

        assertEquals("Will E2E demo-rail liquid refund munbn1yg deliver: E2E milestone munbn1yg by 2026-09-30?", event.title())
        assertTrue(event.description()!!.contains("CRITERIA: E2E milestone munbn1yg"))
        assertEquals(listOf("YES", "NO"), event.outcomes())
        assertEquals("bao-fund", event.category())
        assertTrue(event.isDemo())
        assertEquals(end, event.endsAt())
        // No status tag at all: the end decides.
        assertEquals(PredictionMarketStatus.OPEN, event.status(now = end - 100))
        assertEquals(PredictionMarketStatus.CLOSED, event.status(now = end + 1))
        assertNull(event.minBetSats())
        assertNull(event.feePercent())
    }

    @Test
    fun firstShape() {
        val event = market(Kind38000Fixtures.MARKET_FIRST_SHAPE)
        val end = 1769256240L

        assertEquals("will trump turn purple before May?", event.title())
        assertEquals("Trump is orange, but when purple? I think he hes been orange for far too long", event.description())
        // Outcome objects read by their label.
        assertEquals(listOf("Yes", "No"), event.outcomes())
        assertEquals("nostr", event.category())
        assertEquals("community", event.oracle())
        assertNull(event.network())
        assertFalse(event.isDemo())
        assertEquals(end, event.endsAt())
        // `state` = "funding" is open, until the end passes.
        assertEquals(PredictionMarketStatus.OPEN, event.status(now = end - 1))
        assertEquals(PredictionMarketStatus.CLOSED, event.status(now = end + 1))
    }

    @Test
    fun statusWords() {
        val expected =
            mapOf(
                "active" to PredictionMarketStatus.OPEN,
                "Open" to PredictionMarketStatus.OPEN,
                "funding" to PredictionMarketStatus.OPEN,
                "resolving" to PredictionMarketStatus.CLOSED,
                "ended" to PredictionMarketStatus.CLOSED,
                "closed" to PredictionMarketStatus.CLOSED,
                "resolved" to PredictionMarketStatus.RESOLVED,
                "settled" to PredictionMarketStatus.RESOLVED,
                "voided" to PredictionMarketStatus.CANCELLED,
                "cancelled" to PredictionMarketStatus.CANCELLED,
                " CANCELED " to PredictionMarketStatus.CANCELLED,
            )
        expected.forEach { (word, status) -> assertEquals(status, PredictionMarketStatus.parse(word), word) }
        assertNull(PredictionMarketStatus.parse("pending"))
        assertNull(PredictionMarketStatus.parse(""))
    }

    @Test
    fun statusFallbacks() {
        // No status at all, no end: unknown.
        assertNull(market(arrayOf(arrayOf("market", "m"))).status(now = 10))
        // An unknown word falls through to the next tag name.
        assertEquals(
            PredictionMarketStatus.RESOLVED,
            market(arrayOf(arrayOf("market", "m"), arrayOf("status", "weird"), arrayOf("state", "resolved"))).status(now = 10),
        )
        // A resolution resolves an undeclared or still-open market.
        assertEquals(PredictionMarketStatus.RESOLVED, market(arrayOf(arrayOf("market", "m"), arrayOf("resolution", "YES"))).status(now = 10))
        assertEquals(
            PredictionMarketStatus.RESOLVED,
            market(arrayOf(arrayOf("market", "m"), arrayOf("status", "active"), arrayOf("resolution", "YES"))).status(now = 10),
        )
        // …but never un-cancels one.
        assertEquals(
            PredictionMarketStatus.CANCELLED,
            market(arrayOf(arrayOf("market", "m"), arrayOf("status", "voided"), arrayOf("resolution", "YES"))).status(now = 10),
        )
        // A cancel reason on an undeclared market cancels it.
        assertEquals(PredictionMarketStatus.CANCELLED, market(arrayOf(arrayOf("market", "m"), arrayOf("cancel_reason", "oops"))).status(now = 10))
        // Undeclared: the end decides.
        assertEquals(PredictionMarketStatus.OPEN, market(arrayOf(arrayOf("market", "m"), arrayOf("end", "20"))).status(now = 10))
        assertEquals(PredictionMarketStatus.CLOSED, market(arrayOf(arrayOf("market", "m"), arrayOf("end", "20"))).status(now = 20))
        // A status in JSON content counts when no tag declares one.
        assertEquals(PredictionMarketStatus.CANCELLED, market(arrayOf(arrayOf("market", "m")), """{"status":"cancelled"}""").status(now = 10))
    }

    @Test
    fun tagsWinOverJson() {
        val event =
            market(
                arrayOf(
                    arrayOf("market", "m"),
                    arrayOf("title", "From tag"),
                    arrayOf("min_bet", "5"),
                    arrayOf("outcome", "A"),
                    arrayOf("outcome", "B"),
                    arrayOf("data", """{"title":"From data","minBetSats":7,"maxBetSats":9,"outcomes":["X","Y"]}"""),
                ),
                """{"title":"From content","description":"Content description"}""",
            )

        // `data` is BAO's canonical blob, so its title beats the tag; the tag beats JSON content.
        assertEquals("From data", event.title())
        assertEquals("Content description", event.description())
        assertEquals(5L, event.minBetSats())
        assertEquals(9L, event.maxBetSats())
        assertEquals(listOf("A", "B"), event.outcomes())
    }

    @Test
    fun junkJsonNeverThrows() {
        val junk =
            listOf(
                "",
                "   ",
                "not json at all",
                "{",
                "{\"title\":",
                "[1,2,3]",
                "null",
                "{\"a\":".repeat(5000),
                "{\"title\": 5, \"description\": null, \"outcomes\": \"nope\", \"minBetSats\": \"abc\", \"feePercent\": {}}",
                "{\"outcomes\": [1, null, [], {\"id\": 7}, {\"label\": \"A\"}, \"B\", \"B\", \" \"]}",
            )

        junk.forEach { json ->
            listOf(
                market(arrayOf(arrayOf("market", "m"), arrayOf("data", json)), json),
                market(arrayOf(arrayOf("type", "binary"), arrayOf("end", "not a number"), arrayOf("min_bet", "x"), arrayOf("fee_percent", "y")), json),
            ).forEach { event ->
                event.title()
                event.description()
                event.outcomes()
                event.status(now = 10)
                event.endsAt()
                event.minBetSats()
                event.maxBetSats()
                event.feePercent()
                event.cancelReason()
                event.indexableContent()
            }
        }

        val odd = market(arrayOf(arrayOf("market", "m")), junk.last())
        assertEquals(listOf("A", "B"), odd.outcomes())

        val typed = market(arrayOf(arrayOf("market", "m"), arrayOf("min_bet", "x")), junk[junk.size - 2])
        assertNull(typed.title())
        assertNull(typed.description())
        assertEquals(emptyList(), typed.outcomes())
        assertNull(typed.minBetSats())
        assertNull(typed.feePercent())
        assertEquals("", typed.indexableContent())
    }

    @Test
    fun visitorAgreesWithIndexedContentAndStops() {
        listOf(
            Kind38000Fixtures.MARKET_RESOLVED_BINARY,
            Kind38000Fixtures.MARKET_CANCELLED,
            Kind38000Fixtures.MARKET_FIRST_SHAPE,
        ).forEach { json ->
            val event = market(json)
            val visited = mutableListOf<String>()
            event.forEachIndexableField {
                if (it != null) visited.add(it)
                true
            }
            assertEquals(event.indexableContent(), visited.joinToString(event.indexableSeparator()))

            var seen = 0
            event.forEachIndexableField {
                seen++
                false
            }
            assertEquals(1, seen)
        }
    }
}
