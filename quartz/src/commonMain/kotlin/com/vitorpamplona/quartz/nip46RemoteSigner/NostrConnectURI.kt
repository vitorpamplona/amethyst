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
package com.vitorpamplona.quartz.nip46RemoteSigner

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.Hex

/**
 * KMP-safe parsing and building of the two NIP-46 pairing URIs:
 *
 *  - `bunker://<remote-signer-pubkey>?relay=…&secret=…` — the **signer**
 *    advertises how to reach it (Amethyst mints this when it acts as a bunker).
 *  - `nostrconnect://<client-pubkey>?relay=…&secret=…&perms=…&name=…&url=…&image=…`
 *    — the **client** offers to connect and the signer answers with a connect
 *    ack that echoes the secret (Amethyst parses this when a user pastes an
 *    offer to pair a new app).
 *
 * Values are percent-encoded per the spec/nak convention (`relay=wss%3A%2F%2F…`);
 * this object encodes/decodes without any JVM-only `java.net.URLEncoder`, so it
 * lives in `commonMain` and is shared by the CLI, desktop and Android.
 */
object NostrConnectURI {
    const val BUNKER_SCHEME = "bunker://"
    const val NOSTRCONNECT_SCHEME = "nostrconnect://"

    /** A parsed `bunker://` advertisement. */
    data class Bunker(
        val remoteSignerPubKey: HexKey,
        val relays: Set<NormalizedRelayUrl>,
        val secret: String?,
    )

    /** A parsed `nostrconnect://` client offer. */
    data class NostrConnect(
        val clientPubKey: HexKey,
        val relays: Set<NormalizedRelayUrl>,
        val secret: String,
        val perms: String? = null,
        val name: String? = null,
        val url: String? = null,
        val image: String? = null,
    )

    /** Parse a `bunker://<pubkey>?relay=…&secret=…` URI, or `null` if malformed. */
    fun parseBunker(uri: String): Bunker? {
        if (!uri.startsWith(BUNKER_SCHEME)) return null
        val (pubkey, params) = splitAuthority(uri.removePrefix(BUNKER_SCHEME)) ?: return null
        if (!isValidPubKey(pubkey)) return null
        val relays = mutableSetOf<NormalizedRelayUrl>()
        var secret: String? = null
        forEachParam(params) { key, value ->
            when (key) {
                "relay" -> RelayUrlNormalizer.normalizeOrNull(value)?.let { relays.add(it) }
                "secret" -> secret = value
            }
        }
        return Bunker(pubkey, relays, secret)
    }

    /** Build a `bunker://<pubkey>?relay=…&secret=…` advertisement URI. */
    fun buildBunker(
        remoteSignerPubKey: HexKey,
        relays: Collection<NormalizedRelayUrl>,
        secret: String?,
    ): String =
        buildString {
            append(BUNKER_SCHEME).append(remoteSignerPubKey)
            append('?').append(relays.joinToString("&") { "relay=${encode(it.url)}" })
            if (secret != null) append("&secret=").append(encode(secret))
        }

    /** Parse a `nostrconnect://<client-pubkey>?relay=…&secret=…&…` offer, or `null` if malformed. */
    fun parseNostrConnect(uri: String): NostrConnect? {
        if (!uri.startsWith(NOSTRCONNECT_SCHEME)) return null
        val (pubkey, params) = splitAuthority(uri.removePrefix(NOSTRCONNECT_SCHEME)) ?: return null
        if (!isValidPubKey(pubkey)) return null
        val relays = mutableSetOf<NormalizedRelayUrl>()
        var secret: String? = null
        var perms: String? = null
        var name: String? = null
        var url: String? = null
        var image: String? = null
        forEachParam(params) { key, value ->
            when (key) {
                "relay" -> RelayUrlNormalizer.normalizeOrNull(value)?.let { relays.add(it) }
                "secret" -> secret = value
                "perms" -> perms = value
                "name" -> name = value
                "url" -> url = value
                "image" -> image = value
            }
        }
        val validSecret = secret ?: return null
        return NostrConnect(pubkey.lowercase(), relays, validSecret, perms, name, url, image)
    }

    /** Build a `nostrconnect://<client-pubkey>?relay=…&secret=…&…` offer URI. */
    fun buildNostrConnect(
        clientPubKey: HexKey,
        relays: Collection<NormalizedRelayUrl>,
        secret: String,
        perms: String? = null,
        name: String? = null,
        url: String? = null,
        image: String? = null,
    ): String =
        buildString {
            append(NOSTRCONNECT_SCHEME).append(clientPubKey)
            append('?').append(relays.joinToString("&") { "relay=${encode(it.url)}" })
            append("&secret=").append(encode(secret))
            if (perms != null) append("&perms=").append(encode(perms))
            if (name != null) append("&name=").append(encode(name))
            if (url != null) append("&url=").append(encode(url))
            if (image != null) append("&image=").append(encode(image))
        }

    private fun isValidPubKey(pubkey: String): Boolean = pubkey.length == 64 && Hex.isHex(pubkey)

    /** Splits `<authority>?<query>` into the authority and the raw query (empty when no `?`). */
    private fun splitAuthority(rest: String): Pair<String, String>? {
        val parts = rest.split("?", limit = 2)
        val authority = parts[0]
        if (authority.isEmpty()) return null
        return authority to (parts.getOrNull(1) ?: "")
    }

    private inline fun forEachParam(
        query: String,
        action: (key: String, value: String) -> Unit,
    ) {
        if (query.isEmpty()) return
        for (param in query.split("&")) {
            val kv = param.split("=", limit = 2)
            if (kv.size < 2) continue
            action(kv[0], decode(kv[1]))
        }
    }

    /** Percent-decode a query value (e.g. `wss%3A%2F%2F…` → `wss://…`). */
    fun decode(input: String): String {
        if ('%' !in input) return input
        val bytes = ArrayList<Byte>(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '%' && i + 2 < input.length) {
                val code = input.substring(i + 1, i + 3).toIntOrNull(16)
                if (code != null) {
                    bytes.add(code.toByte())
                    i += 3
                    continue
                }
            }
            for (b in c.toString().encodeToByteArray()) bytes.add(b)
            i++
        }
        return bytes.toByteArray().decodeToString()
    }

    /** Percent-encode a query value; only unreserved `A-Za-z0-9-._~` pass through. */
    fun encode(input: String): String {
        val sb = StringBuilder(input.length)
        for (b in input.encodeToByteArray()) {
            val c = (b.toInt() and 0xFF).toChar()
            if (c.isLetterOrDigit() && c.code < 128 || c in "-._~") {
                sb.append(c)
            } else {
                sb.append('%').append((b.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase())
            }
        }
        return sb.toString()
    }
}
