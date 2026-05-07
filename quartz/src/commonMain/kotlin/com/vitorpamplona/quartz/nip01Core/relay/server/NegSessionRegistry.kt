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

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegCloseCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegErrMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegMsgCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegOpenCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegentropyServerSession

/**
 * Per-connection NIP-77 negentropy state and dispatch.
 *
 * Owns the map of active reconciliation sessions keyed by NEG-OPEN
 * subId, and the open/msg/close handlers. Pulled out of [RelaySession]
 * so the connection class only routes commands while this class owns
 * the negentropy lifecycle and error mapping.
 *
 * Plain [HashMap] is sufficient because the registry is mutated only
 * from [RelaySession.receive] — that path is single-threaded per the
 * WebSocket handler contract.
 */
class NegSessionRegistry(
    private val store: LiveEventStore,
    private val send: (Message) -> Unit,
) {
    private val sessions = HashMap<String, NegentropyServerSession>()

    /**
     * Open a reconciliation session. The relay snapshots its matching
     * events at this instant — concurrent inserts during the sync are
     * not surfaced; clients re-open if they want fresh state.
     *
     * Access control reuses the REQ policy hook: a relay that requires
     * AUTH or has kind/pubkey allow-deny lists applies the same rules
     * to NEG-OPEN as it does to subscription REQs.
     */
    suspend fun open(
        cmd: NegOpenCmd,
        policy: IRelayPolicy,
    ) {
        val gate = policy.accept(ReqCmd(cmd.subId, listOf(cmd.filter)))
        if (gate is PolicyResult.Rejected) {
            send(NegErrMessage(cmd.subId, gate.reason))
            return
        }
        val filters = (gate as PolicyResult.Accepted).cmd.filters

        // NIP-77: same-subId OPEN replaces any prior session.
        sessions.remove(cmd.subId)

        val events = store.snapshotQuery(filters)
        val session = NegentropyServerSession(cmd.subId, events)
        sessions[cmd.subId] = session

        runMessage(cmd.subId, session) { it.processMessage(cmd.initialMessage) }
    }

    fun msg(cmd: NegMsgCmd) {
        val session = sessions[cmd.subId]
        if (session == null) {
            send(NegErrMessage(cmd.subId, "error: no negentropy session for ${cmd.subId}"))
            return
        }
        runMessage(cmd.subId, session) { it.processMessage(cmd.message) }
    }

    /**
     * Spec: clients send NEG-CLOSE to free server-side state.
     * Silent no-op if the session is unknown — there's no authoritative
     * error response in NIP-77 for an unknown close.
     */
    fun close(cmd: NegCloseCmd) {
        sessions.remove(cmd.subId)
    }

    /** Dropped on `RelaySession.cancelAllSubscriptions`. */
    fun clear() {
        sessions.clear()
    }

    private inline fun runMessage(
        subId: String,
        session: NegentropyServerSession,
        block: (NegentropyServerSession) -> Message?,
    ) {
        try {
            val response = block(session)
            if (response != null) send(response)
        } catch (e: Exception) {
            sessions.remove(subId)
            send(NegErrMessage(subId, "error: ${e.message ?: e::class.simpleName}"))
        }
    }
}
