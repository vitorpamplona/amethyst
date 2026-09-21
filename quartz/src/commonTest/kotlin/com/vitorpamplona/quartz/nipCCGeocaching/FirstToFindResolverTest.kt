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
package com.vitorpamplona.quartz.nipCCGeocaching

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nipCCGeocaching.firstToFind.FirstToFindResolver
import com.vitorpamplona.quartz.nipCCGeocaching.foundLog.GeocacheFoundLogEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import com.vitorpamplona.quartz.nipCCGeocaching.verification.GeocacheVerificationEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The first-to-find claim rules.
 *
 * The interesting case is the forged timestamp: `created_at` is author-supplied, so the
 * provisional ordering can be stolen by anyone willing to backdate a log. The `F` tag is what
 * takes it back, and these tests pin that it actually does.
 */
class FirstToFindResolverTest {
    private val owner = "0461fcbecc4c3374439932d6b8f11269ccdb7cc973ad7a50ae362db135a474dd"
    private val cacheKey = NostrSignerInternal(KeyPair())
    private val winner = NostrSignerInternal(KeyPair())
    private val latecomer = NostrSignerInternal(KeyPair())

    private val cacheAddress = Address(GeocacheListingEvent.KIND, owner, "linocut-aftermath")

    private fun listing(
        firstToFind: Boolean = true,
        lockedInWinner: String? = null,
        withVerificationKey: Boolean = true,
    ) = GeocacheListingEvent(
        "id",
        owner,
        1_748_619_568L,
        buildList {
            add(arrayOf("d", cacheAddress.dTag))
            add(arrayOf("name", "Aftermath"))
            add(arrayOf("g", "u4xsu6ryb"))
            add(arrayOf("D", "2"))
            add(arrayOf("T", "2"))
            add(arrayOf("S", "small"))
            if (firstToFind) add(arrayOf("n", "first-to-find"))
            if (withVerificationKey) add(arrayOf("verification", cacheKey.pubKey))
            lockedInWinner?.let { add(arrayOf("F", it)) }
        }.toTypedArray(),
        "",
        "sig",
    )

    private suspend fun verifiedLog(
        finder: NostrSignerInternal,
        createdAt: Long,
        id: String = createdAt.toString().padStart(64, '0'),
    ): GeocacheFoundLogEvent {
        val verification = cacheKey.sign(GeocacheVerificationEvent.build(finder.pubKey, cacheAddress, createdAt = createdAt))
        return GeocacheFoundLogEvent(
            id,
            finder.pubKey,
            createdAt,
            arrayOf(arrayOf("a", cacheAddress.toValue()), arrayOf("verification", verification.toJson())),
            "Found it!",
            "sig",
        )
    }

    private fun unverifiedLog(
        finder: NostrSignerInternal,
        createdAt: Long,
        id: String = createdAt.toString().padStart(64, '0'),
    ) = GeocacheFoundLogEvent(
        id,
        finder.pubKey,
        createdAt,
        arrayOf(arrayOf("a", cacheAddress.toValue())),
        "Found it!",
        "sig",
    )

    @Test
    fun theEarliestVerifiedLogHoldsTheProvisionalClaim() =
        runTest {
            val first = verifiedLog(winner, 1_000L)
            val second = verifiedLog(latecomer, 2_000L)
            val cache = listing()

            assertEquals(first.id, FirstToFindResolver.provisionalWinningLog(cache, listOf(second, first))?.id)
            assertEquals(winner.pubKey, FirstToFindResolver.winnerPubKey(cache, listOf(second, first)))
            assertTrue(FirstToFindResolver.isClaimed(cache, listOf(second, first)))
        }

    @Test
    fun tiesOnCreatedAtBreakOnAscendingEventId() =
        runTest {
            val a = verifiedLog(winner, 1_000L, id = "a".repeat(64))
            val b = verifiedLog(latecomer, 1_000L, id = "b".repeat(64))

            assertEquals(a.id, FirstToFindResolver.provisionalWinningLog(listing(), listOf(b, a))?.id)
        }

    @Test
    fun anUnverifiedLogIsNotAClaimHoweverEarlyItIs() =
        runTest {
            // "I got here first" without a 7517 is somebody's word. The exclusive claim is
            // reserved for logs that prove physical presence.
            val wordOfMouth = unverifiedLog(latecomer, 1L)
            val real = verifiedLog(winner, 9_000L)

            assertEquals(real.id, FirstToFindResolver.provisionalWinningLog(listing(), listOf(wordOfMouth, real))?.id)
        }

    @Test
    fun aLogAboutAnotherCacheIsNotACandidate() =
        runTest {
            val elsewhere =
                GeocacheFoundLogEvent(
                    "e".repeat(64),
                    latecomer.pubKey,
                    1L,
                    arrayOf(arrayOf("a", Address(GeocacheListingEvent.KIND, owner, "some-other-cache").toValue())),
                    "Found it!",
                    "sig",
                )
            val real = verifiedLog(winner, 9_000L)

            assertEquals(listOf(real.id), FirstToFindResolver.verifiedLogs(listing(), listOf(elsewhere, real)).map { it.id })
        }

    @Test
    fun aForgedEarlierTimestampStealsTheProvisionalClaim() =
        runTest {
            // Not a bug to fix here — this is exactly the weakness NIP-CC calls out, and the
            // reason the owner gets to lock a winner in. Pinned so the next test means something.
            val real = verifiedLog(winner, 5_000L)
            val backdated = verifiedLog(latecomer, 1L)

            assertEquals(latecomer.pubKey, FirstToFindResolver.winnerPubKey(listing(), listOf(real, backdated)))
        }

    @Test
    fun theFTagBeatsAnyTimestamp() =
        runTest {
            // "clients MUST attribute the exclusive claim to the pubkey in the `F` tag,
            // regardless of which verified found log currently appears earliest."
            val real = verifiedLog(winner, 5_000L)
            val backdated = verifiedLog(latecomer, 1L)
            val cache = listing(lockedInWinner = winner.pubKey)

            assertEquals(winner.pubKey, FirstToFindResolver.winnerPubKey(cache, listOf(real, backdated)))
            assertEquals(real.id, FirstToFindResolver.winningLog(cache, listOf(real, backdated))?.id)
            assertTrue(FirstToFindResolver.isLockedIn(cache))
        }

    @Test
    fun theLockedInWinnerHoldsEvenWithNoLogFetchedForThem() =
        runTest {
            // The owner has confirmed the claim; the winning log may simply not be in hand yet.
            val cache = listing(lockedInWinner = winner.pubKey)

            assertEquals(winner.pubKey, FirstToFindResolver.winnerPubKey(cache, emptyList()))
            assertNull(FirstToFindResolver.winningLog(cache, emptyList()))
            assertTrue(FirstToFindResolver.isClaimed(cache, emptyList()))
        }

    @Test
    fun theWinningLogIsTheWinnersEarliestVerifiedOne() =
        runTest {
            val early = verifiedLog(winner, 5_000L, id = "1".repeat(64))
            val later = verifiedLog(winner, 6_000L, id = "2".repeat(64))
            val cache = listing(lockedInWinner = winner.pubKey)

            assertEquals(early.id, FirstToFindResolver.winningLog(cache, listOf(later, early))?.id)
        }

    @Test
    fun aCacheThatIsNotFirstToFindHasNoExclusiveClaimToAward() =
        runTest {
            // Later verified logs stay valid records of presence on any cache; they are only
            // *claims* where the modifier says the cache is single-claim.
            val logs = listOf(verifiedLog(winner, 1_000L))
            val cache = listing(firstToFind = false, lockedInWinner = winner.pubKey)

            assertNull(FirstToFindResolver.winnerPubKey(cache, logs))
            assertNull(FirstToFindResolver.winningLog(cache, logs))
            assertFalse(FirstToFindResolver.isClaimed(cache, logs))
        }

    @Test
    fun aFirstToFindCacheWithNoVerificationKeyCanNeverBeClaimed() =
        runTest {
            // NIP-CC: `first-to-find` "Requires a `verification` tag". With none, no log can be
            // verified, so no log is a claim.
            val logs = listOf(verifiedLog(winner, 1_000L))
            val cache = listing(withVerificationKey = false)

            assertNull(FirstToFindResolver.provisionalWinningLog(cache, logs))
            assertFalse(FirstToFindResolver.isClaimed(cache, logs))
        }

    @Test
    fun anUnclaimedFirstToFindCacheReportsNoWinner() {
        assertNull(FirstToFindResolver.winnerPubKey(listing(), emptyList()))
        assertFalse(FirstToFindResolver.isClaimed(listing(), emptyList()))
        assertFalse(FirstToFindResolver.isLockedIn(listing()))
    }
}
