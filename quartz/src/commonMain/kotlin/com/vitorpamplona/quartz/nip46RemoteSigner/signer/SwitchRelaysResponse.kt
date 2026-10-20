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
package com.vitorpamplona.quartz.nip46RemoteSigner.signer

import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseError
import kotlin.coroutines.cancellation.CancellationException

class SwitchRelaysResponse {
    companion object {
        /** More than any signer needs; a longer list is cut rather than dialed in full. */
        const val MAX_RELAYS = 20

        /**
         * The answer is a JSON-stringified list of relay URLs, or `null` when nothing changed.
         * Entries that are not relay URLs are dropped; a list with none left reads as no change,
         * so a bad answer can't leave the client listening nowhere.
         */
        fun parse(response: BunkerResponse): SignerResult.RequestAddressed<SwitchRelaysResult> {
            if (response is BunkerResponseError) return SignerResult.RequestAddressed.Rejected(response.error)

            val result = response.result?.trim()
            if (result.isNullOrEmpty() || result == "null") {
                return SignerResult.RequestAddressed.Successful(SwitchRelaysResult(null))
            }

            val urls =
                try {
                    JsonMapper.fromJson<List<String?>>(result)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    return SignerResult.RequestAddressed.ReceivedButCouldNotPerform()
                }

            val relays =
                urls
                    .mapNotNull { url -> url?.let { RelayUrlNormalizer.normalizeOrNull(it) } }
                    .distinct()
                    .take(MAX_RELAYS)
                    .toSet()

            return SignerResult.RequestAddressed.Successful(SwitchRelaysResult(relays.ifEmpty { null }))
        }
    }
}
