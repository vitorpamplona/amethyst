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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.RequestContext
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PassThroughPolicy

/**
 * Gates SQL on NIP-42: with [requireAuth] the connection must have
 * authenticated, and with a non-empty [allowedPubkeys] one of its
 * authenticated keys must be on the list. Everything else passes through,
 * so stack it with the relay's other policies. Build one per connection.
 */
class SqlAccessPolicy(
    private val requireAuth: Boolean = false,
    private val allowedPubkeys: Set<HexKey> = emptySet(),
) : PassThroughPolicy() {
    private var scope: RequestContext? = null

    override fun onConnect(
        scope: RequestContext,
        send: (Message) -> Unit,
    ) {
        this.scope = scope
    }

    override fun acceptSql(cmd: SqlCmd): String? {
        if (!requireAuth && allowedPubkeys.isEmpty()) return null
        val users = scope?.authenticatedUsers.orEmpty()
        if (users.isEmpty()) return "auth-required: SQL needs NIP-42 authentication"
        if (allowedPubkeys.isNotEmpty() && users.none { it in allowedPubkeys }) return "restricted: SQL is limited to allowed pubkeys"
        return null
    }
}
