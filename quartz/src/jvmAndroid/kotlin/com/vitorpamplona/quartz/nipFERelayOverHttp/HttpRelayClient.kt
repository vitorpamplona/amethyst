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
package com.vitorpamplona.quartz.nipFERelayOverHttp

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync
import okio.IOException

/**
 * NIP-FE over OkHttp: one relay command per request, its answer read line by line as the relay
 * writes it. Nothing stays open after a call returns.
 *
 * With a [signer], a request the relay refuses with 401 goes once more carrying a NIP-98 token for
 * its exact body, as a websocket client answers a NIP-42 challenge; without one, the 401 is the
 * answer. [signFirst] sends the token with the first try instead, saving the round trip against a
 * relay known to want it.
 */
class HttpRelayClient(
    private val http: OkHttpClient,
    private val signer: NostrSigner? = null,
    private val signFirst: Boolean = false,
) {
    /** Stored events matching [filters], each to [onEvent] as it arrives. Complete only if it ended on EOSE or CLOSED. */
    suspend fun req(
        relay: NormalizedRelayUrl,
        filters: List<Filter>,
        onEvent: (Event) -> Unit,
    ): HttpRelayAnswer =
        send(relay, HttpRelayCommand.REQ, HttpRelayCommand.body(filters)) {
            if (it is EventMessage) onEvent(it.event)
        }

    /** NIP-45: [HttpRelayAnswer.last] is the COUNT, or the refusal. */
    suspend fun count(
        relay: NormalizedRelayUrl,
        filters: List<Filter>,
    ): HttpRelayAnswer = send(relay, HttpRelayCommand.COUNT, HttpRelayCommand.body(filters))

    /** Publishes [event]: [HttpRelayAnswer.last] is its OK, or the refusal. */
    suspend fun publish(
        relay: NormalizedRelayUrl,
        event: Event,
    ): HttpRelayAnswer = send(relay, HttpRelayCommand.EVENT, event.toJson())

    /** Posts [body] to [command]'s endpoint on [relay], handing every frame to [onMessage] as it is read. */
    suspend fun send(
        relay: NormalizedRelayUrl,
        command: HttpRelayCommand,
        body: String,
        onMessage: (Message) -> Unit = {},
    ): HttpRelayAnswer {
        val url = command.url(relay)
        val bytes = body.encodeToByteArray()
        if (signer == null || signFirst) return post(command, url, bytes, token(url, bytes), onMessage, retrying = false)
        val first = post(command, url, bytes, null, onMessage, retrying = true)
        if (first.status != HttpRelayStatus.UNAUTHORIZED) return first
        return post(command, url, bytes, token(url, bytes), onMessage, retrying = false)
    }

    private suspend fun token(
        url: String,
        body: ByteArray,
    ): String? = signer?.sign(HTTPAuthorizationEvent.build(url, "POST", body))?.toAuthToken()

    private suspend fun post(
        command: HttpRelayCommand,
        url: String,
        body: ByteArray,
        authorization: String?,
        onMessage: (Message) -> Unit,
        /** A signed try follows a 401, so that refusal is not the answer and is not handed on. */
        retrying: Boolean,
    ): HttpRelayAnswer =
        coroutineScope {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .post(body.toRequestBody(JSON))
                    .header("Accept", NDJSON)
                    .apply { authorization?.let { header("Authorization", it) } }
                    .build()
            val call = http.newCall(request)
            // A blocking read does not see coroutine cancellation; cancelling the call unblocks it.
            val watcher =
                launch {
                    try {
                        awaitCancellation()
                    } finally {
                        call.cancel()
                    }
                }
            try {
                call.executeAsync().use { response ->
                    withContext(Dispatchers.IO) {
                        val reader = HttpRelayAnswerReader(command, response.code)
                        val deliver = !(retrying && response.code == HttpRelayStatus.UNAUTHORIZED)
                        val source = response.body.source()
                        try {
                            while (true) {
                                val line = source.readUtf8Line() ?: break
                                val message = reader.read(line)
                                if (message != null && deliver) onMessage(message)
                            }
                        } catch (_: IOException) {
                            // The connection dropped mid-answer: what came is what the reader says it is.
                        }
                        reader.answer(response.header("Retry-After"))
                    }
                }
            } finally {
                watcher.cancel()
            }
        }

    companion object {
        const val NDJSON = "application/x-ndjson"
        private val JSON = "application/json".toMediaType()
    }
}
