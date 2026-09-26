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
package com.vitorpamplona.quartz.nipXXSql

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.IRelayPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PolicyResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * Answers one connection's `NQL` commands (NIP-FF): each is gated like a
 * `REQ`, run by [engine], and answered with one [NqlResultMessage] (at most
 * [maxRows] rows, marked truncated past that) or a `CLOSED`.
 */
class NqlSessionHandler(
    private val engine: NqlEngine?,
    private val send: (Message) -> Unit,
    private val maxRows: Int?,
) {
    suspend fun run(
        cmd: NqlCmd,
        policy: IRelayPolicy,
    ) {
        if (engine == null) {
            send(ClosedMessage(cmd.queryId, "unsupported: this relay does not accept NQL"))
            return
        }

        // Same gate as a REQ: auth requirements, allow/deny lists and id limits apply alike.
        val gate = policy.accept(ReqCmd(cmd.queryId, listOf(Filter())))
        if (gate is PolicyResult.Rejected) {
            send(ClosedMessage(cmd.queryId, gate.reason))
            return
        }

        val answer =
            try {
                NqlResultMessage(cmd.queryId, withContext(Dispatchers.IO) { engine.run(cmd.query, cmd.params, maxRows) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: SqlException) {
                ClosedMessage(cmd.queryId, e.message ?: "invalid: query")
            } catch (e: Exception) {
                ClosedMessage(cmd.queryId, "error: ${e.message}")
            }
        send(answer)
    }
}
