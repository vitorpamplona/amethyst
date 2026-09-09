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

import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `marmot.group.avatar-url.v1` (`0x8007`) — the plain-https alternative to a
 * Blossom group image.
 *
 * The load-bearing rule is normalization. The spec says a producer stores the
 * WHATWG-serialized form and "a decoder re-runs validation and the WHATWG
 * parse-and-serialize on the decoded `url` and MUST reject state whose stored
 * URL bytes differ from the serializer's output". So our normalizer has to
 * agree with everybody else's byte for byte: too lax and we accept state a peer
 * rejects, too strict and we reject a group MDK made.
 *
 * The expectations below are not invented. They are the output of the Rust
 * `url` 2.5.8 crate — the one MDK calls through
 * `validate_and_normalize_group_avatar_url` — run over each input.
 */
class GroupAvatarUrlV1Test {
    @Test
    fun normalizationMatchesTheReferenceSerializerCaseForCase() {
        val vectors =
            listOf(
                // scheme + host lowercased, default port dropped
                "https://CDN.Example.COM:443/a.png" to "https://cdn.example.com/a.png",
                // an empty path serializes as "/"
                "https://cdn.example.com" to "https://cdn.example.com/",
                "https://cdn.example.com/" to "https://cdn.example.com/",
                // dot segments resolve
                "https://cdn.example.com/a/./b/../c.png" to "https://cdn.example.com/a/c.png",
                // percent-encoding case is PRESERVED, not canonicalised, and an
                // already-encoded sequence is never decoded
                "https://cdn.example.com/%7euser/a.png" to "https://cdn.example.com/%7euser/a.png",
                "https://cdn.example.com/%7Euser/a.png" to "https://cdn.example.com/%7Euser/a.png",
                "https://cdn.example.com/A%2fB.png" to "https://cdn.example.com/A%2fB.png",
                // characters outside the path set are encoded, in UPPERCASE hex
                "https://cdn.example.com/a b.png" to "https://cdn.example.com/a%20b.png",
                "https://cdn.example.com/ünïcode.png" to "https://cdn.example.com/%C3%BCn%C3%AFcode.png",
                // a non-default port stays
                "https://cdn.example.com:8443/a.png" to "https://cdn.example.com:8443/a.png",
                // the query is carried through, including a bare trailing "?"
                "https://cdn.example.com/a.png?x=1&y=2" to "https://cdn.example.com/a.png?x=1&y=2",
                "https://cdn.example.com/a.png?" to "https://cdn.example.com/a.png?",
                // an IPv6 literal lowercases inside its brackets
                "https://[2001:DB8::1]/a.png" to "https://[2001:db8::1]/a.png",
                // an already-punycoded host is left alone
                "https://xn--bcher-kva.example/a.png" to "https://xn--bcher-kva.example/a.png",
            )

        for ((raw, expected) in vectors) {
            assertEquals("normalizing $raw", expected, MarmotHttpsUrl.normalize(raw))
        }
    }

    @Test
    fun normalizationIsIdempotent() {
        // A decoder compares stored bytes against its own serialization, so a
        // normalizer that moves on the second pass would reject its own output.
        for (raw in listOf(
            "https://CDN.Example.COM:443/a/./b/../c.png?x=1",
            "https://cdn.example.com/%7euser/a b.png",
            "https://[2001:DB8::1]:8443/",
        )) {
            val once = MarmotHttpsUrl.normalize(raw)
            assertEquals(once, MarmotHttpsUrl.normalize(once))
        }
    }

    @Test
    fun onlyHttpsWithAHostAndNoUserinfoOrFragmentIsValid() {
        for (bad in listOf(
            "http://cdn.example.com/a.png",
            "ftp://cdn.example.com/a.png",
            "blossom://cdn.example.com/a.png",
            "https://user:pass@cdn.example.com/a.png",
            "https://user@cdn.example.com/a.png",
            "https://cdn.example.com/a.png#frag",
            "https:///a.png",
            "https://",
            "cdn.example.com/a.png",
            "",
        )) {
            assertThrows("must reject $bad", IllegalArgumentException::class.java) {
                MarmotHttpsUrl.normalize(bad)
            }
        }
    }

    @Test
    fun contactSafetyIsNotAValidityQuestion() {
        // "Whether a client contacts or renders the parsed destination is local
        // application policy and MUST NOT affect component or commit validity."
        // A group whose avatar points at localhost is still a valid group.
        for (raw in listOf(
            "https://localhost/avatar.png",
            "https://127.0.0.1/avatar.png",
            "https://10.0.0.1/avatar.png",
            "https://[::1]/avatar.png",
        )) {
            MarmotHttpsUrl.normalize(raw)
        }
        assertTrue(MarmotHttpsUrl.isSafeToContact("https://cdn.example.com/a.png"))
        assertTrue(!MarmotHttpsUrl.isSafeToContact("https://localhost/a.png"))
        assertTrue(!MarmotHttpsUrl.isSafeToContact("https://127.0.0.1/a.png"))
        assertTrue(!MarmotHttpsUrl.isSafeToContact("https://10.0.0.1/a.png"))
        assertTrue(!MarmotHttpsUrl.isSafeToContact("https://192.168.1.1/a.png"))
        assertTrue(!MarmotHttpsUrl.isSafeToContact("https://[::1]/a.png"))
    }

    @Test
    fun aHostThatWouldNeedIdnaIsRefusedRatherThanGuessedAt() {
        // We do not implement IDNA/punycode, so we cannot produce the encoded
        // form a peer expects. Refusing at the producer is safe; it costs
        // nothing on decode, because a conformant producer already stored
        // punycode and a raw Unicode host is non-normalized anyway.
        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                MarmotHttpsUrl.normalize("https://bücher.example/a.png")
            }
        assertTrue(failure.message.orEmpty().contains("punycode"))
    }

    @Test
    fun stateRoundTripsWithAndWithoutHints() {
        val full =
            GroupAvatarUrlV1(
                url = "https://cdn.example.com/avatar.png",
                dim = "512x512".encodeToByteArray(),
                thumbhash = byteArrayOf(1, 2, 3),
            )
        assertEquals(full, GroupAvatarUrlV1.decode(full.encode()))

        val urlOnly = GroupAvatarUrlV1(url = "https://cdn.example.com/avatar.png")
        assertEquals(urlOnly, GroupAvatarUrlV1.decode(urlOnly.encode()))

        val absent = GroupAvatarUrlV1.ABSENT
        assertEquals(absent, GroupAvatarUrlV1.decode(absent.encode()))
        assertTrue(GroupAvatarUrlV1.decode(absent.encode()).isAbsent)
    }

    @Test
    fun anAbsentAvatarCannotCarryHints() {
        assertThrows(IllegalArgumentException::class.java) {
            GroupAvatarUrlV1(url = "", dim = "512x512".encodeToByteArray()).encode()
        }
        // …and the same state rejected on the way in, hand-built.
        val writer = TlsWriter()
        writer.putOpaqueVarInt(ByteArray(0))
        writer.putOpaqueVarInt("512x512".encodeToByteArray())
        writer.putOpaqueVarInt(ByteArray(0))
        assertThrows(IllegalArgumentException::class.java) { GroupAvatarUrlV1.decode(writer.toByteArray()) }
    }

    @Test
    fun decodeRejectsAStoredUrlThatIsNotNormalized() {
        val writer = TlsWriter()
        writer.putOpaqueVarInt("https://CDN.EXAMPLE.COM/a.png".encodeToByteArray())
        writer.putOpaqueVarInt(ByteArray(0))
        writer.putOpaqueVarInt(ByteArray(0))
        val failure = assertThrows(IllegalArgumentException::class.java) { GroupAvatarUrlV1.decode(writer.toByteArray()) }
        assertTrue(failure.message.orEmpty().contains("normalized"))
    }

    @Test
    fun decodeRejectsTrailingBytes() {
        val encoded = GroupAvatarUrlV1(url = "https://cdn.example.com/a.png").encode()
        assertThrows(IllegalArgumentException::class.java) {
            GroupAvatarUrlV1.decode(encoded + byteArrayOf(0))
        }
    }

    @Test
    fun theBoundsAreEnforcedOnBothFields() {
        val longPath = "https://cdn.example.com/" + "a".repeat(2100)
        assertThrows(IllegalArgumentException::class.java) { MarmotHttpsUrl.normalize(longPath) }
        assertThrows(IllegalArgumentException::class.java) {
            GroupAvatarUrlV1(url = "https://cdn.example.com/a.png", dim = ByteArray(257)).encode()
        }
        assertThrows(IllegalArgumentException::class.java) {
            GroupAvatarUrlV1(url = "https://cdn.example.com/a.png", thumbhash = ByteArray(257)).encode()
        }
    }

    @Test
    fun aHintTheRendererCannotReadIsNotAValidityProblem() {
        // "A hint the renderer cannot interpret is treated as absent and MUST
        // NOT invalidate otherwise-valid group state."
        val weird =
            GroupAvatarUrlV1(
                url = "https://cdn.example.com/a.png",
                dim = byteArrayOf(0xff.toByte(), 0xfe.toByte()),
                thumbhash = byteArrayOf(0x00),
            )
        val decoded = GroupAvatarUrlV1.decode(weird.encode())
        assertEquals(weird, decoded)
        assertEquals(null, decoded.dimensions)
    }

    @Test
    fun dimensionsAreParsedOnlyWhenTheyAreTheConventionalShape() {
        assertEquals(
            512 to 512,
            GroupAvatarUrlV1("https://cdn.example.com/a.png", dim = "512x512".encodeToByteArray()).dimensions,
        )
        assertEquals(
            null,
            GroupAvatarUrlV1("https://cdn.example.com/a.png", dim = "not-a-size".encodeToByteArray()).dimensions,
        )
    }
}
