/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.vitorpamplona.quartz.nip01Core.jackson.EventMapper
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.filters.FilterDeserializer
import com.vitorpamplona.quartz.utils.joinToStringLimited

class ReqCmd(
    val subscriptionId: String,
    val filters: List<Filter>,
) : com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command {
    companion object {
        const val LABEL = "REQ"

        @JvmStatic
        fun toJson(
            requestId: String,
            filters: List<Filter>,
            limit: Int = 19,
        ): String =
            filters.joinToStringLimited(
                separator = ",",
                limit = limit,
                prefix = """["REQ","$requestId",""",
                postfix = "]",
            ) {
                it.toJson()
            }

        @JvmStatic
        fun parse(msgArray: JsonNode): com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd {
            val filters = mutableListOf<Filter>()

            for (i in 2 until msgArray.size()) {
                val json = EventMapper.mapper.readTree(msgArray.get(i).asText())
                if (json is ObjectNode) {
                    filters.add(FilterDeserializer.fromJson(json))
                }
            }

            return com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd(
                msgArray.get(1).asText(),
                filters,
            )
        }
    }
}
