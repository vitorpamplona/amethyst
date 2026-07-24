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
package com.vitorpamplona.quartz.concord.cord05Invites

import com.vitorpamplona.quartz.concord.cord05Invites.bundle.ConcordInviteBundleEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** The decoded contents of an invite-link URL fragment. */
class InviteFragment(
    /** The 16-byte unlock token (derives the bundle key; never sent to a server). */
    val token: ByteArray,
    /** The resolved bootstrap relay URLs (stock set, or the encoded custom list). */
    val relays: List<String>,
    val usedStockRelays: Boolean,
)

/** A parsed invite-link URL: its addressable pointer plus the private fragment. */
class ParsedInviteLink(
    val naddr: String,
    val linkSignerPubKey: String,
    val kind: Int,
    val fragment: InviteFragment,
)

/**
 * Codec for Concord invite links (CORD-05):
 *
 * ```
 * {base}/invite/{naddr}#{fragment}
 * ```
 *
 * The `naddr` is a public NIP-19 pointer to the kind-33301 bundle
 * `(33301, link_signer_pubkey, d="")`. The `#fragment` is **never sent to any
 * server**: it is base64url of `[version=4][flags][relays?][token:16]`, carrying
 * the 16-byte unlock token (→ [com.vitorpamplona.quartz.concord.crypto
 * .ConcordKeyDerivation.inviteBundleKey]) and, when flag `0x01` is unset, up to
 * three bootstrap relays encoded against [InviteRelayDictionary].
 *
 * Pinned to the Concord v2 reference client for interop.
 */
object ConcordInviteLink {
    const val VERSION = 4
    const val FLAG_STOCK_RELAYS = 0x01
    const val MAX_RELAYS = 3

    private const val MARKER_WSS_HOST = 0
    private const val MARKER_FULL_URL = 255
    private const val WSS_PREFIX = "wss://"
    private const val TOKEN_LEN = 16

    /**
     * Encodes the fragment for [token] and optional [relays]. Passing null or the
     * exact stock set uses flag `0x01` and emits no relay bytes; otherwise up to
     * [MAX_RELAYS] relays are encoded (dictionary id, `wss://` host, or full URL).
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun encodeFragment(
        token: ByteArray,
        relays: List<String>? = null,
    ): String {
        require(token.size == TOKEN_LEN) { "token must be $TOKEN_LEN bytes, was ${token.size}" }
        val out = ArrayList<Byte>(2 + TOKEN_LEN)
        out.add(VERSION.toByte())

        val useStock = relays == null || relays == InviteRelayDictionary.STOCK
        if (useStock) {
            out.add(FLAG_STOCK_RELAYS.toByte())
        } else {
            require(relays.size <= MAX_RELAYS) { "at most $MAX_RELAYS relays, was ${relays.size}" }
            out.add(0)
            out.add(relays.size.toByte())
            for (r in relays) {
                val id = InviteRelayDictionary.idOf(r)
                when {
                    id != null -> out.add(id.toByte())
                    r.startsWith(WSS_PREFIX) -> {
                        val host = r.substring(WSS_PREFIX.length).encodeToByteArray()
                        require(host.size <= 255) { "relay host too long" }
                        out.add(MARKER_WSS_HOST.toByte())
                        out.add(host.size.toByte())
                        host.forEach { out.add(it) }
                    }
                    else -> {
                        val url = r.encodeToByteArray()
                        require(url.size <= 255) { "relay url too long" }
                        out.add(MARKER_FULL_URL.toByte())
                        out.add(url.size.toByte())
                        url.forEach { out.add(it) }
                    }
                }
            }
        }
        token.forEach { out.add(it) }
        return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(out.toByteArray())
    }

    /**
     * Decodes an invite [fragment]. Throws for a malformed fragment or a version
     * other than [VERSION] (lower = legacy, higher = newer than this client).
     * Unknown dictionary ids are skipped rather than aborting the parse.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun decodeFragment(fragment: String): InviteFragment {
        val bytes = Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL).decode(fragment)
        require(bytes.size >= 2 + TOKEN_LEN) { "fragment too short" }
        val version = bytes[0].toInt() and 0xFF
        require(version == VERSION) { "unsupported invite fragment version $version" }
        val flags = bytes[1].toInt() and 0xFF

        var pos = 2
        val relays = ArrayList<String>()
        var usedStock = false
        if (flags and FLAG_STOCK_RELAYS != 0) {
            relays.addAll(InviteRelayDictionary.STOCK)
            usedStock = true
        } else {
            val count = bytes[pos++].toInt() and 0xFF
            repeat(count) {
                val marker = bytes[pos++].toInt() and 0xFF
                when (marker) {
                    MARKER_WSS_HOST -> {
                        val len = bytes[pos++].toInt() and 0xFF
                        relays.add(WSS_PREFIX + bytes.decodeToString(pos, pos + len))
                        pos += len
                    }
                    MARKER_FULL_URL -> {
                        val len = bytes[pos++].toInt() and 0xFF
                        relays.add(bytes.decodeToString(pos, pos + len))
                        pos += len
                    }
                    else -> InviteRelayDictionary.urlOf(marker)?.let { relays.add(it) } // unknown id: skip
                }
            }
        }

        require(bytes.size - pos == TOKEN_LEN) { "trailing bytes are not a $TOKEN_LEN-byte token" }
        return InviteFragment(bytes.copyOfRange(pos, pos + TOKEN_LEN), relays, usedStock)
    }

    /** Builds a full shareable invite URL under [base]. */
    fun buildUrl(
        base: String,
        linkSignerPubKey: String,
        token: ByteArray,
        relays: List<String>? = null,
    ): String {
        val naddr = NAddress.create(ConcordInviteBundleEvent.KIND, linkSignerPubKey, "", null)
        val trimmed = base.trimEnd('/')
        return "$trimmed/invite/$naddr#${encodeFragment(token, relays)}"
    }

    /**
     * Parses an invite link back into its pointer + fragment, or null if malformed.
     * Accepts both the full `{base}/invite/{naddr}#{fragment}` URL and the
     * domain-agnostic bare `{naddr}#{fragment}` form produced by [bareForm], so a
     * link stored by one front end still resolves when shared through another.
     */
    fun parseUrl(url: String): ParsedInviteLink? {
        val hash = url.indexOf('#')
        if (hash < 0) return null
        val fragment =
            try {
                decodeFragment(url.substring(hash + 1))
            } catch (_: Exception) {
                return null
            }
        val marker = url.indexOf("/invite/")
        val naddr =
            if (marker >= 0) {
                url.substring(marker + "/invite/".length, hash)
            } else {
                // Bare `<naddr>#<fragment>` — no host, no path.
                url.substring(0, hash)
            }
        val parsed = NAddress.parse(naddr) ?: return null
        if (parsed.kind != ConcordInviteBundleEvent.KIND) return null
        return ParsedInviteLink(naddr, parsed.author, parsed.kind, fragment)
    }

    /**
     * Reduces any invite link to the domain-agnostic bare `{naddr}#{fragment}` form
     * (CORD-05 §2/§3 `invite_ref`), dropping any `https://host/invite/` prefix so a
     * membership anchored through one front end still matches a link shared via
     * another. Returns null if [url] is not a parseable invite link.
     */
    fun bareForm(url: String): String? {
        val parsed = parseUrl(url) ?: return null
        return parsed.naddr + "#" + url.substring(url.indexOf('#') + 1)
    }
}
