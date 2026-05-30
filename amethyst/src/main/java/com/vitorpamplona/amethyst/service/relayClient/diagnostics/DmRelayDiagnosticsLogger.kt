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
package com.vitorpamplona.amethyst.service.relayClient.diagnostics

import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.AuthMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.EphemeralGiftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.Log

/**
 * Diagnostic connection listener for the DM / gift-wrap loading path.
 *
 * It folds the full per-relay timeline — connect, auth challenge, REQ sent,
 * gift-wrap events, EOSE, plus any NOTICE/CLOSED rejection — into the single
 * `DMPagination` log tag with an elapsed-time prefix, so a slow cold boot can be
 * attributed (connection? auth? relay response?) and a silent failure to load
 * (e.g. a relay answering CLOSED "auth-required" / "restricted") becomes visible.
 *
 * Gift-wrap subscriptions are recognised by the kind:1059/1060 filter in the REQ
 * we send, so EOSE/CLOSED for those subscriptions can be singled out from the
 * rest of the app's relay traffic.
 */
class DmRelayDiagnosticsLogger(
    val client: INostrClient,
) {
    private val startMs = System.currentTimeMillis()

    private fun at() = System.currentTimeMillis() - startMs

    // Subscription ids whose REQ carried a gift-wrap kind, so we can attribute their EOSE/CLOSED.
    private val giftWrapSubIds = mutableSetOf<String>()

    private val listener =
        object : RelayConnectionListener {
            override fun onConnecting(relay: IRelayClient) {
                Log.d(TAG) { "[+${at()}ms] connecting ${relay.url.url}" }
            }

            override fun onConnected(
                relay: IRelayClient,
                pingMillis: Int,
                compressed: Boolean,
            ) {
                Log.d(TAG) { "[+${at()}ms] connected ${relay.url.url} (ping ${pingMillis}ms${if (compressed) ", compressed" else ""})" }
            }

            override fun onSent(
                relay: IRelayClient,
                cmdStr: String,
                cmd: Command,
                success: Boolean,
            ) {
                if (!cmdStr.contains("1059") && !cmdStr.contains("1060")) return
                reqSubId(cmdStr)?.let { giftWrapSubIds.add(it) }
                Log.d(TAG) { "[+${at()}ms] REQ -> ${relay.url.url} success=$success ${cmdStr.take(400)}" }
            }

            override fun onIncomingMessage(
                relay: IRelayClient,
                msgStr: String,
                msg: Message,
            ) {
                when (msg) {
                    is AuthMessage ->
                        Log.d(TAG) { "[+${at()}ms] AUTH <- ${relay.url.url} challenge=${msg.challenge.take(12)}…" }

                    is NoticeMessage ->
                        Log.d(TAG) { "[+${at()}ms] NOTICE <- ${relay.url.url} '${msg.message}'" }

                    is ClosedMessage ->
                        if (msg.subId in giftWrapSubIds) {
                            Log.d(TAG) { "[+${at()}ms] CLOSED <- ${relay.url.url} sub=${msg.subId} reason='${msg.message}'" }
                        }

                    is EoseMessage ->
                        if (msg.subId in giftWrapSubIds) {
                            Log.d(TAG) { "[+${at()}ms] EOSE <- ${relay.url.url} sub=${msg.subId}" }
                        }

                    is EventMessage ->
                        if (msg.event.kind == GiftWrapEvent.KIND || msg.event.kind == EphemeralGiftWrapEvent.KIND) {
                            Log.d(TAG) { "[+${at()}ms] EVENT <- ${relay.url.url} kind=${msg.event.kind} sub=${msg.subId} createdAt=${msg.event.createdAt}" }
                        }

                    is OkMessage ->
                        if (!msg.success) {
                            Log.d(TAG) { "[+${at()}ms] OK(fail) <- ${relay.url.url} '${msg.message}'" }
                        }

                    else -> {}
                }
            }

            override fun onDisconnected(relay: IRelayClient) {
                Log.d(TAG) { "[+${at()}ms] disconnected ${relay.url.url}" }
            }

            override fun onCannotConnect(
                relay: IRelayClient,
                errorMessage: String,
            ) {
                Log.d(TAG) { "[+${at()}ms] CANNOT CONNECT ${relay.url.url}: $errorMessage" }
            }
        }

    init {
        client.addConnectionListener(listener)
    }

    fun destroy() {
        client.removeConnectionListener(listener)
    }

    companion object {
        private const val TAG = "DMPagination"

        // Extracts the subscription id from a `["REQ","<subId>",{...}]` command string.
        private val REQ_SUB_ID = Regex("^\\[\"REQ\",\"([^\"]+)\"")

        private fun reqSubId(cmdStr: String) = REQ_SUB_ID.find(cmdStr)?.groupValues?.get(1)
    }
}
