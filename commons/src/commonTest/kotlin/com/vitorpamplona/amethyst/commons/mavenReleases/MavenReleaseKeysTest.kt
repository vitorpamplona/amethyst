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
package com.vitorpamplona.amethyst.commons.mavenReleases

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip05DnsIdentifiers.INip05Client
import com.vitorpamplona.quartz.nip05DnsIdentifiers.KeyInfoSet
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05FetchException
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Id
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05KeyInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Stands in for java.net.UnknownHostException, which common code recognises by name. */
private class UnknownHostException : Exception("no such host")

/** Answers `releases@host` from [answers]: a key, null (not listed), or an exception to throw. */
private class FakeNip05(
    val answers: Map<String, Any?>,
) : INip05Client {
    val asked = mutableListOf<String>()

    override suspend fun get(nip05: Nip05Id): Nip05KeyInfo? {
        asked.add(nip05.domain)
        return when (val answer = answers[nip05.domain]) {
            is Throwable -> throw IllegalStateException("Error Fetching JSON", answer)
            is String -> Nip05KeyInfo(answer, emptyList())
            else -> null
        }
    }

    override suspend fun verify(
        nip05: Nip05Id,
        hexKey: HexKey,
    ) = get(nip05)?.pubkey == hexKey

    override suspend fun load(nip05: Nip05Id): KeyInfoSet? = null

    override suspend fun list(domain: String) = KeyInfoSet(emptyMap(), emptyMap())
}

class MavenReleaseKeysTest {
    @Test
    fun walksFromTheGroupDownToItsDomain() {
        assertEquals(listOf("quartz.vitorpamplona.com", "vitorpamplona.com"), MavenReleaseKeys.hostsFor("com.vitorpamplona.quartz"))
        assertEquals(listOf("vitorpamplona.com"), MavenReleaseKeys.hostsFor("com.vitorpamplona"))
        assertEquals(listOf("lib.example.com", "example.com"), MavenReleaseKeys.hostsFor("com.Example.Lib"))
        assertEquals(emptyList(), MavenReleaseKeys.hostsFor("com.my_company.lib"))
        assertEquals(emptyList(), MavenReleaseKeys.hostsFor("local"))
    }

    @Test
    fun aMissingSubdomainMovesOnToTheDomain() =
        runTest {
            val client = FakeNip05(mapOf("crypto.vitorpamplona.com" to UnknownHostException(), "vitorpamplona.com" to SIGNER))
            val lookup = MavenReleaseKeys.lookup("com.vitorpamplona.crypto", client)
            assertEquals(ReleaseKeyLookup.Found("vitorpamplona.com", SIGNER), lookup)
            assertEquals(ReleaseKeyVerdict.VERIFIED, MavenReleaseKeys.verdict(lookup, SIGNER.uppercase()))
            assertEquals(ReleaseKeyVerdict.OTHER_KEY, MavenReleaseKeys.verdict(lookup, "aa".repeat(32)))
        }

    @Test
    fun aNotFoundOrUnlistedHostMovesOn() =
        runTest {
            val client = FakeNip05(mapOf("crypto.vitorpamplona.com" to Nip05FetchException.Status(404)))
            val lookup = MavenReleaseKeys.lookup("com.vitorpamplona.crypto", client)
            assertEquals(ReleaseKeyLookup.None("vitorpamplona.com"), lookup)
            assertEquals(listOf("crypto.vitorpamplona.com", "vitorpamplona.com"), client.asked)
            assertEquals(ReleaseKeyVerdict.NO_KEY, MavenReleaseKeys.verdict(lookup, SIGNER))
        }

    @Test
    fun aParentThatNamesTheSignerPastAnUnreadableHostVerifies() =
        runTest {
            // A proxy or Tor turns a missing subdomain into an unreadable one.
            val client = FakeNip05(mapOf("crypto.vitorpamplona.com" to Nip05FetchException.Status(502), "vitorpamplona.com" to SIGNER))
            val lookup = MavenReleaseKeys.lookup("com.vitorpamplona.crypto", client)
            assertEquals(ReleaseKeyLookup.Found("vitorpamplona.com", SIGNER, skipped = "crypto.vitorpamplona.com"), lookup)
            assertEquals(ReleaseKeyVerdict.VERIFIED, MavenReleaseKeys.verdict(lookup, SIGNER))
        }

    @Test
    fun anotherKeyPastAnUnreadableHostIsNoProofOfAnImpostor() =
        runTest {
            val client = FakeNip05(mapOf("crypto.vitorpamplona.com" to Nip05FetchException.Status(503), "vitorpamplona.com" to "bb".repeat(32)))
            val lookup = MavenReleaseKeys.lookup("com.vitorpamplona.crypto", client)
            assertEquals(ReleaseKeyVerdict.UNKNOWN, MavenReleaseKeys.verdict(lookup, SIGNER))
        }

    @Test
    fun noKeyPastAnUnreadableHostIsUnknown() =
        runTest {
            val client = FakeNip05(mapOf("crypto.vitorpamplona.com" to Nip05FetchException.Status(503)))
            val lookup = MavenReleaseKeys.lookup("com.vitorpamplona.crypto", client)
            assertEquals(ReleaseKeyLookup.Unreachable("crypto.vitorpamplona.com"), lookup)
            assertEquals(ReleaseKeyVerdict.UNKNOWN, MavenReleaseKeys.verdict(lookup, SIGNER))
        }

    @Test
    fun cardsOfOneGroupShareOneWalk() =
        runTest {
            val client = FakeNip05(mapOf("dedupe.example.org" to SIGNER))
            val results = (1..5).map { async { MavenReleaseKeys.cachedLookup("org.example.dedupe", client) } }.awaitAll()
            results.forEach { assertEquals(ReleaseKeyLookup.Found("dedupe.example.org", SIGNER), it) }
            assertEquals(listOf("dedupe.example.org"), client.asked)
            // Remembered: asking again fetches nothing.
            MavenReleaseKeys.cachedLookup("org.example.dedupe", client)
            assertEquals(1, client.asked.size)
        }

    @Test
    fun theMostSpecificHostWins() =
        runTest {
            val sub = "bb".repeat(32)
            val client = FakeNip05(mapOf("crypto.vitorpamplona.com" to sub, "vitorpamplona.com" to SIGNER))
            assertEquals(ReleaseKeyLookup.Found("crypto.vitorpamplona.com", sub), MavenReleaseKeys.lookup("com.vitorpamplona.crypto", client))
        }
}
