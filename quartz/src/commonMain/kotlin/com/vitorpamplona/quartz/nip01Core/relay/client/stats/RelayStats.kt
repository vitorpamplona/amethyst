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
package com.vitorpamplona.quartz.nip01Core.relay.client.stats

import androidx.collection.LruCache
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NotifyMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.bytesUsedInMemory

class RelayStats(
    val client: INostrClient,
) {
    private val innerCache =
        object : LruCache<NormalizedRelayUrl, RelayStat>(1000) {
            override fun create(key: NormalizedRelayUrl): RelayStat = RelayStat()
        }

    fun get(url: NormalizedRelayUrl): RelayStat = innerCache[url] ?: throw IllegalArgumentException("Should never happen")

    private val clientListener =
        object : IRelayClientListener {
            override fun onConnected(
                relay: IRelayClient,
                pingMillis: Int,
                compressed: Boolean,
            ) {
                with(get(relay.url)) {
                    pingInMs = pingMillis
                    compression = compressed
                }
            }

            override fun onCannotConnect(
                relay: IRelayClient,
                errorMessage: String,
            ) {
                get(relay.url).newError(errorMessage)
            }

            override fun onSent(
                relay: IRelayClient,
                cmdStr: String,
                cmd: Command,
                success: Boolean,
            ) {
                get(relay.url).addBytesSent(cmdStr.bytesUsedInMemory())
            }

            override fun onIncomingMessage(
                relay: IRelayClient,
                msgStr: String,
                msg: Message,
            ) {
                val stat = get(relay.url)

                stat.addBytesReceived(msgStr.bytesUsedInMemory())

                when (msg) {
                    is NoticeMessage -> stat.newNotice(msg.message)
                    is NotifyMessage -> stat.newNotice("Notify user: " + msg.message)
                    is OkMessage -> {
                        if (!msg.success) {
                            stat.newNotice("Rejected event ${msg.eventId}: ${msg.message}")
                        }
                    }
                    is ClosedMessage -> stat.newNotice("Subscription closed: ${msg.subId} ${msg.message}")
                }
            }
        }

    init {
        Log.d("RelayStats", "Init, Subscribe")
        client.subscribe(clientListener)
    }

    fun destroy() {
        // makes sure to run
        Log.d("RelayStats", "Destroy, Unsubscribe")
        client.unsubscribe(clientListener)
    }
}
