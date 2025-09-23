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

import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
        val parts = nip05.split("@")
        val user =
            if (parts.size == 2) {
                parts[0].lowercase()
            } else {
                "_"
            }

        // NIP05 usernames are case insensitive, but JSON properties are not
        // converts the json to lowercase and then tries to access the username via a
        // lowercase version of the username.
        return try {
            val rootElement = Json.parseToJsonElement(returnBody.lowercase())
            val hexKey =
                rootElement.jsonObject["names"]
                    ?.jsonObject[user]
                    ?.jsonPrimitive
                    ?.content
            Result.success(hexKey)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Log.w("Nip05", "Unable to Parse NIP-05 for $nip05 with $returnBody", e)
            Result.failure(e)
        }
    }
}
