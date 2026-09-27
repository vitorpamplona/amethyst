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

import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
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
import com.vitorpamplona.quartz.nip77Negentropy.NegErrMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegMsgMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegOpenCmd
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * NIP-FE: the client commands HTTP carries, one path each. A body is the
 * command's arguments after its subscription id (a lone object where the
 * command takes one); the answer ends on the first frame [ends] accepts.
 *
 * `NEG` is one NIP-77 round, `[filter, message]`: the responder keeps no
 * state between rounds but its snapshot, which the backend caches per
 * filter, so each round carries its filter and there is no session to close.
 */
enum class HttpRelayCommand(
    val path: String,
) {
    REQ("/req"),
    COUNT("/count"),
    EVENT("/event"),
    NEG("/neg"),
    ;

    /**
     * The command [body] stands for, parsed and validated, or null when it
     * is not this command's arguments. Re-serialized from a JSON tree, so a
     * body can only ever be arguments, never a second command.
     */
    fun parse(body: String): Parsed? {
        val tree =
            try {
                Json.parseToJsonElement(body)
            } catch (_: SerializationException) {
                return null
            }
        val args = arguments(tree) ?: return null
        val frame = JsonArray(head() + args).toString()
        val cmd = runCatching { OptimizedJsonMapper.fromJsonToCommand(frame) }.getOrNull() ?: return null
        return if (cmd.isValid() && cmd.matches()) Parsed(cmd, frame.length) else null
    }

    /** A body as its command, with the length of the frame it stands for: what the message-length limit measures. */
    class Parsed(
        val command: Command,
        val wireLength: Int,
    )

    /** Whether [message] is the last frame of this command's answer. */
    fun ends(message: Message): Boolean =
        message is NoticeMessage ||
            when (this) {
                REQ -> message is EoseMessage || message is ClosedMessage
                COUNT -> message is CountMessage || message is ClosedMessage
                EVENT -> message is OkMessage
                NEG -> message is NegMsgMessage || message is NegErrMessage
            }

    private fun head(): List<JsonElement> =
        when (this) {
            REQ -> listOf(JsonPrimitive(ReqCmd.LABEL), JsonPrimitive(SUB_ID))
            COUNT -> listOf(JsonPrimitive(CountCmd.LABEL), JsonPrimitive(SUB_ID))
            EVENT -> listOf(JsonPrimitive(EventCmd.LABEL))
            NEG -> listOf(JsonPrimitive(NegOpenCmd.LABEL), JsonPrimitive(SUB_ID))
        }

    private fun arguments(body: JsonElement): List<JsonElement>? =
        when (this) {
            REQ, COUNT -> {
                when (body) {
                    is JsonObject -> listOf(body)
                    is JsonArray -> body.takeIf { it.isNotEmpty() && it.all { f -> f is JsonObject } }
                    else -> null
                }
            }

            EVENT -> {
                (body as? JsonObject)?.let(::listOf)
            }

            NEG -> {
                (body as? JsonArray)?.takeIf {
                    it.size == 2 && it[0] is JsonObject && (it[1] as? JsonPrimitive)?.isString == true
                }
            }
        }

    private fun Command.matches(): Boolean =
        when (this@HttpRelayCommand) {
            REQ -> this is ReqCmd
            COUNT -> this is CountCmd
            EVENT -> this is EventCmd
            NEG -> this is NegOpenCmd
        }

    companion object {
        /** The subscription id every HTTP command runs under; each request is its own connection. */
        const val SUB_ID = "http"

        fun forPath(path: String): HttpRelayCommand? = entries.firstOrNull { it.path == path }
    }
}
