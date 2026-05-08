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
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegCloseCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegErrMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegMsgCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegOpenCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegentropyServerSession
import com.vitorpamplona.quartz.nip77Negentropy.NegentropySettings

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
 *
 * Defaults match strfry (`hoytech/strfry`) so a Geode relay reconciles
 * with the same round-trip shape and the same operator-visible
 * protections — see [NegentropySettings].
 */
class NegSessionRegistry(
    private val store: LiveEventStore,
    private val send: (Message) -> Unit,
    private val settings: NegentropySettings = NegentropySettings.Default,
) {
    private val sessions = HashMap<String, NegentropyServerSession>()

    /**
     * Open a reconciliation session. The relay snapshots the matching
     * `(created_at, id)` pairs at this instant — concurrent inserts
     * during the sync are not surfaced; clients re-open if they want
     * fresh state.
     *
     * Access control reuses the REQ policy hook: a relay that requires
     * AUTH or has kind/pubkey allow-deny lists applies the same rules
     * to NEG-OPEN as it does to subscription REQs.
     *
     * Two strfry-parity protections fire here:
     *  - **Per-connection session cap.** If an OPEN would push the
     *    map past [NegentropySettings.maxSessionsPerConnection], we
     *    send a NOTICE (matching strfry's
     *    `"too many concurrent NEG requests"`) and drop the OPEN.
     *  - **Snapshot size cap.** The store is asked for at most
     *    `maxSyncEvents + 1` entries; if the +1 sentinel comes back,
     *    the corpus exceeds the cap and we send NEG-ERR
     *    `"blocked: too many query results"` (matching strfry).
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

        // Per-connection cap. Only fires when this is a NEW subId —
        // a same-subId re-open replaces the prior session 1-for-1.
        val isReopen = sessions.containsKey(cmd.subId)
        if (!isReopen && sessions.size >= settings.maxSessionsPerConnection) {
            send(NoticeMessage("too many concurrent NEG requests"))
            return
        }

        // NIP-77: same-subId OPEN replaces any prior session.
        sessions.remove(cmd.subId)

        val cap = settings.maxSyncEvents
        val entries = store.snapshotIdsForNegentropy(filters, maxEntries = cap)
        if (entries.size > cap) {
            send(NegErrMessage(cmd.subId, "blocked: too many query results"))
            return
        }

        val session =
            NegentropyServerSession(
                subId = cmd.subId,
                localEntries = entries,
                frameSizeLimit = settings.frameSizeLimit,
            )
        sessions[cmd.subId] = session

        runMessage(cmd.subId, session) { it.processMessage(cmd.initialMessage) }
    }

    /**
     * Process a follow-up NEG-MSG. strfry-parity wording for the
     * unknown-subId case: `"closed: unknown subscription handle"`.
     */
    fun msg(cmd: NegMsgCmd) {
        val session = sessions[cmd.subId]
        if (session == null) {
            send(NegErrMessage(cmd.subId, "closed: unknown subscription handle"))
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

    /** Test/diagnostic accessor. */
    val activeSessionCount: Int get() = sessions.size

    private inline fun runMessage(
        subId: String,
        session: NegentropyServerSession,
        block: (NegentropyServerSession) -> Message?,
    ) {
        try {
            val response = block(session)
            if (response != null) send(response)
        } catch (_: Exception) {
            // strfry sends `PROTOCOL-ERROR` on library reconcile()
            // parse failure and tears the session down.
            sessions.remove(subId)
            send(NegErrMessage(subId, "PROTOCOL-ERROR"))
        }
    }
}
