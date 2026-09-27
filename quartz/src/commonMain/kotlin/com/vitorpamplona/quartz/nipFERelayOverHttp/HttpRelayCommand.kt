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
package com.vitorpamplona.quartz.nipFERelayOverHttp

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd

/**
 * NIP-FE: the client commands an HTTP request may carry, each as the frame a client sends on the
 * websocket, and the frame that ends each one's answer.
 */
enum class HttpRelayCommand {
    REQ,
    COUNT,
    EVENT,
    ;

    /** Whether [message] is the last frame of this command's answer. A NOTICE ends any: the command never ran. */
    fun ends(message: Message): Boolean =
        message is NoticeMessage ||
            when (this) {
                REQ -> message is EoseMessage || message is ClosedMessage
                COUNT -> message is CountMessage || message is ClosedMessage
                EVENT -> message is OkMessage
            }

    companion object {
        /** The kind of [cmd], or null for one HTTP does not carry (AUTH, CLOSE, NEG-*). */
        fun of(cmd: Command): HttpRelayCommand? =
            when (cmd) {
                is ReqCmd -> REQ
                is CountCmd -> COUNT
                is EventCmd -> EVENT
                else -> null
            }

        /** The frame that refuses [cmd] with [reason], as the socket would: CLOSED for a REQ or COUNT, OK false for an EVENT. */
        fun refusal(
            cmd: Command,
            reason: String,
        ): Message =
            when (cmd) {
                is EventCmd -> OkMessage(cmd.event.id, false, reason)
                is ReqCmd -> ClosedMessage(cmd.subId, reason)
                is CountCmd -> ClosedMessage(cmd.queryId, reason)
                else -> NoticeMessage(reason)
            }
    }
}
