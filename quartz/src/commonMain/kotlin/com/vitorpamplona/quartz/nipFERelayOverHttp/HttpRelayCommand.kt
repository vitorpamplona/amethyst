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
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.toHttp

/**
 * NIP-FE: the client commands HTTP carries, one path each. A body is the command's arguments
 * after its subscription id (a lone object where the command takes one); the answer ends on the
 * first frame [ends] accepts.
 */
enum class HttpRelayCommand(
    val path: String,
) {
    REQ("/req"),
    COUNT("/count"),
    EVENT("/event"),
    ;

    /**
     * The client frame [body] stands for, or null when it plainly is not this command's arguments.
     * The body is spliced in as sent and the engine parses the frame, as it parses socket text, so
     * any other malformed body is the engine's NOTICE. The verb and subscription id come first and
     * the parser reads one value, so nothing a body holds can make it another command.
     */
    fun frameOf(body: String): String? {
        val text = body.trim()
        return when (this) {
            REQ, COUNT -> {
                val filters =
                    when {
                        text.startsWith('{') -> text
                        text.startsWith('[') && text.endsWith(']') -> text.substring(1, text.length - 1).trim().ifEmpty { return null }
                        else -> return null
                    }
                "[\"${if (this == REQ) ReqCmd.LABEL else CountCmd.LABEL}\",\"$SUB_ID\",$filters]"
            }

            EVENT -> {
                if (!text.startsWith('{')) return null
                "[\"${EventCmd.LABEL}\",$text]"
            }
        }
    }

    /** This command's endpoint on [relay]: the relay URL read as http(s), host and path kept, plus [path]. */
    fun url(relay: NormalizedRelayUrl): String = relay.toHttp().trimEnd('/') + path

    /** Whether [message] is the last frame of this command's answer. */
    fun ends(message: Message): Boolean =
        message is NoticeMessage ||
            when (this) {
                REQ -> message is EoseMessage || message is ClosedMessage
                COUNT -> message is CountMessage || message is ClosedMessage
                EVENT -> message is OkMessage
            }

    companion object {
        /**
         * The subscription id every HTTP command runs under inside the engine. NIP-FE answers carry
         * none, so [HttpRelayHandler] takes it back out of each frame before it goes out.
         */
        const val SUB_ID = "http"

        fun forPath(path: String): HttpRelayCommand? = entries.firstOrNull { it.path == path }

        /** A REQ or COUNT body: the filters as the array that follows the subscription id. */
        fun body(filters: List<Filter>): String = filters.joinToString(",", "[", "]") { it.toJson() }
    }
}
