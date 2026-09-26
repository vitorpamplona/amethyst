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

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command

/**
 * Runs a query: `["NQL", <queryId>, <query>, <params>?]` (NIP-FF). `params`
 * holds one value per `?`, in order. The relay answers [NqlResultMessage], or
 * `CLOSED` with an `invalid:` / `auth-required:` / `restricted:` /
 * `unsupported:` / `error:` reason.
 */
class NqlCmd(
    val queryId: String,
    val query: String,
    val params: List<Any?> = emptyList(),
) : Command {
    override fun label() = LABEL

    override fun isValid() = queryId.isNotEmpty()

    companion object {
        const val LABEL = "NQL"
    }
}

/** The answer: `["NQL", <queryId>, {"columns": [[name, type], …], "rows": [[…], …], "truncated": bool}]`. */
class NqlResultMessage(
    val queryId: String,
    val result: NqlResult,
) : Message {
    override fun label() = LABEL

    companion object {
        const val LABEL = "NQL"
    }
}
