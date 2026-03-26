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
package com.vitorpamplona.quartz.nip77Negentropy

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Callback interface for negentropy reconciliation results.
 */
interface INegentropyListener {
    fun onHaveIds(
        relay: NormalizedRelayUrl,
        subId: String,
        haveIds: List<String>,
    )

    fun onNeedIds(
        relay: NormalizedRelayUrl,
        subId: String,
        needIds: List<String>,
    )

    fun onComplete(
        relay: NormalizedRelayUrl,
        subId: String,
    )

    fun onError(
        relay: NormalizedRelayUrl,
        subId: String,
        reason: String,
    )
}

/**
 * Manages NIP-77 negentropy sync sessions across multiple relays.
 *
 * This class acts as a [IRelayClientListener] that intercepts NEG-MSG and NEG-ERR
 * messages and drives the reconciliation protocol. It maintains active sessions
 * per relay and subscription ID.
 *
 * Usage:
 * 1. Register this as a listener on the NostrClient
 * 2. Call [startSync] with a filter and local events to begin reconciliation
 * 3. Receive results via [INegentropyListener] callbacks
 */
class NegentropyManager(
    private val listener: INegentropyListener,
) : IRelayClientListener {
    private val activeSessions = mutableMapOf<String, Pair<NormalizedRelayUrl, NegentropySession>>()

    fun startSync(
        relay: IRelayClient,
        subId: String,
        filter: Filter,
        localEvents: List<Event>,
        frameSizeLimit: Long = 0,
    ) {
        val session = NegentropySession(subId, filter, localEvents, frameSizeLimit)
        activeSessions[subId] = Pair(relay.url, session)

        val openCmd = session.open()
        relay.sendOrConnectAndSync(openCmd)
    }

    fun closeSync(
        relay: IRelayClient,
        subId: String,
    ) {
        val (_, session) = activeSessions.remove(subId) ?: return
        relay.sendIfConnected(session.close())
    }

    override fun onIncomingMessage(
        relay: IRelayClient,
        msgStr: String,
        msg: Message,
    ) {
        when (msg) {
            is NegMsgMessage -> handleNegMsg(relay, msg)
            is NegErrMessage -> handleNegErr(relay, msg)
        }
    }

    private fun handleNegMsg(
        relay: IRelayClient,
        msg: NegMsgMessage,
    ) {
        val (relayUrl, session) = activeSessions[msg.subId] ?: return

        val result = session.processMessage(msg.message)

        if (result.haveIds.isNotEmpty()) {
            listener.onHaveIds(relayUrl, msg.subId, result.haveIds)
        }
        if (result.needIds.isNotEmpty()) {
            listener.onNeedIds(relayUrl, msg.subId, result.needIds)
        }

        if (result.nextCmd != null) {
            relay.sendIfConnected(result.nextCmd)
        } else {
            activeSessions.remove(msg.subId)
            relay.sendIfConnected(session.close())
            listener.onComplete(relayUrl, msg.subId)
        }
    }

    private fun handleNegErr(
        relay: IRelayClient,
        msg: NegErrMessage,
    ) {
        val (relayUrl, _) = activeSessions.remove(msg.subId) ?: return
        listener.onError(relayUrl, msg.subId, msg.reason)
    }

    override fun onDisconnected(relay: IRelayClient) {
        val toRemove = activeSessions.filter { it.value.first == relay.url }
        toRemove.forEach { (subId, pair) ->
            activeSessions.remove(subId)
            listener.onError(pair.first, subId, "closed: relay disconnected")
        }
    }
}
