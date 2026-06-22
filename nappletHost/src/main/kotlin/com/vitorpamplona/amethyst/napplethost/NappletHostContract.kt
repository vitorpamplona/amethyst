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

/**
 * Intent-extra contract for launching [NappletHostActivity]. Lives in the sandbox module so the
 * activity (here) and the launcher (in `:amethyst`) share the keys without a dependency cycle —
 * `:amethyst` depends on `:nappletHost`, never the other way around.
 */
object NappletHostContract {
    const val EXTRA_PATHS = "napplet_paths"
    const val EXTRA_HASHES = "napplet_hashes"
    const val EXTRA_SERVERS = "napplet_servers"
    const val EXTRA_AUTHOR = "napplet_author"
    const val EXTRA_IDENTIFIER = "napplet_identifier"
    const val EXTRA_AGGREGATE_HASH = "napplet_aggregate_hash"
    const val EXTRA_TITLE = "napplet_title"

    /** Bare NAP capability domains the manifest declared (empty for a plain nSite). */
    const val EXTRA_REQUIRES = "napplet_requires"

    /**
     * Pre-localized capability labels for the "what it can access" sheet, resolved by the launcher
     * (which has the app's resources) so the sandbox module needs no capability string resources.
     */
    const val EXTRA_CAP_LABELS = "napplet_cap_labels"

    /**
     * Unguessable token for this launch. The sandbox relays it to the broker, which resolves it back
     * to the trusted identity + declared capabilities — the sandbox never carries (and so can never
     * forge) its own coordinate.
     */
    const val EXTRA_LAUNCH_TOKEN = "napplet_launch_token"

    /** SOCKS proxy port to route blob fetches through, or -1 for a direct connection. */
    const val EXTRA_PROXY_PORT = "napplet_proxy_port"

    /**
     * nSite "website mode": treat the content as a normal web app — install the NIP-07 `window.nostr`
     * provider and allow normal network (no app CSP). Off for locked napplets.
     */
    const val EXTRA_WEBSITE_MODE = "napplet_website_mode"

    /**
     * FQN of the main-process broker service (in `:amethyst`). The sandbox binds it by name so it
     * needs no compile-time reference to `:amethyst`. Must match the manifest `<service>` declaration.
     */
    const val BROKER_SERVICE_CLASS = "com.vitorpamplona.amethyst.napplet.NappletBrokerService"
}
