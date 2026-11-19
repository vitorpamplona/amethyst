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
package com.vitorpamplona.amethyst.commons.service.image

import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.network.HttpException
import coil3.network.NetworkResponse
import com.vitorpamplona.amethyst.commons.service.http.BlossomAuthorHints
import com.vitorpamplona.amethyst.commons.service.http.BlossomBlobName
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BlossomFallbackFetcherTest {
    private val sha = "4e910deea95eb38d4ca652d43c7c11b210b7c5b07d41898fd974da6b494d4fee"
    private val author = "000000005e9dda01479c76c5f4fccbaebe4e7856e02f8e85adba05ad62ad6927"
    private val url = "https://24242.io/$sha.jpg"
    private val mirror = "https://blossom.primal.net/$sha.jpg"
    private val blob = BlossomBlobName.parse(url)!!

    private fun fetcher(block: suspend () -> FetchResult?): Fetcher =
        object : Fetcher {
            override suspend fun fetch(): FetchResult? = block()
        }

    private fun http(code: Int) = HttpException(NetworkResponse(code = code))

    /** Runs the fetcher with a primary that throws [primaryError] and records what happened. */
    private class Run(
        val primaryError: Exception?,
        val resolved: String?,
        val fallbackError: Exception? = null,
    ) {
        val asked = mutableListOf<String>()
        val fetched = mutableListOf<String>()
    }

    private fun build(
        run: Run,
        url: String = this.url,
    ) = BlossomFallbackFetcher(
        url = url,
        blob = BlossomBlobName.parse(url)!!,
        resolve = { uri ->
            run.asked.add(uri)
            run.resolved
        },
        primary = {
            fetcher {
                run.fetched.add(url)
                run.primaryError?.let { throw it }
                null
            }
        },
        fallback = { other ->
            fetcher {
                run.fetched.add(other)
                run.fallbackError?.let { throw it }
                null
            }
        },
    )

    @Test
    fun aWorkingServerIsNeverAskedAbout() =
        runTest {
            val run = Run(primaryError = null, resolved = mirror)
            assertNull(build(run).fetch())
            assertEquals(listOf(url), run.fetched)
            assertTrue(run.asked.isEmpty())
        }

    @Test
    fun aMissingBlobLoadsFromAnotherServer() =
        runTest {
            val run = Run(primaryError = http(404), resolved = mirror)
            build(run).fetch()
            assertEquals(listOf(url, mirror), run.fetched)
        }

    @Test
    fun theSearchCarriesTheAuthorAndNoServerHint() =
        runTest {
            BlossomAuthorHints.add(url, author)
            val run = Run(primaryError = http(404), resolved = mirror)
            build(run).fetch()

            val asked = BlossomUri.parse(run.asked.single())!!
            assertEquals(sha, asked.sha256)
            assertEquals("jpg", asked.extension)
            assertEquals(listOf(author), asked.authors)
            assertTrue(asked.servers.isEmpty())
        }

    @Test
    fun goneAndBrokenAndUnreachableServersMoveOn() =
        runTest {
            listOf(http(410), http(500), http(503), UnknownHostException("24242.io")).forEach { error ->
                val run = Run(primaryError = error, resolved = mirror)
                build(run).fetch()
                assertEquals(listOf(url, mirror), run.fetched, "after $error")
            }
        }

    @Test
    fun anAnswerAboutAccessIsKept() =
        runTest {
            listOf(401, 402, 403).forEach { code ->
                val error = http(code)
                val run = Run(primaryError = error, resolved = mirror)
                assertSame(error, assertFailsWith<HttpException> { build(run).fetch() })
                assertTrue(run.asked.isEmpty(), "asked after $code")
            }
        }

    @Test
    fun noOtherServerKeepsTheOriginalFailure() =
        runTest {
            val error = http(404)
            val run = Run(primaryError = error, resolved = null)
            assertSame(error, assertFailsWith<HttpException> { build(run).fetch() })
            assertEquals(listOf(url), run.fetched)
        }

    @Test
    fun theSameUrlAgainIsNotRetried() =
        runTest {
            val error = http(404)
            val run = Run(primaryError = error, resolved = url)
            assertSame(error, assertFailsWith<HttpException> { build(run).fetch() })
            assertEquals(listOf(url), run.fetched)
        }

    @Test
    fun aFailingMirrorReportsTheOriginalFailure() =
        runTest {
            val error = http(404)
            val run = Run(primaryError = error, resolved = mirror, fallbackError = http(404))
            val thrown = assertFailsWith<HttpException> { build(run).fetch() }
            assertSame(error, thrown)
            assertEquals(1, thrown.suppressed.size)
        }

    @Test
    fun cancellationIsNotAFailure() =
        runTest {
            val run = Run(primaryError = CancellationException("scrolled away"), resolved = mirror)
            assertFailsWith<CancellationException> { build(run).fetch() }
            assertTrue(run.asked.isEmpty())
        }

    @Test
    fun anExtensionlessBlobIsSearchedAsBin() =
        runTest {
            val bare = "https://24242.io/$sha"
            val run = Run(primaryError = http(404), resolved = mirror)
            build(run, bare).fetch()
            assertEquals("bin", BlossomUri.parse(run.asked.single())!!.extension)
        }

    @Test
    fun onlyBlobNamesAreBlossom() {
        assertEquals(sha, blob.sha256)
        assertEquals("jpg", blob.extension)
        assertEquals(sha, BlossomBlobName.parse("https://cdn.nostr.build/i/${sha.uppercase()}.PNG?x=1#y")!!.sha256)
        assertNull(BlossomBlobName.parse("https://nostr.build/nostr.build_$sha.jpg"))
        assertNull(BlossomBlobName.parse("https://host/list/$sha/more"))
        assertNull(BlossomBlobName.parse("blossom:$sha.jpg"))
        assertNull(BlossomBlobName.parse("https://$sha"))
    }
}
