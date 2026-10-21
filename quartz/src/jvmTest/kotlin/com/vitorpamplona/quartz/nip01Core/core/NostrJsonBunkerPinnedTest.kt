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
package com.vitorpamplona.quartz.nip01Core.core

import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerMessage
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * NIP-46 messages read and written by Quartz's reader and writer. The pins were recorded while
 * every outcome was also compared with the Jackson (de)serializers they replaced. The only
 * intended difference: a JSON `null` in `id`, `method`, `result` or `error` reads as absent,
 * where Jackson read the text "null".
 */
class NostrJsonBunkerPinnedTest {
    private fun describe(m: BunkerMessage): String =
        when (m) {
            is BunkerRequest -> "${m::class.simpleName}|${m.id}|${m.method}|${m.params.joinToString("\u00A6")}"
            is BunkerResponse -> "${m::class.simpleName}|${m.id}|${m.result}|${m.error}"
            else -> "?"
        }

    private fun <T> run(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: Exception) {
            Result.failure(e)
        }

    private val pk = "a".repeat(64)
    private val template = """{"kind":1,"content":"hi","tags":[["t","x"]],"created_at":1700000000}"""

    private val requests =
        listOf(
            """{"id":"1","method":"connect","params":["$pk","secret","sign_event:1,nip44_encrypt"]}""",
            """{"id":"2","method":"sign_event","params":["${template.replace("\"", "\\\"")}"]}""",
            """{"id":"3","method":"get_public_key","params":[]}""",
            """{"id":"4","method":"get_relays"}""",
            """{"id":"5","method":"ping","params":[]}""",
            """{"id":"6","method":"logout","params":[]}""",
            """{"id":"7","method":"nip04_encrypt","params":["$pk","plain text \n with \"quotes\""]}""",
            """{"id":"8","method":"nip04_decrypt","params":["$pk","Zm9v?iv=YmFy"]}""",
            """{"id":"9","method":"nip44_encrypt","params":["$pk","x"]}""",
            """{"id":"10","method":"nip44_decrypt","params":["$pk","AgAAA"]}""",
            """{"id":"11","method":"switch_relays","params":[]}""",
            """{"id":"12","method":"some_future_method","params":["a",1,true,null,1.5]}""",
            """{"id":"13","method":"sign_event","params":[{"kind": 1, "content" : "x", "tags": [ ["a", "b"] ]}]}""",
            """{"id":"14","method":"sign_event","params":"not an array"}""",
            """{"id":"15","method":"sign_event","params":[["nested", ["array"]]]}""",
            """{"id":"16","method":"sign_event"}""",
            """{"id":17,"method":"ping"}""",
            """{"method":"ping","id":"18","extra":{"x":[1,2]}}""",
            """ {"id" : "19", "method" : "connect", "params" : [ "$pk" ] } """,
            """{"id":"20","method":"sign_event","params":["{not json"]}""",
        )

    private val responses =
        listOf(
            """{"id":"1","result":"ack"}""",
            """{"id":"2","result":"pong"}""",
            """{"id":"3","result":"$pk"}""",
            """{"id":"4","result":"auth_url","error":"https://signer.example/auth?x=1"}""",
            """{"id":"5","error":"denied"}""",
            """{"id":"6","result":"some text"}""",
            """{"id":"7","result":"${("{\"id\":\"" + "e".repeat(64) + "\",\"pubkey\":\"" + pk + "\",\"created_at\":1,\"kind\":1,\"tags\":[],\"content\":\"\",\"sig\":\"" + "f".repeat(128) + "\"}").replace("\"", "\\\"")}"}""",
            """{"id":"8","result":"{\"wss://r.example/\":{\"read\":true,\"write\":true}}"}""",
            """{"id":"9","result":"{broken"}""",
            """{"id":"11"}""",
            """{"id":"12","result":5}""",
            """{"id":"13","result":true,"error":false}""",
            """{"id":"14","result":"ack","error":"also an error"}""",
        )

    private val broken =
        listOf("", "{", "[]", "\"x\"", """{"id":"1","method":"ping",}""", """{"id":"1","method":"ping"""", """{"method":"ping"}""", """{"result":"ack"}""")

    /** Pins the message read of [input], and checks the [typed] read builds the same. */
    private fun compare(
        input: String,
        typed: (String) -> BunkerMessage,
    ) {
        val r = run { describe(NostrJsonReader.readBunkerMessage(input)) }
        readPin.add(r.getOrNull())
        assertEquals(r.getOrNull(), run { describe(typed(input)) }.getOrNull(), input)
    }

    private val readPin = Pin("bunker reads")

    @Test
    fun requestsAndResponsesReadAsPinned() {
        for (s in requests) {
            compare(s) { OptimizedJsonMapper.fromJsonTo<BunkerMessage>(it) }
            compare(s) { OptimizedJsonMapper.fromJsonTo<BunkerRequest>(it) }
        }
        for (s in responses) {
            compare(s) { OptimizedJsonMapper.fromJsonTo<BunkerMessage>(it) }
            compare(s) { OptimizedJsonMapper.fromJsonTo<BunkerResponse>(it) }
        }
        for (s in broken) {
            if (run { NostrJsonReader.readBunkerMessage(s) }.isSuccess) fail("accepted $s")
        }
        readPin.check(PIN_READS)

        // Every prefix of a real request and response is rejected.
        for (s in listOf(requests[0], requests[1], responses[6])) {
            for (k in s.indices) {
                if (run { NostrJsonReader.readBunkerMessage(s.substring(0, k)) }.isSuccess) fail("accepted a prefix: ${s.substring(0, k)}")
            }
        }
    }

    @Test
    fun anEmptyResultIsARawResponse() {
        // Jackson's deserializer threw StringIndexOutOfBoundsException on it (result[0] of "").
        assertEquals("BunkerResponse|10||null", describe(NostrJsonReader.readBunkerMessage("""{"id":"10","result":""}""")))
    }

    @Test
    fun nullFieldsReadAsAbsent() {
        // Jackson read these nulls as the text "null": the first was an error named "null".
        assertEquals("BunkerResponsePublicKey|1|$pk|null", describe(NostrJsonReader.readBunkerMessage("""{"id":"1","result":"$pk","error":null}""")))
        assertEquals("BunkerResponse|2|null|null", describe(NostrJsonReader.readBunkerMessage("""{"id":"2","result":null}""")))
        assertEquals("BunkerResponseError|3|null|nope", describe(NostrJsonReader.readBunkerMessage("""{"id":"3","result":null,"error":"nope"}""")))
        if (run { NostrJsonReader.readBunkerMessage("""{"id":null,"result":"ack"}""") }.isSuccess) fail("an id of null")
        if (run { NostrJsonReader.readBunkerMessage("""{"id":"4","method":null}""") }.isSuccess) fail("a method of null")
    }

    @Test
    fun writesAsPinned() {
        val values =
            requests.map { NostrJsonReader.readBunkerRequest(it) } +
                responses.map { NostrJsonReader.readBunkerResponse(it) } +
                listOf(BunkerResponse("x", null, null), BunkerResponse("y", null, "e\n\"r\""), BunkerRequest("z", "m\u0001", arrayOf("", "\u2028", "\uD83D\uDE00")))
        val pin = Pin("bunker writes")
        for (v in values) {
            pin.add(NostrJsonWriter.toJsonOrNull(v))
            assertEquals(OptimizedJsonMapper.toJson(v), NostrJsonWriter.toJsonOrNull(v), describe(v as BunkerMessage))
        }
        pin.check(PIN_WRITES)
    }

    companion object {
        /** See [Pin]: recorded while every outcome was also compared with Jackson. */
        private const val PIN_READS = "b492b25982eaca844ef64c7c8d1f551e92de87dfbdca3d3db59a3f4641354bb0"
        private const val PIN_WRITES = "787f168266c0fdbbc05aeddaaa52d9c42e05aab50955aaa5881b4a3e40c86f97"
    }
}
