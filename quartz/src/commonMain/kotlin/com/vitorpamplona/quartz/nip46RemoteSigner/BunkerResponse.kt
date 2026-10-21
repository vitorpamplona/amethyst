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

import com.vitorpamplona.quartz.utils.Hex

open class BunkerResponse(
    val id: String,
    val result: String?,
    val error: String?,
) : BunkerMessage() {
    companion object {
        /** NIP-46 auth-challenge marker: `result == "auth_url"`, with the URL carried in `error`. */
        const val RESULT_AUTH_URL = "auth_url"

        /** The typed response a signer's `{id, result, error}` stands for. */
        fun parse(
            id: String,
            result: String?,
            error: String?,
        ): BunkerResponse {
            // NIP-46 auth challenge: `{"result":"auth_url","error":"<url>"}`. It carries
            // an `error` (the URL) but is NOT a failure — keep both fields so the client
            // can surface the URL and keep waiting for the real response. Must precede the
            // generic error branch below, which would otherwise drop the `auth_url` marker.
            if (result == RESULT_AUTH_URL) {
                return BunkerResponse(id, result, error)
            }

            if (error != null) {
                return BunkerResponseError.parse(id, result, error)
            }

            if (result != null) {
                when (result) {
                    BunkerResponseAck.RESULT -> {
                        return BunkerResponseAck.parse(id, result, error)
                    }

                    BunkerResponsePong.RESULT -> {
                        return BunkerResponsePong.parse(id, result, error)
                    }

                    else -> {
                        if (result.length == 64 && Hex.isHex(result)) {
                            return BunkerResponsePublicKey.parse(id, result)
                        }

                        if (result.isNotEmpty() && result[0] == '{') {
                            try {
                                return BunkerResponseEvent.parse(id, result)
                            } catch (_: Exception) {
                            }

                            try {
                                return BunkerResponseGetRelays.parse(id, result)
                            } catch (_: Exception) {
                            }
                        }

                        return BunkerResponse(id, result, error)
                    }
                }
            }

            return BunkerResponse(id, result, error)
        }
    }
}
