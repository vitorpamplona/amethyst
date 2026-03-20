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
package com.vitorpamplona.quartz.nip01Core.relay.server

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountResult
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Represents a single connected client with its active subscriptions.
 */
class RelaySession(
    private val store: LiveEventStore,
    private val verify: (Event) -> Boolean,
    private val scope: CoroutineScope,
    private val onSend: (String) -> Unit,
    private val onClose: (RelaySession) -> Unit,
) : AutoCloseable {
    private val subscriptions = LargeCache<String, Job>()

    fun send(message: Message) {
        try {
            onSend(OptimizedJsonMapper.toJson(message))
        } catch (e: Exception) {
            Log.w("ClientSession", "Failed to send to ${e.message}")
        }
    }

    fun addSubscription(
        subId: String,
        job: Job,
    ) = subscriptions.put(subId, job)

    fun cancelSubscription(subId: String): Boolean =
        subscriptions.remove(subId)?.let {
            it.cancel()
            true
        } ?: false

    fun cancelAllSubscriptions() {
        subscriptions.forEach { _, job -> job.cancel() }
        subscriptions.clear()
    }

    override fun close() {
        cancelAllSubscriptions()
        onClose(this)
    }

    /**
     * Processes a raw JSON message from a client.
     *
     * Parses the message as a NIP-01 command and dispatches it.
     */
    suspend fun processMessage(message: String) {
        val cmd =
            try {
                OptimizedJsonMapper.fromJsonToCommand(message)
            } catch (_: Exception) {
                send(NoticeMessage("error: could not parse message"))
                return
            }

        if (!cmd.isValid()) {
            send(NoticeMessage("error: invalid command"))
            return
        }

        when (cmd) {
            is EventCmd -> handleEvent(cmd)
            is ReqCmd -> handleReq(cmd)
            is CloseCmd -> handleClose(cmd)
            is CountCmd -> handleCount(cmd)
            else -> send(NoticeMessage("error: unsupported command ${cmd.label()}"))
        }
    }

    // -- NIP-01: EVENT --------------------------------------------------------
    private fun handleEvent(cmd: EventCmd) {
        val event = cmd.event

        if (!verify(event)) {
            send(OkMessage(event.id, false, "invalid: bad signature or id"))
            return
        }

        try {
            store.insert(event)
            send(OkMessage(event.id, true, ""))
        } catch (e: Exception) {
            send(OkMessage(event.id, false, e.message ?: e::class.simpleName ?: "unkown error"))
        }
    }

    // -- NIP-01: REQ ----------------------------------------------------------
    private fun handleReq(cmd: ReqCmd) {
        // Cancel any existing subscription with the same id (NIP-01 spec).
        cancelSubscription(cmd.subId)

        val job =
            scope.launch {
                try {
                    store.query(
                        filters = cmd.filters,
                        onEach = { send(EventMessage(cmd.subId, it)) },
                        onEose = { send(EoseMessage(cmd.subId)) },
                    )
                } catch (_: kotlinx.coroutines.CancellationException) {
                    // Subscription was closed – this is expected.
                }
            }

        addSubscription(cmd.subId, job)
    }

    // -- NIP-01: CLOSE --------------------------------------------------------
    private fun handleClose(cmd: CloseCmd) {
        val cancelled = cancelSubscription(cmd.subId)
        if (!cancelled) {
            send(ClosedMessage(cmd.subId, "error: no such subscription"))
        }
    }

    // -- NIP-45: COUNT --------------------------------------------------------
    private fun handleCount(cmd: CountCmd) {
        val total = store.count(cmd.filters)
        send(CountMessage(cmd.queryId, CountResult(total)))
    }
}
