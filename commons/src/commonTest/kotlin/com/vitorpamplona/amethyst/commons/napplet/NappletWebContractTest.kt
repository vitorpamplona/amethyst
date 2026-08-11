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

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NappletWebContractTest {
    @Test
    fun lockedPreludeIsTheFirstHeadContentAndRunsBeforeAuthoredCode() {
        val authored = "<html><head><script id=\"authored\">run()</script></head><body></body></html>"
        val injected =
            NappletWebContract
                .injectPrelude(
                    html = authored.encodeToByteArray(),
                    shimJs = "window.__shimRan=true;",
                    declaredDomains = listOf("identity", "relay", "shell", "Relay", "bad\"domain", "relay"),
                    locked = true,
                ).decodeToString()

        val head = injected.indexOf("<head>") + "<head>".length
        val csp = injected.indexOf("<meta http-equiv=\"Content-Security-Policy\"")
        val domains = injected.indexOf("window.__nappletDomains=[\"identity\", \"relay\"]")
        val shim = injected.indexOf("window.__shimRan=true")
        val authoredScript = injected.indexOf("id=\"authored\"")

        assertTrue(head == csp, "the CSP meta must be the first element in head")
        assertTrue(csp < domains && domains < shim && shim < authoredScript)
        assertContains(injected, "window.__nappletNip07=false")
        assertFalse(injected.contains("\"shell\""))
        assertFalse(injected.contains("bad\"domain"))
    }

    @Test
    fun lockedCspMatchesTheConservativeOpaqueOriginPosture() {
        val csp = NappletWebContract.APP_CSP

        assertContains(csp, "default-src 'none'")
        assertContains(csp, "connect-src 'none'")
        assertContains(csp, "frame-src 'none'")
        assertContains(csp, "object-src 'none'")
        assertContains(csp, "base-uri 'none'")
        assertContains(csp, "form-action 'none'")
        assertFalse(csp.contains("'self'"))
    }

    @Test
    fun shellTemplateKeepsTheOpaqueIframeAndSourceChecks() =
        runTest {
            val shell = NappletWebContract.shellHtml().decodeToString()

            assertContains(shell, "sandbox=\"${NappletWebContract.APP_SANDBOX_PLACEHOLDER}\"")
            assertContains(shell, NappletWebContract.APP_BOOTSTRAP_PLACEHOLDER)
            assertContains(shell, "e.source !== iframe.contentWindow")
            assertContains(shell, "iframe.contentWindow.postMessage(msg, '*')")
            assertFalse(shell.contains("allow-same-origin"))
        }
}
