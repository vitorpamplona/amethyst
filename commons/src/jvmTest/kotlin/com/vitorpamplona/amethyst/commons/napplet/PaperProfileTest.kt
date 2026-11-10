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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.amethyst.commons.napplet.blobs.PaperFiles
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The front page's posture on the shared site server both hosts use. */
class PaperProfileTest {
    private val page = "<!doctype html><html><body><h1>The Nostr Observer</h1></body></html>"
    private val origin = NappletWebContract.appOrigin(NappletWebContract.appId("a".repeat(64), NappletLaunch.PAPER_IDENTIFIER, profile = HostProfile.PAPER), HostProfile.PAPER)
    private val shellHtml = "<!doctype html><html><head></head><body>__APP_SANDBOX__ __APP_BOOTSTRAP__</body></html>".encodeToByteArray()

    private val server =
        NappletSiteServer(
            blobs = PaperFiles(page.encodeToByteArray()),
            shellHtml = shellHtml,
            shimJs = "/*shim*/",
            appOrigin = origin,
            profile = HostProfile.PAPER,
            declaredDomains = emptyList(),
        )

    @Test
    fun aPaperAsksForNothingAndRunsNoScripts() {
        assertTrue(HostProfile.PAPER.declaredCapabilities(listOf("storage", "relay")).isEmpty())
        assertFalse(HostProfile.PAPER.runsScripts)
        assertFalse(HostProfile.PAPER.injectsNip07)
        assertTrue(HostProfile.PAPER.opensLinks)
        assertTrue(HostProfile.PAPER.allowsOffOrigin)
        assertFalse(HostProfile.PAPER.appCsp!!.contains("script-src"))
        assertTrue(HostProfile.PAPER.appCsp!!.contains("default-src 'none'"))
    }

    @Test
    fun theShellFramesThePageWithoutScripts() =
        runBlocking {
            val shell = server.serve("GET", NappletWebContract.SHELL_URL, acceptsHtml = true)!!.readBody().decodeToString()
            assertFalse(shell.contains("allow-scripts"))
            assertTrue(shell.contains("allow-popups allow-popups-to-escape-sandbox"))
            assertTrue(shell.contains("iframe.src = '$origin/index.html'"))
        }

    @Test
    fun thePageIsServedAsWrittenUnderItsCsp() =
        runBlocking {
            val response = server.serve("GET", "$origin/index.html", acceptsHtml = true)!!
            assertEquals(page, response.readBody().decodeToString())
            assertEquals(NappletWebContract.PAPER_CSP, response.headers["Content-Security-Policy"])
            assertEquals(404, server.serve("GET", "$origin/other.js", acceptsHtml = false)?.status)
        }

    @Test
    fun hotlinkedArtIsTheBrowsersToFetch() =
        runBlocking {
            assertNull(server.serve("GET", "https://img.example/sun.jpg", acceptsHtml = false))
        }

    @Test
    fun aLaunchCarriesThePageAndKeepsItInline() {
        val launch = NappletLaunch.forPaper("a".repeat(64), "The Nostr Observer", page).inline("front-page-X")
        assertEquals(HostProfile.PAPER, launch.profile)
        assertEquals(page, launch.paper?.decodeToString())
        assertTrue(launch.identity.inline)
        assertTrue(launch.paths.isEmpty())
    }
}
