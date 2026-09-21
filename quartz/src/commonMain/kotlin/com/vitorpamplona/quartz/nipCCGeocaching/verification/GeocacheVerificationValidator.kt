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
package com.vitorpamplona.quartz.nipCCGeocaching.verification

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nipCCGeocaching.foundLog.GeocacheFoundLogEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent

/** Why a kind 7517 does not prove what it claims to prove. Ordered cheapest check first. */
enum class VerificationFailure {
    /** The found log carries no `verification` tag, or its payload did not parse as a 7517. */
    NO_EMBEDDED_VERIFICATION,

    /** The listing has no `verification` tag, so nothing at this cache can be verified. */
    CACHE_HAS_NO_VERIFICATION_KEY,

    /** The `a` tag is missing or is not the `<finder-hex>:<cache>` pair NIP-CC defines. */
    MALFORMED_FINDER_CACHE_TAG,

    /** The 7517 names a different cache than the listing it is being checked against. */
    CACHE_MISMATCH,

    /** The 7517 was issued to somebody other than the author of the found log. */
    FINDER_MISMATCH,

    /** `content` is not the static `"Geocache verification for <npub>"` string. */
    CONTENT_MISMATCH,

    /** The 7517 was signed by some other key than the cache's verification key. */
    WRONG_SIGNER,

    /** The id or the signature does not check out. */
    BAD_SIGNATURE,
}

/**
 * The four checks NIP-CC lists under "Verification Validation", plus the two the spec leaves
 * implicit.
 *
 * Each one exists because skipping it breaks something concrete:
 *
 * - **signer** — without it, anybody signs their own "verification" and every cache is verified.
 * - **finder** — without it, a verification issued to somebody else can be lifted out of their
 *   log and replayed into yours. This is the check that makes the proof about *a person* rather
 *   than about the cache.
 * - **cache** — without it, a verification earned at an easy cache is replayed at a hard one.
 * - **signature** — without it, all three of the above are checks against unauthenticated text.
 * - **content** — NIP-CC fixes the content string, so a mismatch means the event was not
 *   produced by a conforming client and its other fields are not worth trusting either.
 *
 * Note what a valid result does and does not mean. It means somebody who had access to the
 * private key at the cache issued this to that finder for that cache. It does not mean the finder
 * went there in person — a key that has leaked verifies just as well, which is why NIP-CC treats
 * these as evidence of presence rather than proof of identity, and why first-to-find claims get
 * locked in by the owner rather than decided by timestamps.
 */
object GeocacheVerificationValidator {
    /**
     * Validates [verification] as proof that [finderPubKey] was at [listing].
     *
     * @return null when it checks out, or the first [VerificationFailure] found.
     */
    fun validate(
        verification: GeocacheVerificationEvent,
        listing: GeocacheListingEvent,
        finderPubKey: HexKey,
    ): VerificationFailure? {
        val expectedSigner = listing.verificationKey() ?: return VerificationFailure.CACHE_HAS_NO_VERIFICATION_KEY
        val claim = verification.finderCache() ?: return VerificationFailure.MALFORMED_FINDER_CACHE_TAG

        if (claim.cache != listing.address()) return VerificationFailure.CACHE_MISMATCH
        if (claim.finderPubKey != finderPubKey) return VerificationFailure.FINDER_MISMATCH
        if (verification.content != GeocacheVerificationEvent.contentFor(claim.finderPubKey)) return VerificationFailure.CONTENT_MISMATCH
        if (verification.pubKey != expectedSigner) return VerificationFailure.WRONG_SIGNER

        // Last because it is the only check that costs a curve operation.
        if (!verification.verify()) return VerificationFailure.BAD_SIGNATURE

        return null
    }

    /** Validates the verification embedded in [log], attributing it to the log's own author. */
    fun validate(
        log: GeocacheFoundLogEvent,
        listing: GeocacheListingEvent,
    ): VerificationFailure? {
        val verification = log.embeddedVerification() ?: return VerificationFailure.NO_EMBEDDED_VERIFICATION
        return validate(verification, listing, log.pubKey)
    }

    fun isValid(
        verification: GeocacheVerificationEvent,
        listing: GeocacheListingEvent,
        finderPubKey: HexKey,
    ) = validate(verification, listing, finderPubKey) == null

    /** Whether [log] carries a verification that holds up against [listing]. */
    fun isValid(
        log: GeocacheFoundLogEvent,
        listing: GeocacheListingEvent,
    ) = validate(log, listing) == null
}
