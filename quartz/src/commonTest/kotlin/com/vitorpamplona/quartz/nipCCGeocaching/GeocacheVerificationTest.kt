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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nipCCGeocaching.foundLog.GeocacheFoundLogEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import com.vitorpamplona.quartz.nipCCGeocaching.verification.GeocacheVerificationEvent
import com.vitorpamplona.quartz.nipCCGeocaching.verification.GeocacheVerificationValidator
import com.vitorpamplona.quartz.nipCCGeocaching.verification.VerificationFailure
import com.vitorpamplona.quartz.nipCCGeocaching.verification.tags.FinderCacheTag
import com.vitorpamplona.quartz.utils.EventFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Kind 7517 and the validation NIP-CC puts around it.
 *
 * Each negative case here is a replay or a forgery that a missing check would let through, so
 * they are written as the attack rather than as "returns null".
 */
class GeocacheVerificationTest {
    private val owner = "0461fcbecc4c3374439932d6b8f11269ccdb7cc973ad7a50ae362db135a474dd"

    // The keypair that lives at the cache, behind the QR code.
    private val cacheKey = NostrSignerInternal(KeyPair())
    private val someoneElsesCacheKey = NostrSignerInternal(KeyPair())

    private val finder = NostrSignerInternal(KeyPair())
    private val otherFinder = NostrSignerInternal(KeyPair())

    private val cacheAddress = Address(GeocacheListingEvent.KIND, owner, "verified-treasure-1748619568669")
    private val otherCacheAddress = Address(GeocacheListingEvent.KIND, owner, "some-other-treasure")

    private fun listing(verificationKey: String? = cacheKey.pubKey) =
        GeocacheListingEvent(
            "id",
            owner,
            1_748_619_568L,
            buildList {
                add(arrayOf("d", cacheAddress.dTag))
                add(arrayOf("name", "Verified Treasure"))
                add(arrayOf("g", "u4xsu6ryb"))
                add(arrayOf("D", "3"))
                add(arrayOf("T", "2"))
                add(arrayOf("S", "small"))
                verificationKey?.let { add(arrayOf("verification", it)) }
            }.toTypedArray(),
            "",
            "sig",
        )

    private suspend fun verification(
        signer: NostrSignerInternal = cacheKey,
        forFinder: String = finder.pubKey,
        atCache: Address = cacheAddress,
    ) = signer.sign(GeocacheVerificationEvent.build(forFinder, atCache, createdAt = 1_748_619_600L))

    private fun foundLog(
        author: String,
        verification: GeocacheVerificationEvent?,
        cache: Address = cacheAddress,
        createdAt: Long = 1_748_619_700L,
        id: String = "1".repeat(64),
    ) = GeocacheFoundLogEvent(
        id,
        author,
        createdAt,
        buildList {
            add(arrayOf("a", cache.toValue()))
            verification?.let { add(arrayOf("verification", it.toJson())) }
        }.toTypedArray(),
        "Found it!",
        "sig",
    )

    @Test
    fun theKindsAreRegisteredWithTheEventFactory() {
        assertTrue(EventFactory.isKnownKind(GeocacheVerificationEvent.KIND))
        assertTrue(EventFactory.isKnownKind(GeocacheFoundLogEvent.KIND))
    }

    @Test
    fun theContentIsTheStaticStringTheSpecMandates() {
        val pubKey = "a".repeat(64)

        assertEquals("Geocache verification for ${NPub.create(pubKey)}", GeocacheVerificationEvent.contentFor(pubKey))
        assertTrue(GeocacheVerificationEvent.contentFor(pubKey).startsWith("Geocache verification for npub1"))
    }

    @Test
    fun theATagIsTheFinderAndAnNaddrJoinedByAColon() =
        runTest {
            val event = verification()
            val raw = event.tags.first { it[0] == "a" }[1]

            assertEquals("${finder.pubKey}:", raw.substring(0, 65))
            assertTrue(raw.substring(65).startsWith("naddr1"))
            assertEquals(cacheAddress, NAddress.parse(raw.substring(65))?.address())
        }

    @Test
    fun theCompositeATagIsNotSomethingAddressParseCanRead() =
        runTest {
            // The reason FinderCacheTag exists. A generic `a`-tag reader gets nothing from a 7517
            // (and logs a warning on the way out), so the package parses it itself.
            val raw = verification().tags.first { it[0] == "a" }[1]

            assertNull(Address.parse(raw))
            assertNotNull(FinderCacheTag.parse(arrayOf("a", raw)))
        }

    @Test
    fun readingAlsoAcceptsThePlainAddressFormOfTheCacheHalf() {
        // Writing always emits the naddr NIP-CC specifies; reading tolerates `kind:pubkey:d`
        // because Address.parse handles both and the 64-hex finder keeps the two unambiguous.
        val tag = arrayOf("a", "${finder.pubKey}:${cacheAddress.toValue()}")
        val parsed = FinderCacheTag.parse(tag)

        assertEquals(finder.pubKey, parsed?.finderPubKey)
        assertEquals(cacheAddress, parsed?.cache)
    }

    @Test
    fun aBareNip01AddressIsNotAFinderCachePair() {
        // `37516` on the left is not a 64-char pubkey, so there is no way to mistake one for the
        // other and no way to end up with a verification attributed to nobody.
        assertNull(FinderCacheTag.parse(arrayOf("a", cacheAddress.toValue())))
        assertNull(FinderCacheTag.parse(arrayOf("a", finder.pubKey)))
        assertNull(FinderCacheTag.parse(arrayOf("a", ":${cacheAddress.toValue()}")))
        assertNull(FinderCacheTag.parse(arrayOf("a", "${finder.pubKey}:not-an-address")))
    }

    @Test
    fun aWellFormedVerificationValidates() =
        runTest {
            assertNull(GeocacheVerificationValidator.validate(verification(), listing(), finder.pubKey))
        }

    @Test
    fun aVerificationSignedByAnyOtherKeyIsRejected() =
        runTest {
            // Without the signer check, anyone signs their own "verification" and every cache in
            // the world is verified by whoever wants to claim it.
            val forged = verification(signer = someoneElsesCacheKey)

            assertEquals(
                VerificationFailure.WRONG_SIGNER,
                GeocacheVerificationValidator.validate(forged, listing(), finder.pubKey),
            )
        }

    @Test
    fun aVerificationIssuedToSomeoneElseCannotBeReplayedIntoYourLog() =
        runTest {
            // This is the check that makes the proof about a person. Lift a real verification out
            // of another finder's log, paste it into yours, and without it you are verified.
            val theirs = verification(forFinder = otherFinder.pubKey)

            assertEquals(
                VerificationFailure.FINDER_MISMATCH,
                GeocacheVerificationValidator.validate(theirs, listing(), finder.pubKey),
            )
        }

    @Test
    fun aVerificationEarnedAtAnotherCacheCannotBeReplayedHere() =
        runTest {
            val elsewhere = verification(atCache = otherCacheAddress)

            assertEquals(
                VerificationFailure.CACHE_MISMATCH,
                GeocacheVerificationValidator.validate(elsewhere, listing(), finder.pubKey),
            )
        }

    @Test
    fun aTamperedSignatureIsRejected() =
        runTest {
            val real = verification()
            val tampered =
                GeocacheVerificationEvent(real.id, real.pubKey, real.createdAt, real.tags, real.content, "0".repeat(128))

            assertEquals(
                VerificationFailure.BAD_SIGNATURE,
                GeocacheVerificationValidator.validate(tampered, listing(), finder.pubKey),
            )
        }

    @Test
    fun aRewrittenContentIsRejected() =
        runTest {
            val real = verification()
            val rewritten =
                GeocacheVerificationEvent(real.id, real.pubKey, real.createdAt, real.tags, "Geocache verification for someone", real.sig)

            assertEquals(
                VerificationFailure.CONTENT_MISMATCH,
                GeocacheVerificationValidator.validate(rewritten, listing(), finder.pubKey),
            )
        }

    @Test
    fun aCacheWithoutAVerificationKeyCannotVerifyAnything() =
        runTest {
            assertEquals(
                VerificationFailure.CACHE_HAS_NO_VERIFICATION_KEY,
                GeocacheVerificationValidator.validate(verification(), listing(verificationKey = null), finder.pubKey),
            )
        }

    @Test
    fun aFoundLogValidatesAgainstItsOwnAuthor() =
        runTest {
            val log = foundLog(finder.pubKey, verification())

            assertNull(GeocacheVerificationValidator.validate(log, listing()))
            assertTrue(GeocacheVerificationValidator.isValid(log, listing()))
        }

    @Test
    fun aFoundLogCarryingSomeoneElsesVerificationDoesNotValidate() =
        runTest {
            // The same replay as above, entered through the log rather than the raw event: the
            // author is taken from the log, never from the verification's own `a` tag.
            val log = foundLog(otherFinder.pubKey, verification(forFinder = finder.pubKey))

            assertEquals(VerificationFailure.FINDER_MISMATCH, GeocacheVerificationValidator.validate(log, listing()))
        }

    @Test
    fun aLogWithNoVerificationSaysSoRatherThanFailingSomewhereElse() {
        val log = foundLog(finder.pubKey, null)

        assertEquals(VerificationFailure.NO_EMBEDDED_VERIFICATION, GeocacheVerificationValidator.validate(log, listing()))
    }

    @Test
    fun aGarbageVerificationPayloadIsNullRatherThanAnException() {
        // The payload is whatever a stranger put in a public event. A parse failure that threw
        // would take the feed rendering the log down with it.
        listOf("", "not json", "{", "[1,2,3]", """{"kind":"seven"}""").forEach { payload ->
            val log =
                GeocacheFoundLogEvent(
                    "1".repeat(64),
                    finder.pubKey,
                    1L,
                    arrayOf(arrayOf("a", cacheAddress.toValue()), arrayOf("verification", payload)),
                    "Found it!",
                    "sig",
                )

            assertNull(log.embeddedVerification(), "payload <$payload> should not parse")
            assertEquals(VerificationFailure.NO_EMBEDDED_VERIFICATION, GeocacheVerificationValidator.validate(log, listing()))
        }
    }

    @Test
    fun aVerificationOfAnotherKindEmbeddedInALogIsNotAVerification() =
        runTest {
            // Well-formed JSON for a real event of the wrong kind must not be read as a 7517.
            val notAVerification = finder.sign(GeocacheFoundLogEvent.build("hi", cacheAddress))
            val log =
                GeocacheFoundLogEvent(
                    "1".repeat(64),
                    finder.pubKey,
                    1L,
                    arrayOf(arrayOf("a", cacheAddress.toValue()), arrayOf("verification", notAVerification.toJson())),
                    "Found it!",
                    "sig",
                )

            assertNull(log.embeddedVerification())
        }

    @Test
    fun anEmbeddedVerificationSurvivesTheJsonRoundTrip() =
        runTest {
            val original = verification()
            val template = GeocacheFoundLogEvent.build("Found it!", cacheAddress, verification = original)
            val log = finder.sign(template)
            val reparsed = Event.fromJson(log.toJson()) as GeocacheFoundLogEvent

            assertEquals(original.id, reparsed.embeddedVerification()?.id)
            assertNull(GeocacheVerificationValidator.validate(reparsed, listing()))
        }
}
