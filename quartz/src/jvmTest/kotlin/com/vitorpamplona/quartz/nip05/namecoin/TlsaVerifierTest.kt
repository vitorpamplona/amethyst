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
package com.vitorpamplona.quartz.nip05.namecoin

import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver.TlsaMatchingType
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver.TlsaRecord
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver.TlsaSelector
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver.TlsaUsage
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.TlsaVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

/**
 * Unit tests for the spec-compliant matching policy in [TlsaVerifier].
 *
 * These tests do NOT exercise any TLS handshake — they feed pre-built
 * `CertView` chains and verify that the matching/usage/selector logic is
 * RFC 6698 compliant, including SHA-256, SHA-512, exact-match, full-cert
 * vs SPKI selectors, and end-entity vs trust-anchor cert positioning.
 */
class TlsaVerifierTest {
    private val sha512 = { input: ByteArray -> MessageDigest.getInstance("SHA-512").digest(input) }
    private val sha256 = { input: ByteArray -> MessageDigest.getInstance("SHA-256").digest(input) }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun bytesOf(vararg ints: Int): ByteArray = ByteArray(ints.size) { ints[it].toByte() }

    private fun b64(data: ByteArray): String = Base64.getEncoder().encodeToString(data)

    private fun cert(
        index: Int,
        cert: ByteArray = bytesOf(0x01, 0x02, 0x03, index),
        spki: ByteArray = bytesOf(0xAA, 0xBB, 0xCC, index),
    ) = TlsaVerifier.CertView(index = index, encodedDer = cert, spkiDer = spki)

    // ── Empty / unusable record sets ────────────────────────────────────

    @Test
    fun `empty record set is NoUsableRecords`() {
        val r = TlsaVerifier.verify(listOf(cert(0)), emptyList(), sha512)
        assertSame(TlsaVerifier.Result.NoUsableRecords, r)
    }

    @Test
    fun `record with UNKNOWN usage is skipped`() {
        val rec =
            TlsaRecord(
                usage = TlsaUsage.UNKNOWN,
                selector = TlsaSelector.SUBJECT_PUBLIC_KEY_INFO,
                matchingType = TlsaMatchingType.SHA_256,
                associationDataBase64 = b64(sha256(bytesOf(0xAA, 0xBB, 0xCC, 0))),
            )
        val r = TlsaVerifier.verify(listOf(cert(0)), listOf(rec), sha512)
        assertSame(TlsaVerifier.Result.NoUsableRecords, r)
    }

    @Test
    fun `record with empty data is skipped`() {
        val rec =
            TlsaRecord(
                usage = TlsaUsage.DANE_EE,
                selector = TlsaSelector.SUBJECT_PUBLIC_KEY_INFO,
                matchingType = TlsaMatchingType.SHA_256,
                associationDataBase64 = "",
            )
        val r = TlsaVerifier.verify(listOf(cert(0)), listOf(rec), sha512)
        assertSame(TlsaVerifier.Result.NoUsableRecords, r)
    }

    // ── DANE-EE matching ────────────────────────────────────────────────

    @Test
    fun `DANE-EE SHA-256 SPKI matches leaf`() {
        val leaf = cert(0)
        val rec =
            TlsaRecord(
                usage = TlsaUsage.DANE_EE,
                selector = TlsaSelector.SUBJECT_PUBLIC_KEY_INFO,
                matchingType = TlsaMatchingType.SHA_256,
                associationDataBase64 = b64(sha256(leaf.spkiDer)),
            )
        val r = TlsaVerifier.verify(listOf(leaf), listOf(rec), sha512)
        assertTrue(r is TlsaVerifier.Result.Match)
        r as TlsaVerifier.Result.Match
        assertEquals(0, r.matchedCertIndex)
        assertEquals(false, r.requiresPkixValidation)
    }

    @Test
    fun `DANE-EE SHA-512 full cert matches leaf`() {
        val leaf = cert(0)
        val rec =
            TlsaRecord(
                usage = TlsaUsage.DANE_EE,
                selector = TlsaSelector.FULL_CERT,
                matchingType = TlsaMatchingType.SHA_512,
                associationDataBase64 = b64(sha512(leaf.encodedDer)),
            )
        val r = TlsaVerifier.verify(listOf(leaf), listOf(rec), sha512)
        assertTrue("expected Match got $r", r is TlsaVerifier.Result.Match)
    }

    @Test
    fun `DANE-EE EXACT full cert matches`() {
        val leaf = cert(0)
        val rec =
            TlsaRecord(
                usage = TlsaUsage.DANE_EE,
                selector = TlsaSelector.FULL_CERT,
                matchingType = TlsaMatchingType.EXACT,
                associationDataBase64 = b64(leaf.encodedDer),
            )
        val r = TlsaVerifier.verify(listOf(leaf), listOf(rec), sha512)
        assertTrue(r is TlsaVerifier.Result.Match)
    }

    @Test
    fun `DANE-EE does NOT match against intermediate cert`() {
        // RFC 6698: usage 3 (DANE-EE) MUST match the end-entity (leaf) only.
        val leaf = cert(0)
        val intermediate = cert(1)
        val rec =
            TlsaRecord(
                usage = TlsaUsage.DANE_EE,
                selector = TlsaSelector.SUBJECT_PUBLIC_KEY_INFO,
                matchingType = TlsaMatchingType.SHA_256,
                associationDataBase64 = b64(sha256(intermediate.spkiDer)),
            )
        val r = TlsaVerifier.verify(listOf(leaf, intermediate), listOf(rec), sha512)
        assertTrue("intermediate must not satisfy DANE-EE", r is TlsaVerifier.Result.NoMatch)
    }

    // ── DANE-TA / PKIX-TA matching against intermediates ────────────────

    @Test
    fun `DANE-TA SHA-256 SPKI matches root in chain`() {
        val leaf = cert(0)
        val intermediate = cert(1)
        val root = cert(2)
        val rec =
            TlsaRecord(
                usage = TlsaUsage.DANE_TA,
                selector = TlsaSelector.SUBJECT_PUBLIC_KEY_INFO,
                matchingType = TlsaMatchingType.SHA_256,
                associationDataBase64 = b64(sha256(root.spkiDer)),
            )
        val r = TlsaVerifier.verify(listOf(leaf, intermediate, root), listOf(rec), sha512)
        assertTrue(r is TlsaVerifier.Result.Match)
        r as TlsaVerifier.Result.Match
        assertEquals(2, r.matchedCertIndex)
        assertEquals(false, r.requiresPkixValidation)
    }

    @Test
    fun `PKIX-EE matches leaf and requires PKIX validation`() {
        val leaf = cert(0)
        val rec =
            TlsaRecord(
                usage = TlsaUsage.PKIX_EE,
                selector = TlsaSelector.SUBJECT_PUBLIC_KEY_INFO,
                matchingType = TlsaMatchingType.SHA_256,
                associationDataBase64 = b64(sha256(leaf.spkiDer)),
            )
        val r = TlsaVerifier.verify(listOf(leaf), listOf(rec), sha512)
        assertTrue(r is TlsaVerifier.Result.Match)
        r as TlsaVerifier.Result.Match
        assertEquals(true, r.requiresPkixValidation)
    }

    @Test
    fun `PKIX-TA matches intermediate and requires PKIX validation`() {
        val leaf = cert(0)
        val intermediate = cert(1)
        val rec =
            TlsaRecord(
                usage = TlsaUsage.PKIX_TA,
                selector = TlsaSelector.SUBJECT_PUBLIC_KEY_INFO,
                matchingType = TlsaMatchingType.SHA_256,
                associationDataBase64 = b64(sha256(intermediate.spkiDer)),
            )
        val r = TlsaVerifier.verify(listOf(leaf, intermediate), listOf(rec), sha512)
        assertTrue(r is TlsaVerifier.Result.Match)
        r as TlsaVerifier.Result.Match
        assertEquals(1, r.matchedCertIndex)
        assertEquals(true, r.requiresPkixValidation)
    }

    // ── No match path ───────────────────────────────────────────────────

    @Test
    fun `unrelated cert chain triggers NoMatch`() {
        val leaf = cert(0)
        val rec =
            TlsaRecord(
                usage = TlsaUsage.DANE_EE,
                selector = TlsaSelector.SUBJECT_PUBLIC_KEY_INFO,
                matchingType = TlsaMatchingType.SHA_256,
                associationDataBase64 = b64(sha256(bytesOf(0xDE, 0xAD, 0xBE, 0xEF))),
            )
        val r = TlsaVerifier.verify(listOf(leaf), listOf(rec), sha512)
        assertTrue(r is TlsaVerifier.Result.NoMatch)
        r as TlsaVerifier.Result.NoMatch
        assertEquals(1, r.triedRecords)
    }

    // ── Multi-record short-circuit ──────────────────────────────────────

    @Test
    fun `first matching record short-circuits subsequent ones`() {
        val leaf = cert(0)
        val matchingRecord =
            TlsaRecord(
                usage = TlsaUsage.DANE_EE,
                selector = TlsaSelector.SUBJECT_PUBLIC_KEY_INFO,
                matchingType = TlsaMatchingType.SHA_256,
                associationDataBase64 = b64(sha256(leaf.spkiDer)),
            )
        val unrelated =
            TlsaRecord(
                usage = TlsaUsage.DANE_EE,
                selector = TlsaSelector.FULL_CERT,
                matchingType = TlsaMatchingType.EXACT,
                associationDataBase64 = b64(bytesOf(0x00)),
            )
        val r = TlsaVerifier.verify(listOf(leaf), listOf(matchingRecord, unrelated), sha512)
        assertTrue(r is TlsaVerifier.Result.Match)
        r as TlsaVerifier.Result.Match
        assertSame(matchingRecord, r.matchedRecord)
    }

    @Test
    fun `multiple records, second one wins`() {
        val leaf = cert(0)
        val unrelated =
            TlsaRecord(
                usage = TlsaUsage.DANE_EE,
                selector = TlsaSelector.SUBJECT_PUBLIC_KEY_INFO,
                matchingType = TlsaMatchingType.SHA_256,
                associationDataBase64 = b64(sha256(bytesOf(0x77))),
            )
        val matchingRecord =
            TlsaRecord(
                usage = TlsaUsage.DANE_EE,
                selector = TlsaSelector.SUBJECT_PUBLIC_KEY_INFO,
                matchingType = TlsaMatchingType.SHA_256,
                associationDataBase64 = b64(sha256(leaf.spkiDer)),
            )
        val r = TlsaVerifier.verify(listOf(leaf), listOf(unrelated, matchingRecord), sha512)
        assertTrue(r is TlsaVerifier.Result.Match)
        r as TlsaVerifier.Result.Match
        assertSame(matchingRecord, r.matchedRecord)
    }

    // ── Base64 quirks ────────────────────────────────────────────────────

    @Test
    fun `base64 with newlines decodes`() {
        val leaf = cert(0)
        // MIME-style line wrapping every 76 chars is allowed per RFC 4648
        val raw = b64(sha256(leaf.spkiDer))
        val wrapped = raw.chunked(8).joinToString("\n")
        val rec =
            TlsaRecord(
                usage = TlsaUsage.DANE_EE,
                selector = TlsaSelector.SUBJECT_PUBLIC_KEY_INFO,
                matchingType = TlsaMatchingType.SHA_256,
                associationDataBase64 = wrapped,
            )
        val r = TlsaVerifier.verify(listOf(leaf), listOf(rec), sha512)
        assertTrue("wrapped base64 must still match: $r", r is TlsaVerifier.Result.Match)
    }

    @Test
    fun `unpadded base64 decodes`() {
        // Some publishers strip = padding; we accept that.
        val leaf = cert(0, cert = bytesOf(0x01, 0x02, 0x03))
        val raw = b64(sha256(leaf.spkiDer)).trimEnd('=')
        val rec =
            TlsaRecord(
                usage = TlsaUsage.DANE_EE,
                selector = TlsaSelector.SUBJECT_PUBLIC_KEY_INFO,
                matchingType = TlsaMatchingType.SHA_256,
                associationDataBase64 = raw,
            )
        val r = TlsaVerifier.verify(listOf(leaf), listOf(rec), sha512)
        assertTrue("unpadded base64 must still match: $r", r is TlsaVerifier.Result.Match)
    }

    @Test
    fun `garbage base64 is treated as unusable, not a crash`() {
        val rec =
            TlsaRecord(
                usage = TlsaUsage.DANE_EE,
                selector = TlsaSelector.SUBJECT_PUBLIC_KEY_INFO,
                matchingType = TlsaMatchingType.SHA_256,
                associationDataBase64 = "$$ NOT BASE64 $$",
            )
        val r = TlsaVerifier.verify(listOf(cert(0)), listOf(rec), sha512)
        // Must NOT throw, must NOT match, must report as no-usable since it's the only record.
        assertTrue(r is TlsaVerifier.Result.NoUsableRecords)
    }

    // ── verifyWithStapled ── ncgencert AIA-stapled SPKI fallback path ──────

    @Test
    fun `verifyWithStapled DANE-TA SHA-256 matches a stapled SPKI when chain has none`() {
        val stapledSpki = bytesOf(0x99, 0x88, 0x77, 0x66, 0x55, 0x44, 0x33, 0x22, 0x11)
        val rec =
            TlsaRecord(
                usage = TlsaUsage.DANE_TA,
                selector = TlsaSelector.SUBJECT_PUBLIC_KEY_INFO,
                matchingType = TlsaMatchingType.SHA_256,
                associationDataBase64 = b64(sha256(stapledSpki)),
            )
        val r =
            TlsaVerifier.verifyWithStapled(
                chain = listOf(cert(0), cert(1)), // unrelated SPKIs in the served chain
                records = listOf(rec),
                stapledSpkis = listOf(stapledSpki),
                sha512 = sha512,
            )
        assertTrue("expected match against stapled SPKI: $r", r is TlsaVerifier.Result.Match)
        val match = r as TlsaVerifier.Result.Match
        assertEquals(-1, match.matchedCertIndex) // -1 signals stapled-fallback match
        assertSame(rec, match.matchedRecord)
        assertEquals(false, match.requiresPkixValidation) // DANE-TA does not require PKIX
    }

    @Test
    fun `verifyWithStapled PKIX-TA SHA-256 stapled match still requires PKIX`() {
        val stapledSpki = bytesOf(0xDE, 0xAD, 0xBE, 0xEF)
        val rec =
            TlsaRecord(
                usage = TlsaUsage.PKIX_TA,
                selector = TlsaSelector.SUBJECT_PUBLIC_KEY_INFO,
                matchingType = TlsaMatchingType.SHA_256,
                associationDataBase64 = b64(sha256(stapledSpki)),
            )
        val r =
            TlsaVerifier.verifyWithStapled(
                chain = listOf(cert(0)),
                records = listOf(rec),
                stapledSpkis = listOf(stapledSpki),
                sha512 = sha512,
            )
        val match = r as TlsaVerifier.Result.Match
        assertEquals(true, match.requiresPkixValidation)
    }

    @Test
    fun `verifyWithStapled does not promote stapled SPKI for EE usages`() {
        val stapledSpki = bytesOf(0xCA, 0xFE)
        val daneEe =
            TlsaRecord(
                usage = TlsaUsage.DANE_EE,
                selector = TlsaSelector.SUBJECT_PUBLIC_KEY_INFO,
                matchingType = TlsaMatchingType.SHA_256,
                associationDataBase64 = b64(sha256(stapledSpki)),
            )
        val r =
            TlsaVerifier.verifyWithStapled(
                chain = listOf(cert(0)),
                records = listOf(daneEe),
                stapledSpkis = listOf(stapledSpki),
                sha512 = sha512,
            )
        // DANE-EE matches the leaf only, never a stapled key (the leaf can't be "stapled into" a CA).
        assertTrue("DANE-EE must not match stapled keys: $r", r is TlsaVerifier.Result.NoMatch)
    }

    @Test
    fun `verifyWithStapled prefers chain match over stapled fallback`() {
        val leafSpki = bytesOf(0xAA, 0xBB, 0xCC, 0)
        val stapledSpki = bytesOf(0xDE, 0xAD)
        val danceTaForLeaf =
            TlsaRecord(
                usage = TlsaUsage.DANE_TA,
                selector = TlsaSelector.SUBJECT_PUBLIC_KEY_INFO,
                matchingType = TlsaMatchingType.SHA_256,
                associationDataBase64 = b64(sha256(leafSpki)),
            )
        val danceTaForStapled =
            TlsaRecord(
                usage = TlsaUsage.DANE_TA,
                selector = TlsaSelector.SUBJECT_PUBLIC_KEY_INFO,
                matchingType = TlsaMatchingType.SHA_256,
                associationDataBase64 = b64(sha256(stapledSpki)),
            )
        val r =
            TlsaVerifier.verifyWithStapled(
                chain = listOf(cert(0)), // SPKI = 0xAA 0xBB 0xCC 0x00
                records = listOf(danceTaForLeaf, danceTaForStapled),
                stapledSpkis = listOf(stapledSpki),
                sha512 = sha512,
            )
        val match = r as TlsaVerifier.Result.Match
        assertEquals(0, match.matchedCertIndex) // chain match wins; stapled never tried
    }

    @Test
    fun `verifyWithStapled empty stapledSpkis behaves like verify`() {
        val rec =
            TlsaRecord(
                usage = TlsaUsage.DANE_TA,
                selector = TlsaSelector.SUBJECT_PUBLIC_KEY_INFO,
                matchingType = TlsaMatchingType.SHA_256,
                associationDataBase64 = b64(sha256(bytesOf(0x01))),
            )
        val r =
            TlsaVerifier.verifyWithStapled(
                chain = listOf(cert(0)), // SPKI doesn't match
                records = listOf(rec),
                stapledSpkis = emptyList(),
                sha512 = sha512,
            )
        assertTrue(r is TlsaVerifier.Result.NoMatch)
    }

    @Test
    fun `verifyWithStapled FULL_CERT selector skips stapled fallback`() {
        // Stapled keys are SPKIs only — we can't synthesise a full-cert match from a stapled SPKI.
        val stapledSpki = bytesOf(0x42)
        val rec =
            TlsaRecord(
                usage = TlsaUsage.DANE_TA,
                selector = TlsaSelector.FULL_CERT,
                matchingType = TlsaMatchingType.SHA_256,
                associationDataBase64 = b64(sha256(stapledSpki)),
            )
        val r =
            TlsaVerifier.verifyWithStapled(
                chain = listOf(cert(0)),
                records = listOf(rec),
                stapledSpkis = listOf(stapledSpki),
                sha512 = sha512,
            )
        assertTrue("FULL_CERT selector must not be served by stapled SPKIs: $r", r is TlsaVerifier.Result.NoMatch)
    }
}
