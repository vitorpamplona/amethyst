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
package com.vitorpamplona.contextvm.cep17RelayList

import com.vitorpamplona.contextvm.core.CvmKinds
import com.vitorpamplona.contextvm.core.CvmTags
import com.vitorpamplona.quartz.nip01Core.core.Event

/**
 * A relay a server is reachable on (CEP-17, NIP-65 kind 10002).
 *
 * The ContextVM profile publishes unmarked tags, meaning the relay serves both
 * directions. Markers are honoured when present but are not the norm here.
 */
data class ServerRelay(
    val url: String,
    val read: Boolean = true,
    val write: Boolean = true,
) {
    companion object {
        const val READ = "read"
        const val WRITE = "write"

        fun parseAll(event: Event): List<ServerRelay> {
            if (event.kind != CvmKinds.RELAY_LIST) return emptyList()
            return event.tags
                .filter { it.size >= 2 && it[0] == CvmTags.RELAY && it[1].isNotBlank() }
                .map { tag ->
                    when (tag.getOrNull(2)) {
                        READ -> ServerRelay(tag[1], read = true, write = false)
                        WRITE -> ServerRelay(tag[1], read = false, write = true)
                        // Unmarked is the recommended ContextVM profile: the
                        // relay is usable for both publishing and subscribing.
                        else -> ServerRelay(tag[1])
                    }
                }
        }

        /**
         * Relays usable for a full request/response exchange.
         *
         * A read-only or write-only relay cannot carry both halves, and since
         * kind 25910 is ephemeral there is no fetching a response later from
         * somewhere else.
         */
        fun operational(relays: List<ServerRelay>) = relays.filter { it.read && it.write }
    }
}
