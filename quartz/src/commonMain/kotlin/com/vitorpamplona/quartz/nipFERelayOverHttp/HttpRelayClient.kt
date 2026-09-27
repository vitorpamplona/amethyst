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
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.toHttp
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent

/**
 * NIP-FE client: one relay command per request, POSTed to the relay's URL as the frame the
 * websocket would carry, its answer read line by line as the relay writes it, in the socket's own
 * frames. Nothing stays open after a call returns. [transport] carries the bytes (OkHttp on
 * JVM/Android: `OkHttpRelayTransport`), as a websocket builder does for the NostrClient.
 *
 * With a [signer], a request the relay refuses with 401 goes once more carrying a NIP-98 token for
 * its exact body, as a websocket client answers a NIP-42 challenge; without one, the 401 is the
 * answer. [signFirst] sends the token with the first try instead, saving the round trip against a
 * relay known to want it.
 */
class HttpRelayClient(
    private val transport: HttpRelayTransport,
    private val signer: NostrSigner? = null,
    private val signFirst: Boolean = false,
) {
    /** Stored events matching [filters], each to [onEvent] as it arrives. Complete only if it ended on EOSE or CLOSED. */
    suspend fun req(
        relay: NormalizedRelayUrl,
        filters: List<Filter>,
        subId: String = newSubId(),
        onEvent: (Event) -> Unit,
    ): HttpRelayAnswer =
        send(relay, ReqCmd(subId, filters)) {
            if (it is EventMessage) onEvent(it.event)
        }

    /** NIP-45: [HttpRelayAnswer.last] is the COUNT, or the refusal. */
    suspend fun count(
        relay: NormalizedRelayUrl,
        filters: List<Filter>,
        queryId: String = newSubId(),
    ): HttpRelayAnswer = send(relay, CountCmd(queryId, filters))

    /** Publishes [event]: [HttpRelayAnswer.last] is its OK, or the refusal. */
    suspend fun publish(
        relay: NormalizedRelayUrl,
        event: Event,
    ): HttpRelayAnswer = send(relay, EventCmd(event))

    /** Posts [cmd] (a REQ, COUNT or EVENT) to [relay], handing every frame to [onMessage] as it is read. */
    suspend fun send(
        relay: NormalizedRelayUrl,
        cmd: Command,
        onMessage: (Message) -> Unit = {},
    ): HttpRelayAnswer {
        val command = requireNotNull(HttpRelayCommand.of(cmd)) { "NIP-FE carries REQ, COUNT and EVENT, not ${cmd.label()}" }
        val url = relay.toHttp()
        val body = cmd.toJson().encodeToByteArray()
        if (signer == null || signFirst) return post(relay, command, url, body, token(url, body), onMessage, retrying = false)
        val first = post(relay, command, url, body, null, onMessage, retrying = true)
        if (first.status != HttpRelayStatus.UNAUTHORIZED) return first
        return post(relay, command, url, body, token(url, body), onMessage, retrying = false)
    }

    private suspend fun token(
        url: String,
        body: ByteArray,
    ): String? = signer?.sign(HTTPAuthorizationEvent.build(url, "POST", body))?.toAuthToken()

    private suspend fun post(
        relay: NormalizedRelayUrl,
        command: HttpRelayCommand,
        url: String,
        body: ByteArray,
        authorization: String?,
        onMessage: (Message) -> Unit,
        /** A signed try follows a 401, so that refusal is not the answer and is not handed on. */
        retrying: Boolean,
    ): HttpRelayAnswer {
        var reader: HttpRelayAnswerReader? = null
        var retryAfter: String? = null
        var deliver = true
        transport.post(
            relay = relay,
            url = url,
            body = body,
            authorization = authorization,
            onStatus = { status, after ->
                reader = HttpRelayAnswerReader(command, status)
                retryAfter = after
                deliver = !(retrying && status == HttpRelayStatus.UNAUTHORIZED)
            },
            onLine = { line ->
                val message = checkNotNull(reader) { "a line before the status" }.read(line)
                if (message != null && deliver) onMessage(message)
            },
        )
        return checkNotNull(reader) { "the transport returned without a status" }.answer(retryAfter)
    }
}
