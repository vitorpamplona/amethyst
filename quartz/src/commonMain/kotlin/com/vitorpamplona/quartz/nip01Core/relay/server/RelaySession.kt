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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.AuthMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountResult
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.AuthCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Represents a single connected client with its active subscriptions.
 *
 * Supports NIP-42 authentication. Multiple pubkeys may authenticate on
 * the same session. When [NostrServer.requireAuth] is true, EVENT, REQ
 * and COUNT commands are rejected until at least one pubkey authenticates.
 */
class RelaySession(
    private val server: NostrServer,
    private val store: LiveEventStore,
    private val verify: (Event) -> Boolean,
    private val scope: CoroutineScope,
    private val onSend: (String) -> Unit,
    private val onClose: (RelaySession) -> Unit,
) : AutoCloseable {
    private val subscriptions = LargeCache<String, Job>()

    /** The challenge string sent to this client for NIP-42 authentication. */
    val challenge: String = RandomInstance.randomChars(32)

    /** Set of pubkeys that have successfully authenticated on this session. */
    private val authenticatedUsers = mutableSetOf<HexKey>()

    /** Returns true if at least one pubkey has authenticated. */
    fun isAuthenticated(): Boolean = authenticatedUsers.isNotEmpty()

    /** Returns the set of authenticated pubkeys for this session. */
    fun authenticatedPubkeys(): Set<HexKey> = authenticatedUsers.toSet()

    /**
     * Sends the AUTH challenge to the client.
     * Call this after the WebSocket connection is established.
     */
    fun sendAuthChallenge() {
        send(AuthMessage(challenge))
    }

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
            is AuthCmd -> handleAuth(cmd)
            is EventCmd -> handleEvent(cmd)
            is ReqCmd -> handleReq(cmd)
            is CloseCmd -> handleClose(cmd)
            is CountCmd -> handleCount(cmd)
            else -> send(NoticeMessage("error: unsupported command ${cmd.label()}"))
        }
    }

    // -- NIP-42: AUTH ---------------------------------------------------------
    private fun handleAuth(cmd: AuthCmd) {
        val event = cmd.event

        // Must be kind 22242
        if (event.kind != RelayAuthEvent.KIND) {
            send(OkMessage(event.id, false, "invalid: wrong event kind"))
            return
        }

        // Verify signature and id
        if (!verify(event)) {
            send(OkMessage(event.id, false, "invalid: bad signature or id"))
            return
        }

        // created_at must be within 10 minutes
        val now = TimeUtils.now()
        val tenMinutes = 600L
        if (event.createdAt < now - tenMinutes || event.createdAt > now + tenMinutes) {
            send(OkMessage(event.id, false, "invalid: created_at is too far from the current time"))
            return
        }

        // Challenge tag must match
        val eventChallenge =
            event.tags.firstNotNullOfOrNull { tag ->
                if (tag.size >= 2 && tag[0] == "challenge") tag[1] else null
            }
        if (eventChallenge != challenge) {
            send(OkMessage(event.id, false, "invalid: challenge does not match"))
            return
        }

        // Relay tag must match this relay's URL
        val eventRelay =
            event.tags.firstNotNullOfOrNull { tag ->
                if (tag.size >= 2 && tag[0] == "relay") tag[1] else null
            }
        if (eventRelay == null || !relayUrlMatches(eventRelay)) {
            send(OkMessage(event.id, false, "invalid: relay url does not match"))
            return
        }

        // Authentication successful — add pubkey
        authenticatedUsers.add(event.pubKey)
        send(OkMessage(event.id, true, ""))
    }

    private fun relayUrlMatches(eventRelayUrl: String): Boolean {
        val ours = server.relayUrl.url.trimEnd('/')
        val theirs = eventRelayUrl.trimEnd('/')
        return ours.equals(theirs, ignoreCase = true)
    }

    // -- NIP-01: EVENT --------------------------------------------------------
    private fun handleEvent(cmd: EventCmd) {
        val event = cmd.event

        val result = server.authPolicy.acceptEvent(event, authenticatedPubkeys())
        if (result is PolicyResult.Rejected) {
            send(OkMessage(event.id, false, result.reason))
            return
        }

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
        val result = server.authPolicy.acceptReq(cmd.filters, authenticatedPubkeys())
        if (result is ReqPolicyResult.Rejected) {
            send(ClosedMessage(cmd.subId, result.reason))
            return
        }

        // Policy may rewrite filters to match the user's access level.
        val filters = (result as ReqPolicyResult.Accepted).filters ?: cmd.filters

        // Cancel any existing subscription with the same id (NIP-01 spec).
        cancelSubscription(cmd.subId)

        val authed = authenticatedPubkeys()
        val policy = server.authPolicy
        val job =
            scope.launch {
                try {
                    store.query(
                        filters = filters,
                        onEach = { event ->
                            if (policy.canSendToSession(event, authed)) {
                                send(EventMessage(cmd.subId, event))
                            }
                        },
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
        val result = server.authPolicy.acceptCount(cmd.filters, authenticatedPubkeys())
        if (result is PolicyResult.Rejected) {
            send(ClosedMessage(cmd.queryId, result.reason))
            return
        }

        val total = store.count(cmd.filters)
        send(CountMessage(cmd.queryId, CountResult(total)))
    }
}
