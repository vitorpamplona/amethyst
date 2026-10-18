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
package com.vitorpamplona.quartz.nip34Git.repository

/**
 * NIP-34 `nostr://` clone URLs, which `git clone` resolves through a git-remote-nostr
 * helper (ngit): `nostr://<npub>/<identifier>` or, with a relay hint,
 * `nostr://<npub>/<relay-hint>/<identifier>`. The relay hint drops a `wss://` scheme
 * (any other scheme is kept) and both it and the identifier are percent-encoded per
 * RFC 3986, as the spec's examples show.
 */
object NostrCloneUrl {
    fun build(
        npub: String,
        identifier: String,
        relayHint: String? = null,
    ): String {
        val hint =
            relayHint
                ?.trim()
                ?.removePrefix("wss://")
                ?.trimEnd('/')
                ?.ifEmpty { null }
        return if (hint != null) {
            "nostr://$npub/${percentEncode(hint)}/${percentEncode(identifier)}"
        } else {
            "nostr://$npub/${percentEncode(identifier)}"
        }
    }

    /** RFC 3986 percent-encoding of everything but the unreserved set (`A-Z a-z 0-9 - . _ ~`). */
    fun percentEncode(value: String): String {
        val out = StringBuilder()
        for (byte in value.encodeToByteArray()) {
            val c = byte.toInt() and 0xFF
            val char = c.toChar()
            if (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char == '-' || char == '.' || char == '_' || char == '~') {
                out.append(char)
            } else {
                out.append('%')
                out.append(HEX[c shr 4])
                out.append(HEX[c and 0x0F])
            }
        }
        return out.toString()
    }

    private const val HEX = "0123456789ABCDEF"
}
