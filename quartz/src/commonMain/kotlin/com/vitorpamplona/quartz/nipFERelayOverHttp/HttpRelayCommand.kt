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
import com.vitorpamplona.quartz.nip77Negentropy.NegErrMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegMsgMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegOpenCmd

/**
 * NIP-FE: the client commands HTTP carries, one path each. A body is the command's arguments
 * after its subscription id (a lone object where the command takes one); the answer ends on the
 * first frame [ends] accepts.
 *
 * `NEG` is not in NIP-FE; it is this implementation's extension: one NIP-77 round,
 * `[filter, message]`. The responder keeps no state between rounds but its snapshot, which the
 * backend caches per filter, so each round carries its filter and there is no session to close.
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
     * The client frame [body] stands for, or null when it is not this command's arguments. Only
     * the body's outer shape is checked here ([JsonShape]); the engine parses the frame once, so a
     * malformed inside is its usual NOTICE. The shape check is what makes splicing safe: the body
     * is one balanced value with nothing after it, so it cannot close the frame or open another.
     */
    fun frameOf(body: String): String? {
        val shape = JsonShape.of(body) ?: return null
        return when (this) {
            REQ, COUNT -> {
                val filters =
                    when {
                        shape.isObject -> shape.text
                        shape.elements.isNotEmpty() && shape.elements.all { it == '{' } -> shape.inner
                        else -> return null
                    }
                frame(if (this == REQ) ReqCmd.LABEL else CountCmd.LABEL, SUB_ID, filters)
            }

            EVENT -> {
                if (!shape.isObject) return null
                "[\"${EventCmd.LABEL}\",${shape.text}]"
            }

            NEG -> {
                if (shape.isObject || shape.elements != NEG_ROUND) return null
                frame(NegOpenCmd.LABEL, SUB_ID, shape.inner)
            }
        }
    }

    /** Whether [message] is the last frame of this command's answer. */
    fun ends(message: Message): Boolean =
        message is NoticeMessage ||
            when (this) {
                REQ -> message is EoseMessage || message is ClosedMessage
                COUNT -> message is CountMessage || message is ClosedMessage
                EVENT -> message is OkMessage
                NEG -> message is NegMsgMessage || message is NegErrMessage
            }

    companion object {
        /**
         * The subscription id every HTTP command runs under inside the engine. NIP-FE answers carry
         * none, so [HttpRelayHandler] takes it back out of each frame before it goes out.
         */
        const val SUB_ID = "http"

        private val NEG_ROUND = listOf('{', '"')

        fun forPath(path: String): HttpRelayCommand? = entries.firstOrNull { it.path == path }

        private fun frame(
            label: String,
            subId: String,
            args: String,
        ) = "[\"$label\",\"$subId\",$args]"
    }
}

/**
 * The outer shape of a JSON body, read without building a tree: one object or array, brackets
 * matched by type outside strings, nesting no deeper than [MAX_DEPTH], nothing after it. An array's
 * [elements] are each top-level element's first character. Everything inside is left to the parser.
 */
internal class JsonShape private constructor(
    val text: String,
    val isObject: Boolean,
    val elements: List<Char>,
) {
    /** An array's contents without its brackets. */
    val inner: String get() = text.substring(1, text.length - 1)

    companion object {
        /** Deep enough for any filter, event or round by a wide margin; far too shallow to exhaust a stack. */
        const val MAX_DEPTH = 32

        fun of(body: String): JsonShape? {
            val text = body.trim()
            if (text.length < 2 || (text[0] != '{' && text[0] != '[')) return null
            val elements = ArrayList<Char>()
            val open = CharArray(MAX_DEPTH)
            var depth = 0
            var inString = false
            var escaped = false
            // At depth 1 inside an array: whether the next non-space character starts an element.
            var expectElement = text[0] == '['
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (inString) {
                    when {
                        escaped -> escaped = false
                        c == '\\' -> escaped = true
                        c == '"' -> inString = false
                    }
                    i++
                    continue
                }
                if (depth == 0 && i > 0) return null
                if (depth == 1 && text[0] == '[' && !c.isWhitespace()) {
                    when {
                        c == ',' -> {
                            if (expectElement) return null
                            expectElement = true
                            i++
                            continue
                        }

                        c == ']' -> {
                            if (expectElement && elements.isNotEmpty()) return null
                        }

                        expectElement -> {
                            elements.add(c)
                            expectElement = false
                        }

                        c == '{' || c == '[' || c == '"' -> {
                            return null
                        }
                    }
                }
                when (c) {
                    '"' -> {
                        inString = true
                    }

                    '{', '[' -> {
                        if (depth == MAX_DEPTH) return null
                        open[depth++] = c
                    }

                    '}', ']' -> {
                        if (depth == 0 || open[--depth] != (if (c == '}') '{' else '[')) return null
                    }
                }
                i++
            }
            if (depth != 0 || inString) return null
            return JsonShape(text, text[0] == '{', elements)
        }
    }
}
