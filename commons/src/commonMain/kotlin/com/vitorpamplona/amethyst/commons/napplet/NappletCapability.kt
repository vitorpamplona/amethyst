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

/**
 * The capability classes a napplet shell can broker, each gating a set of dangerous
 * operations behind the trust boundary. A napplet declares the NAP domains it needs
 * via `requires` tags (`NappletManifest.requires()`); [fromNapDomain] maps each bare
 * domain string to the capability the broker enforces.
 *
 * The mapping is intentionally **default-deny**: an unrecognized NAP domain maps to
 * `null` and the shell must surface it as unknown rather than silently granting it.
 */
enum class NappletCapability {
    /** Read the active pubkey, sign events, and NIP-04/44 encrypt/decrypt as the user. */
    IDENTITY,

    /** Publish events to, and read events from, the user's relays. */
    RELAY,

    /** Request NIP-57 zaps / Lightning invoices (and, behind a stricter grant, NWC pay). */
    WALLET,

    /** A per-applet sandboxed key-value store, namespaced by [NappletIdentity] — never app storage. */
    STORAGE,

    /** Direct outbound network to user-approved origins (widens the applet's CSP `connect-src`). */
    NET,
    ;

    companion object {
        /**
         * Maps a bare NAP domain (e.g. `identity`, `relay`, `storage`) to the capability the
         * broker enforces, case-insensitively. Returns `null` for any domain the shell does
         * not recognize — callers MUST treat that as "unknown, do not grant".
         */
        fun fromNapDomain(domain: String): NappletCapability? =
            when (domain.trim().lowercase()) {
                "identity", "sign", "signer" -> IDENTITY
                "relay", "relays" -> RELAY
                "value", "wallet", "zap", "payments" -> WALLET
                "storage" -> STORAGE
                "net", "network", "fetch" -> NET
                else -> null
            }
    }
}

/** A NAP domain the manifest declared that this shell does not recognize. */
data class UnknownNapDomain(
    val domain: String,
)

/** Outcome of resolving a manifest's `requires` list against the capabilities this shell brokers. */
data class NappletRequiredCapabilities(
    val capabilities: Set<NappletCapability>,
    val unknown: List<UnknownNapDomain>,
) {
    val hasUnknown: Boolean get() = unknown.isNotEmpty()
}

/**
 * Resolves a manifest's `requires` domains into the [NappletCapability] set this shell can
 * broker plus the list of domains it does not recognize (which the user must be warned about).
 */
fun resolveRequiredCapabilities(napDomains: List<String>): NappletRequiredCapabilities {
    val caps = mutableSetOf<NappletCapability>()
    val unknown = mutableListOf<UnknownNapDomain>()
    for (domain in napDomains) {
        val cap = NappletCapability.fromNapDomain(domain)
        if (cap != null) caps.add(cap) else unknown.add(UnknownNapDomain(domain))
    }
    return NappletRequiredCapabilities(caps, unknown)
}
