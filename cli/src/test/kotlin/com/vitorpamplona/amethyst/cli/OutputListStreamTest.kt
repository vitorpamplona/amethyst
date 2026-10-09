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
package com.vitorpamplona.amethyst.cli

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `amy fetch --paginate --limit 0` writes its JSON object as the walk streams. A walk that fails
 * midway must still leave one complete object on stdout, and it must say which relays refused,
 * as a finished walk does: the error is the walk's, the refusals are the relays'.
 */
class OutputListStreamTest {
    private val realOut = System.out
    private val realMode = Output.mode

    @AfterTest
    fun restore() {
        System.setOut(realOut)
        Output.mode = realMode
    }

    @Test
    fun anAbortedStreamIsOneObjectWithTheRelayErrorsSoFar() {
        val captured = ByteArrayOutputStream()
        System.setOut(PrintStream(captured, true, Charsets.UTF_8))
        Output.mode = Output.Mode.JSON

        val stream = Output.listStream(mapOf("queried_relays" to listOf("wss://a/", "wss://b/")), "events")
        stream.item({ """{"id":"1"}""" }, { mapOf("id" to "1") })
        stream.abort("relay pool failed", mapOf("relay_errors" to mapOf("wss://b/" to mapOf("reason" to "closed", "message" to "blocked: nope"))))

        val json: Map<String, Any?> = jacksonObjectMapper().readValue(captured.toString(Charsets.UTF_8).trim())
        assertEquals(1, json["count"])
        assertEquals(listOf(mapOf("id" to "1")), json["events"])
        assertEquals(mapOf("wss://b/" to mapOf("reason" to "closed", "message" to "blocked: nope")), json["relay_errors"])
        assertEquals(mapOf("code" to "aborted", "detail" to "relay pool failed"), json["error"])
    }
}
