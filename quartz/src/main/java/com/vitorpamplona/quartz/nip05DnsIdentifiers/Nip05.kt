/**
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

import com.vitorpamplona.quartz.nip01Core.jackson.EventMapper
import kotlinx.coroutines.CancellationException

class Nip05 {
    fun assembleUrl(nip05address: String): String? {
        val parts = nip05address.trim().split("@")

        if (parts.size == 2) {
            return "https://${parts[1]}/.well-known/nostr.json?name=${parts[0]}"
        }
        if (parts.size == 1) {
            return "https://${parts[0]}/.well-known/nostr.json?name=_"
        }

        return null
    }

    fun parseHexKeyFor(
        nip05: String,
        returnBody: String,
    ): Result<String?> {
        // NIP05 usernames are case insensitive, but JSON properties are not
        // converts the json to lowercase and then tries to access the username via a
        // lowercase version of the username.
        val nip05url =
            try {
                EventMapper.mapper.readTree(returnBody.lowercase())
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                return Result.failure(e)
            }

        val parts = nip05.split("@")
        val user =
            if (parts.size == 2) {
                parts[0].lowercase()
            } else {
                "_"
            }

        val hexKey = nip05url?.get("names")?.get(user)?.asText()

        return Result.success(hexKey)
    }
}
