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
package com.vitorpamplona.quartz.utils

import com.vitorpamplona.quartz.experimental.ballots.BallotEvent
import com.vitorpamplona.quartz.experimental.predictionMarkets.PredictionMarketEvent
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip87Ecash.cashu.CashuMintEvent
import com.vitorpamplona.quartz.nip87Ecash.fedimint.FedimintEvent
import com.vitorpamplona.quartz.nip87Ecash.recommendation.MintRecommendationEvent
import com.vitorpamplona.quartz.nip87Ecash.recommendation.UnrecognizedKind38000Event
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertIsNot
import kotlin.test.assertTrue

/**
 * Kind 38000 is NIP-87's mint recommendation, but prediction markets, ballots and spam share the
 * number. The factory must hand each shape its own class, and junk none.
 */
class EventFactoryKind38000Test {
    private fun parse(json: String): Event = Event.fromJson(json)

    private fun build(vararg tags: Array<String>): Event = EventFactory.create("0".repeat(64), "1".repeat(64), 1L, 38000, arrayOf(*tags), "", "")

    @Test
    fun fixturesAreRealSignedEvents() {
        listOf(
            Kind38000Fixtures.MARKET_RESOLVED_BINARY,
            Kind38000Fixtures.MARKET_ACTIVE_BINARY,
            Kind38000Fixtures.MARKET_VOIDED_MULTI_OUTCOME,
            Kind38000Fixtures.MARKET_CANCELLED,
            Kind38000Fixtures.MARKET_BAO_FUND,
            Kind38000Fixtures.MARKET_FIRST_SHAPE,
            Kind38000Fixtures.BALLOT_RESPONSES,
            Kind38000Fixtures.BALLOT_RESPONSES_PROOF_HASH,
            Kind38000Fixtures.BALLOT_OBJECT,
            Kind38000Fixtures.BALLOT_VOTE_CHOICE,
            Kind38000Fixtures.MINT_REVIEW_CASHU,
            Kind38000Fixtures.MINT_REVIEW_FEDIMINT,
            Kind38000Fixtures.MINT_REVIEW_NO_K,
            Kind38000Fixtures.SPAM_SYBIL_VOTE,
            Kind38000Fixtures.SPAM_APP_MANIFEST,
        ).forEach { json ->
            val event = parse(json)
            assertEquals(38000, event.kind)
            assertTrue(event.verify(), "fixture ${event.id} does not verify")
        }
    }

    @Test
    fun mintReviewsAreMintRecommendations() {
        val cashu = assertIs<MintRecommendationEvent>(parse(Kind38000Fixtures.MINT_REVIEW_CASHU))
        assertTrue(cashu.isCashuRecommendation())
        assertEquals(listOf("https://21mint.me"), cashu.mintUrls())

        val fedimint = assertIs<MintRecommendationEvent>(parse(Kind38000Fixtures.MINT_REVIEW_FEDIMINT))
        assertTrue(fedimint.isFedimintRecommendation())

        val noK = assertIs<MintRecommendationEvent>(parse(Kind38000Fixtures.MINT_REVIEW_NO_K))
        assertEquals(listOf("https://mint.lnpay.cz"), noK.mintUrls())
    }

    @Test
    fun marketsArePredictionMarkets() {
        listOf(
            Kind38000Fixtures.MARKET_RESOLVED_BINARY,
            Kind38000Fixtures.MARKET_ACTIVE_BINARY,
            Kind38000Fixtures.MARKET_VOIDED_MULTI_OUTCOME,
            Kind38000Fixtures.MARKET_CANCELLED,
            Kind38000Fixtures.MARKET_BAO_FUND,
            Kind38000Fixtures.MARKET_FIRST_SHAPE,
        ).forEach { assertIs<PredictionMarketEvent>(parse(it)) }
    }

    @Test
    fun ballotsAreBallots() {
        listOf(
            Kind38000Fixtures.BALLOT_RESPONSES,
            Kind38000Fixtures.BALLOT_RESPONSES_PROOF_HASH,
            Kind38000Fixtures.BALLOT_OBJECT,
            Kind38000Fixtures.BALLOT_VOTE_CHOICE,
        ).forEach { assertIs<BallotEvent>(parse(it)) }
    }

    @Test
    fun spamAndOneOffsAreUnrecognizedButStillAddressable() {
        listOf(
            Kind38000Fixtures.SPAM_SYBIL_VOTE,
            Kind38000Fixtures.SPAM_APP_MANIFEST,
        ).forEach {
            val event = parse(it)
            assertIs<UnrecognizedKind38000Event>(event)
            // Stores key 30000–39999 by `d` only when the event is addressable: replacement and
            // `a`-tag deletion must still reach the spam.
            assertIs<AddressableEvent>(event)
            assertIsNot<SearchableEvent>(event)
        }
    }

    @Test
    fun aKNamingOnlyOtherKindsIsNotARecommendationEvenWithAMintUrl() {
        assertIs<UnrecognizedKind38000Event>(build(arrayOf("k", "1"), arrayOf("u", "https://mint.example")))
        // Any k naming a mint kind counts, wherever it sits.
        assertIs<MintRecommendationEvent>(build(arrayOf("k", "1"), arrayOf("k", "38172")))
        assertIs<MintRecommendationEvent>(build(arrayOf("k", "38173"), arrayOf("k", "1")))
    }

    @Test
    fun aRecommendationNamingItsMintOnlyByAddressIsOne() {
        assertIs<MintRecommendationEvent>(build(arrayOf("d", "x"), arrayOf("a", "38172:${"a".repeat(64)}:x")))
        assertIs<MintRecommendationEvent>(build(arrayOf("d", "x"), arrayOf("a", "38173:${"a".repeat(64)}:x")))
        assertIs<UnrecognizedKind38000Event>(build(arrayOf("d", "x"), arrayOf("a", "30023:${"a".repeat(64)}:x")))
    }

    @Test
    fun aBlankMintUrlWithoutKIsNotARecommendation() {
        assertEquals(UnrecognizedKind38000Event::class, build(arrayOf("d", "x"), arrayOf("u", " "))::class)
        assertEquals(UnrecognizedKind38000Event::class, build(arrayOf("u"))::class)
    }

    @Test
    fun recommendationWinsOverTheOtherShapes() {
        assertIs<MintRecommendationEvent>(build(arrayOf("k", "38172"), arrayOf("election", "e1"), arrayOf("market", "m1")))
    }

    @Test
    fun ballotWinsOverMarket() {
        assertIs<BallotEvent>(build(arrayOf("election", "e1"), arrayOf("market", "m1")))
    }

    @Test
    fun aBlankElectionIsNotABallot() {
        assertEquals(UnrecognizedKind38000Event::class, build(arrayOf("election", ""))::class)
    }

    @Test
    fun marketNeedsAMarketTwoOutcomesOrATypeAndAnEnd() {
        assertIs<PredictionMarketEvent>(build(arrayOf("market", "m1")))
        assertIs<PredictionMarketEvent>(build(arrayOf("outcome", "YES"), arrayOf("outcome", "NO")))
        assertIs<PredictionMarketEvent>(build(arrayOf("type", "binary"), arrayOf("end", "1")))

        assertEquals(UnrecognizedKind38000Event::class, build(arrayOf("market", " "))::class)
        assertEquals(UnrecognizedKind38000Event::class, build(arrayOf("outcome", "YES"))::class)
        assertEquals(UnrecognizedKind38000Event::class, build(arrayOf("type", "binary"))::class)
        assertEquals(UnrecognizedKind38000Event::class, build(arrayOf("end", "1"))::class)
    }

    @Test
    fun signingARecommendationRoundTripsToARecommendation() {
        val signer = NostrSignerSync()
        listOf(CashuMintEvent.KIND, FedimintEvent.KIND).forEach { mintKind ->
            val signed =
                signer.sign<MintRecommendationEvent>(
                    MintRecommendationEvent.build(
                        mintIdentifier = "https://mint.example",
                        mintKind = mintKind,
                        review = "great",
                    ),
                )
            assertIs<MintRecommendationEvent>(signed)
            assertEquals(mintKind, signed.mintEventKind())
            assertIs<MintRecommendationEvent>(Event.fromJson(signed.toJson()))
        }
    }

    @Test
    fun theKindStillProbesAsAKnownSearchableKind() {
        assertTrue(EventFactory.isKnownKind(MintRecommendationEvent.KIND))
        assertIs<MintRecommendationEvent>(EventFactory.probe(MintRecommendationEvent.KIND))
        assertTrue(EventFactory.probe(MintRecommendationEvent.KIND) is SearchableEvent)
    }
}
