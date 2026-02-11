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
package com.vitorpamplona.quartz.nip05DnsIdentifiers

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.CancellationException

data class Nip05KeyInfo(
    val pubkey: HexKey,
    val relays: List<String>,
)

class Nip05Client(
    val fetcher: Nip05Fetcher,
) {
    val parser = Nip05Parser()

    suspend fun verify(
        nip05: Nip05Id,
        hexKey: HexKey,
    ): Boolean {
        val json = fetchNip05Data(nip05)

        val key =
            try {
                parser.parseHexKey(nip05, json)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                throw IllegalStateException("Error Parsing JSON from NIP-05 address $nip05", e)
            }

        return if (key == null) {
            false
        } else {
            key == hexKey
        }
    }

    suspend fun get(nip05: Nip05Id) = parser.parseHexKeyAndRelays(nip05, fetchNip05Data(nip05))

    suspend fun load(nip05: Nip05Id) = parser.parse(fetchNip05Data(nip05))

    suspend fun list(domain: String) = parser.parse(fetchNip05Data(domain))

    suspend fun fetchNip05Data(nip05: Nip05Id): String {
        val url = nip05.toUserUrl()

        return try {
            fetcher.fetch(url)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw IllegalStateException("Error Fetching JSON from NIP-05 address $nip05 at $url", e)
        }
    }

    suspend fun fetchNip05Data(domain: String): String {
        val url = Nip05Id.domainUrl(domain)

        return try {
            fetcher.fetch(url)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw IllegalStateException("Error Fetching JSON from the entire NIP-05 domain $domain at $url", e)
        }
    }
}
