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
package com.vitorpamplona.quartz.nip55AndroidSigner

import com.vitorpamplona.quartz.nip55AndroidSigner.api.CommandType
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.results.IntentResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.permission.Permission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * [Nip55Json]'s reads and writes, pinned. The pins were recorded while every outcome was also
 * compared with the Jackson mapper it replaced. The one intended difference: a permission's
 * `"kind": null` reads as no kind, where Jackson read kind 0.
 */
class Nip55JsonPinnedTest {
    private val pin = Pin("nip55")

    private fun describe(p: Permission) = "${p.type}|${p.kind}"

    private fun describe(r: IntentResult) = "${r.`package`}|${r.result}|${r.event}|${r.id}|${r.rejected}"

    private fun <T> run(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: Exception) {
            Result.failure(e)
        }

    private fun read(
        input: String,
        ours: (String) -> String,
    ) {
        pin.add(run { ours(input) }.getOrNull())
    }

    private val results =
        listOf(
            """{"package":"com.signer","result":"sig","event":"{\"id\":\"x\"}","id":"1","rejected":false}""",
            """{"package":null,"result":"r","rejected":null,"id":"2","signature":"s"}""",
            """{"rejected":true}""",
            """{"result":5,"id":true,"package":{},"event":[1],"rejected":"true"}""",
            "{}",
            """ { "id" : "3" , "unknown" : { "deep" : [ 1 , { } ] } } """,
        )

    private val permissions =
        listOf(
            """{"type":"sign_event","kind":1}""",
            """{"type":"nip44_encrypt"}""",
            """{"type":"sign_event","kind":"7"}""",
            """{"type":"sign_event","kind":7.9}""",
            """{"type":"sign_event","kind":true}""",
            """{"type":"sign_event","kind":{}}""",
            """{"type":"unknown_type","kind":1}""",
            """{"type":5}""",
            """{"type":"get_public_key","extra":[1,2]}""",
        )

    @Test
    fun readsAsPinned() {
        for (s in results) read(s) { describe(Nip55Json.fromJsonToIntentResult(it)) }
        val list = "[" + results.joinToString(",") + "]"
        read(list) { Nip55Json.fromJsonToIntentResultList(it).joinToString("\n") { r -> describe(r) } }
        read("[]") { Nip55Json.fromJsonToIntentResultList(it).size.toString() }

        for (s in permissions) read(s) { describe(Nip55Json.fromJsonToPermission(it)) }
        val array = "[" + permissions.joinToString(",") + "]"
        read(array) { Nip55Json.fromJsonToPermissionArray(it).joinToString("\n") { p -> describe(p) } }

        pin.check(PIN_READS)

        for (bad in listOf("", "{", "[", """{"type":"sign_event",}""", """[{"id":"1"},""", """{"kind":1}""")) {
            if (run { Nip55Json.fromJsonToPermission(bad) }.isSuccess && bad.startsWith("{\"type")) fail("accepted $bad")
            if (bad.startsWith("[") && run { Nip55Json.fromJsonToIntentResultList(bad) }.isSuccess) fail("accepted $bad")
        }
    }

    @Test
    fun aNullKindStaysNull() {
        assertNull(Nip55Json.fromJsonToPermission("""{"type":"sign_event","kind":null}""").kind)
        val written = Nip55Json.toJson(Permission(CommandType.SIGN_EVENT))
        assertNull(Nip55Json.fromJsonToPermission(written).kind)
    }

    @Test
    fun writesAsPinned() {
        val perms =
            listOf(
                Permission(CommandType.SIGN_EVENT, 1),
                Permission(CommandType.NIP44_ENCRYPT),
                Permission(null, 3),
                Permission(null),
            )
        for (p in perms) {
            pin.add(Nip55Json.toJson(p))
        }
        pin.add(Nip55Json.toJson(perms))
        assertEquals("[]", Nip55Json.toJson(emptyList<Permission>()))

        val rs =
            listOf(
                IntentResult("pkg", "r", "{\"e\":\"\\n\"}", "1", false),
                IntentResult(),
                IntentResult(rejected = null),
                IntentResult(result = "\u0001 \u2028 \uD83D\uDE00", rejected = true),
            )
        for (r in rs) {
            pin.add(Nip55Json.toJson(r))
        }
        pin.check(PIN_WRITES)
    }

    companion object {
        /** See [Pin]: recorded while every outcome was also compared with Jackson. */
        private const val PIN_READS = "0196167e0a2f6211eb3eaf7e9cf9f93c3a456043a7b7f96d510db721b9b889f6"
        private const val PIN_WRITES = "c6e6d4c71c4f5f7b51578bec76d1b38319c9c1e5b0a6419b38741cfd90da87bb"
    }
}
