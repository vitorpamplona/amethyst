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
package com.vitorpamplona.amethyst.napplethost

import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.amethyst.commons.napplet.resolveRequiredCapabilities

/**
 * The security posture a sandbox host renders under — the single decision that "locked nApplet vs
 * open nSite" actually *is*. Replaces the old scattered `websiteMode` boolean: resolved once in the
 * trusted main process, carried over the Intent/Messenger boundary as [name], and every coupled
 * consequence (capabilities, CSP, NIP-07, off-origin, network UI) reads from here — so they can't
 * drift out of sync, and a third posture is one new constant instead of several edited call sites.
 */
enum class HostProfile {
    /** NIP-5D napplet: locked sandbox — declared-only capabilities, strict app CSP, no off-origin, pinned to Tor. */
    NAPPLET,

    /** NIP-5A nSite ("website mode"): a normal web app — NIP-07 provider, no app CSP, off-origin + per-site Tor toggle. */
    WEBSITE,
    ;

    /**
     * What this posture is allowed to ask the broker for — THE security decision, minted into the
     * launch token in the trusted main process. A website gets the IDENTITY + RELAY pair NIP-07 needs
     * (consent-gated); a locked napplet gets only what its manifest `requires` declares.
     */
    fun declaredCapabilities(requires: List<String>): Set<NappletCapability> =
        when (this) {
            WEBSITE -> setOf(NappletCapability.IDENTITY, NappletCapability.RELAY)
            NAPPLET -> resolveRequiredCapabilities(requires).capabilities.toSet()
        }

    /** Strict app CSP for a locked applet (connect-src 'none', etc.); null for a website, which sets its own. */
    val appCsp: String?
        get() = if (this == NAPPLET) NappletWebContract.APP_CSP else null

    /** Install the NIP-07 `window.nostr` provider before the shim runs. */
    val injectsNip07: Boolean get() = this == WEBSITE

    /** Let off-origin requests reach the network (a locked applet 404s them — its CSP is connect-src 'none'). */
    val allowsOffOrigin: Boolean get() = this == WEBSITE

    /**
     * Apply the WebView SOCKS proxy and expose the Tor/network toggle. A website can re-route over Tor
     * or the open web; a locked napplet is pinned to Tor with no toggle.
     */
    val exposesNetwork: Boolean get() = this == WEBSITE

    companion object {
        /** Reconstruct from the wire [name], defaulting to the locked [NAPPLET] posture on anything unknown. */
        fun fromName(name: String?): HostProfile = entries.firstOrNull { it.name == name } ?: NAPPLET
    }
}
