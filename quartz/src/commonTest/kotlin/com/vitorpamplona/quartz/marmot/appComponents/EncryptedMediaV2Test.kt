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
package com.vitorpamplona.quartz.marmot.appComponents

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `app-components/group-encrypted-media-v2.md` and
 * `features/encrypted-media.md`.
 */
class EncryptedMediaV2Test {
    private val mediaSecret = ByteArray(32) { it.toByte() }

    // --- policy component 0x800b -----------------------------------------

    @Test
    fun theReferencePolicyRoundTrips() {
        val decoded = EncryptedMediaPolicyV2.decode(EncryptedMediaPolicyV2.REFERENCE.encode())
        assertEquals(EncryptedMediaPolicyV2.REFERENCE, decoded)
        assertEquals("encrypted-media-v2", decoded.mediaFormat)
        assertEquals(listOf("blossom-v1"), decoded.allowedLocatorKinds)
        assertEquals("https://blossom.primal.net/", decoded.defaultBlobEndpoints.single().baseUrl)
    }

    /**
     * Endpoint order IS the upload/fetch fallback priority, so — unlike relays
     * or admins — it must NOT be sorted. Two policies differing only in order
     * are different canonical values.
     */
    @Test
    fun endpointOrderIsPartOfTheValue() {
        val a =
            EncryptedMediaPolicyV2(
                allowedLocatorKinds = listOf("blossom-v1"),
                defaultBlobEndpoints =
                    listOf(
                        BlobStoreEndpointV2("blossom-v1", "https://a.example.com/"),
                        BlobStoreEndpointV2("blossom-v1", "https://b.example.com/"),
                    ),
            )
        val reversed = a.copy(defaultBlobEndpoints = a.defaultBlobEndpoints.reversed())
        assertNotEquals(a, reversed)
        assertFalse(a.encode().contentEquals(reversed.encode()))
        assertEquals(reversed, EncryptedMediaPolicyV2.decode(reversed.encode()))
    }

    /** A decoder rejects rather than repairs: these bytes sit in signed group state. */
    @Test
    fun rejectsNonCanonicalAndInvalidState() {
        assertFailsWith<IllegalArgumentException> {
            EncryptedMediaPolicyV2(listOf("blossom-v1"), emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            EncryptedMediaPolicyV2(emptyList(), listOf(BlobStoreEndpointV2("blossom-v1", "https://a.example.com/")))
        }
        // An endpoint serving a kind the policy does not allow.
        assertFailsWith<IllegalArgumentException> {
            EncryptedMediaPolicyV2(listOf("blossom-v1"), listOf(BlobStoreEndpointV2("other-v1", "https://a.example.com/")))
        }
        // A duplicate is refused, not deduplicated.
        assertFailsWith<IllegalArgumentException> {
            EncryptedMediaPolicyV2(listOf("blossom-v1", "blossom-v1"), listOf(BlobStoreEndpointV2("blossom-v1", "https://a.example.com/")))
        }
        // media_format is a constant, not negotiation.
        assertFailsWith<IllegalArgumentException> {
            EncryptedMediaPolicyV2(listOf("blossom-v1"), listOf(BlobStoreEndpointV2("blossom-v1", "https://a.example.com/")), "encrypted-media-v3")
        }
        // Trailing bytes are refused, not ignored.
        assertFailsWith<IllegalArgumentException> {
            EncryptedMediaPolicyV2.decode(EncryptedMediaPolicyV2.REFERENCE.encode() + byteArrayOf(0))
        }
    }

    @Test
    fun rejectsNonNormalizedBaseUrls() {
        listOf(
            "ftp://a.example.com/",
            "https://user@a.example.com/",
            "https://a.example.com/?q=1",
            "https://a.example.com/#f",
            "https://A.EXAMPLE.COM/",
            "https://a.example.com/../up/",
            "https:///",
        ).forEach { url ->
            assertFailsWith<IllegalArgumentException>("expected '$url' to be rejected") {
                EncryptedMediaPolicyV2(listOf("blossom-v1"), listOf(BlobStoreEndpointV2("blossom-v1", url)))
            }
        }
    }

    @Test
    fun acceptsBaseUrlsTheWhatwgSerializerLeavesAlone() {
        // An empty path segment is NOT collapsed by the WHATWG serializer — the
        // path is a segment list, and only "." and ".." are special. Rejecting
        // a doubled slash therefore refuses group state the reference
        // implementation produces and accepts, which is the exact failure mode
        // this component's normalization rule exists to prevent.
        //
        // `http` is likewise valid here and not for avatars: the media policy
        // permits it, and a self-hosted blob store on a private network is a
        // real deployment.
        listOf(
            "https://a.example.com//double/",
            "http://blobs.internal/",
            "https://a.example.com:8443/blobs/",
        ).forEach { url ->
            EncryptedMediaPolicyV2(listOf("blossom-v1"), listOf(BlobStoreEndpointV2("blossom-v1", url)))
        }
    }

    @Test
    fun dropsNothingButRefusesToRepair() {
        // The default port is absent from a normalized URL, so a stored value
        // carrying it is non-normalized and refused rather than trimmed. A
        // decoder that repaired it would hold different bytes than the peer
        // that stored them.
        listOf("https://a.example.com:443/", "http://a.example.com:80/").forEach { url ->
            assertFailsWith<IllegalArgumentException>("expected '$url' to be rejected") {
                EncryptedMediaPolicyV2(listOf("blossom-v1"), listOf(BlobStoreEndpointV2("blossom-v1", url)))
            }
        }
    }

    @Test
    fun rejectsInvalidLocatorKinds() {
        listOf("", "Blossom-v1", "blossom_v1", "blossom v1", "a".repeat(65)).forEach { kind ->
            assertFailsWith<IllegalArgumentException>("expected '$kind' to be rejected") {
                EncryptedMediaPolicyV2.requireValidLocatorKind(kind)
            }
        }
    }

    /** Blossom fetch and upload URLs are built from `server_root`, trailing slashes gone. */
    @Test
    fun buildsBlossomFallbackUrls() {
        val endpoint = BlobStoreEndpointV2("blossom-v1", "https://blossom.primal.net/")
        val hash = "ab".repeat(32)
        assertEquals("https://blossom.primal.net/$hash", endpoint.blossomFetchUrl(hash))
        assertEquals("https://blossom.primal.net/upload", endpoint.blossomUploadUrl())
    }

    // --- content crypto ---------------------------------------------------

    @Test
    fun encryptsAndDecryptsAnAttachment() {
        val plaintext = "a picture of a marmot".encodeToByteArray()
        val result = EncryptedMediaV2.encrypt(plaintext, mediaSecret, "image/jpeg", "marmot.jpg")

        assertEquals(12, result.nonce.size)
        assertContentEquals(
            plaintext,
            EncryptedMediaV2.decrypt(
                result.ciphertext,
                mediaSecret,
                result.nonce,
                result.plaintextSha256,
                "image/jpeg",
                "marmot.jpg",
            ),
        )
    }

    /**
     * The key is deterministic in (plaintext hash, media type, filename, epoch),
     * so a resend reuses it — and the nonce MUST still be fresh, or
     * ChaCha20-Poly1305 breaks outright.
     */
    @Test
    fun aResendReusesTheKeyButNeverTheNonce() {
        val plaintext = "same file".encodeToByteArray()
        val first = EncryptedMediaV2.encrypt(plaintext, mediaSecret, "image/png", "a.png")
        val second = EncryptedMediaV2.encrypt(plaintext, mediaSecret, "image/png", "a.png")

        assertContentEquals(
            EncryptedMediaV2.deriveFileKey(mediaSecret, first.plaintextSha256, "image/png", "a.png"),
            EncryptedMediaV2.deriveFileKey(mediaSecret, second.plaintextSha256, "image/png", "a.png"),
        )
        assertFalse(first.nonce.contentEquals(second.nonce), "every encryption needs a fresh nonce")
    }

    /**
     * The media type and filename are inside both the key info and the AAD, so
     * changing either makes decryption fail rather than silently succeed with a
     * different attribution.
     */
    @Test
    fun theMediaTypeAndFilenameAreAuthenticated() {
        val plaintext = "bytes".encodeToByteArray()
        val result = EncryptedMediaV2.encrypt(plaintext, mediaSecret, "image/png", "a.png")

        assertFailsWith<Exception> {
            EncryptedMediaV2.decrypt(result.ciphertext, mediaSecret, result.nonce, result.plaintextSha256, "image/jpeg", "a.png")
        }
        assertFailsWith<Exception> {
            EncryptedMediaV2.decrypt(result.ciphertext, mediaSecret, result.nonce, result.plaintextSha256, "image/png", "b.png")
        }
    }

    /** A non-canonical `m` is refused rather than normalized on the way in. */
    @Test
    fun refusesANonCanonicalMediaType() {
        assertFailsWith<IllegalArgumentException> {
            EncryptedMediaV2.encrypt("x".encodeToByteArray(), mediaSecret, "IMAGE/JPG", "a.jpg")
        }
        assertEquals("image/jpeg", MarmotMediaType.canonicalize("IMAGE/JPG"))
    }

    // --- imeta references -------------------------------------------------

    private fun reference() =
        EncryptedMediaReferenceV2(
            locators = listOf(MediaLocatorV2("blossom-v1", "https://blossom.primal.net/" + "ab".repeat(32))),
            ciphertextSha256 = ByteArray(32) { 1 },
            plaintextSha256 = ByteArray(32) { 2 },
            nonce = ByteArray(12) { 3 },
            mediaType = "image/jpeg",
            filename = "marmot.jpg",
        )

    /**
     * The tag we WRITE, field for field, against MDK's own accepted fixture
     * (`crates/marmot-app/src/media/tests.rs`, `valid_v2_imeta_tag`).
     *
     * Round-tripping through our own parser cannot catch a dialect drift,
     * because both ends drift together -- which is exactly what happened: the
     * app shipped MIP-era `url`/`x`/`n`/`v mip04-v2` tags that our reader
     * accepted and every other implementation dropped at its typed parser,
     * silently, so an attachment simply did not appear. Pinning the names and
     * their order against the reference implementation's fixture is what makes
     * that visible here instead of on someone's screen.
     */
    @Test
    fun theWrittenTagMatchesTheReferenceImplementationsFixture() {
        val tag = reference().toImetaTag()

        assertEquals("imeta", tag[0])
        assertEquals("v encrypted-media-v2", tag[1])
        assertEquals("locator blossom-v1 https://blossom.primal.net/" + "ab".repeat(32), tag[2])
        assertEquals("ciphertext_sha256 " + "01".repeat(32), tag[3])
        assertEquals("plaintext_sha256 " + "02".repeat(32), tag[4])
        assertEquals("nonce " + "03".repeat(12), tag[5])
        assertEquals("m image/jpeg", tag[6])
        assertEquals("filename marmot.jpg", tag[7])

        // None of the MIP-era field names may appear: a receiver keys on the
        // first token of each field, so `url`/`x`/`n` are simply unknown to it.
        val names = tag.drop(1).map { it.substringBefore(' ') }
        assertTrue(names.none { it == "url" || it == "x" || it == "n" }, "MIP-era field names must not be written: $names")
    }

    @Test
    fun anImetaTagRoundTrips() {
        val parsed = EncryptedMediaV2.parseImetaTag(reference().toImetaTag())
        assertEquals("image/jpeg", parsed.mediaType)
        assertEquals("marmot.jpg", parsed.filename)
        assertEquals(1, parsed.locators.size)
        assertEquals("blossom-v1", parsed.locators[0].kind)
        assertContentEquals(ByteArray(12) { 3 }, parsed.nonce)
    }

    /**
     * `m`, `filename` and `plaintext_sha256` feed the key derivation, so a
     * first-wins decoder and a last-wins decoder would derive DIFFERENT keys
     * from the same authenticated tag. The duplicate is refused instead.
     */
    @Test
    fun rejectsADuplicateSingleOccurrenceField() {
        val doubled = reference().toImetaTag().toMutableList().apply { add("m image/png") }
        val failure =
            assertFailsWith<IllegalArgumentException> {
                EncryptedMediaV2.parseImetaTag(doubled.toTypedArray())
            }
        assertTrue(failure.message!!.contains("more than once"))
    }

    /** Exactly `locator` may repeat. */
    @Test
    fun acceptsSeveralLocators() {
        val tag =
            reference()
                .toImetaTag()
                .toMutableList()
                .apply { add(1, "locator blossom-v1 https://mirror.example.com/blob") }
        assertEquals(2, EncryptedMediaV2.parseImetaTag(tag.toTypedArray()).locators.size)
    }

    @Test
    fun rejectsBlurhashAndWrongVersion() {
        val withBlurhash = reference().toImetaTag().toMutableList().apply { add("blurhash abc") }
        assertNull(EncryptedMediaV2.parseImetaTagOrNull(withBlurhash.toTypedArray()))

        val v1 = reference().toImetaTag().map { if (it.startsWith("v ")) "v encrypted-media-v1" else it }
        assertNull(EncryptedMediaV2.parseImetaTagOrNull(v1.toTypedArray()))
    }

    @Test
    fun rejectsMalformedHashesAndNonces() {
        val badNonce = reference().toImetaTag().map { if (it.startsWith("nonce ")) "nonce abcd" else it }
        assertNull(EncryptedMediaV2.parseImetaTagOrNull(badNonce.toTypedArray()))

        val badHash =
            reference().toImetaTag().map {
                if (it.startsWith("ciphertext_sha256 ")) "ciphertext_sha256 notahash" else it
            }
        assertNull(EncryptedMediaV2.parseImetaTagOrNull(badHash.toTypedArray()))
    }

    /** A locator must be usable: an empty kind or value is not a reference. */
    @Test
    fun rejectsAnEmptyOrNonUrlLocator() {
        assertFailsWith<IllegalArgumentException> {
            EncryptedMediaReferenceV2(
                locators = listOf(MediaLocatorV2("blossom-v1", "not-a-url")),
                ciphertextSha256 = ByteArray(32),
                plaintextSha256 = ByteArray(32),
                nonce = ByteArray(12),
                mediaType = "image/jpeg",
                filename = "a.jpg",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            EncryptedMediaReferenceV2(
                locators = emptyList(),
                ciphertextSha256 = ByteArray(32),
                plaintextSha256 = ByteArray(32),
                nonce = ByteArray(12),
                mediaType = "image/jpeg",
                filename = "a.jpg",
            )
        }
    }

    @Test
    fun theCiphertextHashIsTheBlobContentId() {
        val result = EncryptedMediaV2.encrypt("bytes".encodeToByteArray(), mediaSecret, "image/png", "a.png")
        val endpoint = BlobStoreEndpointV2("blossom-v1", "https://blossom.primal.net/")
        assertEquals(
            "https://blossom.primal.net/" + result.ciphertextSha256.toHexKey(),
            endpoint.blossomFetchUrl(result.ciphertextSha256.toHexKey()),
        )
    }
}
