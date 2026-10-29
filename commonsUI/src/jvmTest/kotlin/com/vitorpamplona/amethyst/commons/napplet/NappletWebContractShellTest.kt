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

import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.coroutines.runBlocking
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class NappletWebContractShellTest {
    private val author = "a".repeat(64)
    private val shellHtml = runBlocking { NappletWebResources.shellHtml() }

    @Test
    fun theAppIdKeepsTheOriginAndroidAlwaysDerived() {
        // Moving the derivation must not move a site's origin, or every nSite would lose its storage.
        val before = "n" + sha256("$author:blog".encodeToByteArray()).toHexKey().take(31)
        assertEquals(before, NappletWebContract.appId(author, "blog"))
        assertEquals(32, before.length)
    }

    @Test
    fun aPartitionGivesEachAccountItsOwnOrigin() {
        val alice = NappletWebContract.appId(author, "blog", partition = "alice")
        val bob = NappletWebContract.appId(author, "blog", partition = "bob")
        assertNotEquals(alice, bob)
        assertNotEquals(NappletWebContract.appId(author, "blog"), alice)
    }

    @Test
    fun aNappletRunsItsVerifiedBytesAsAnOpaqueSrcdoc() {
        val origin = NappletWebContract.appOrigin("n1")
        val shell = NappletWebContract.shell(shellHtml, HostProfile.NAPPLET, origin, "<p>game</p>".encodeToByteArray())
        val html = shell.html.decodeToString()
        assertContains(html, "sandbox=\"allow-scripts\"")
        assertContains(html, "iframe.srcdoc=")
        assertContains(html, Base64.getEncoder().encodeToString("<p>game</p>".encodeToByteArray()))
        assertFalse(html.contains("iframe.src = "))
        assertContains(shell.csp, "frame-src 'self';")
    }

    @Test
    fun aWebsiteNavigatesItsOwnOrigin() {
        val origin = NappletWebContract.appOrigin("n1")
        val shell = NappletWebContract.shell(shellHtml, HostProfile.WEBSITE, origin, null)
        val html = shell.html.decodeToString()
        assertContains(html, "sandbox=\"allow-scripts allow-same-origin\"")
        assertContains(html, "iframe.src = '$origin/';")
        assertFalse(html.contains("srcdoc="))
        assertContains(shell.csp, "frame-src $origin;")
    }
}
