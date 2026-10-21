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
 * [Nip55Json] against the Jackson mapper it replaces. The one intended difference: a permission's
 * `"kind": null` reads as no kind, where Jackson read kind 0.
 */
class Nip55JsonParityTest {
    private fun describe(p: Permission) = "${p.type}|${p.kind}"

    private fun describe(r: IntentResult) = "${r.`package`}|${r.result}|${r.event}|${r.id}|${r.rejected}"

    private fun <T> run(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: Exception) {
            Result.failure(e)
        }

    private fun same(
        input: String,
        jackson: (String) -> String,
        ours: (String) -> String,
    ) {
        val j = run { jackson(input) }
        val o = run { ours(input) }
        when {
            j.isSuccess && o.isSuccess -> assertEquals(j.getOrNull(), o.getOrNull(), input)
            o.isSuccess -> fail("only the new reader accepts: $input")
            j.isSuccess -> fail("only Jackson accepts: $input (${o.exceptionOrNull()?.message})")
        }
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
    fun readsTheSame() {
        for (s in results) same(s, { describe(JsonMapperNip55.fromJsonToIntentResult(it)) }, { describe(Nip55Json.fromJsonToIntentResult(it)) })
        val list = "[" + results.joinToString(",") + "]"
        same(list, { JsonMapperNip55.fromJsonToIntentResultList(it).joinToString("\n") { r -> describe(r) } }, { Nip55Json.fromJsonToIntentResultList(it).joinToString("\n") { r -> describe(r) } })
        same("[]", { JsonMapperNip55.fromJsonToIntentResultList(it).size.toString() }, { Nip55Json.fromJsonToIntentResultList(it).size.toString() })

        for (s in permissions) same(s, { describe(JsonMapperNip55.fromJsonToPermission(it)) }, { describe(Nip55Json.fromJsonToPermission(it)) })
        val array = "[" + permissions.joinToString(",") + "]"
        same(array, { JsonMapperNip55.fromJsonToPermissionArray(it).joinToString("\n") { p -> describe(p) } }, { Nip55Json.fromJsonToPermissionArray(it).joinToString("\n") { p -> describe(p) } })

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
    fun writesTheSame() {
        val perms =
            listOf(
                Permission(CommandType.SIGN_EVENT, 1),
                Permission(CommandType.NIP44_ENCRYPT),
                Permission(null, 3),
                Permission(null),
            )
        for (p in perms) assertEquals(JsonMapperNip55.toJson(p), Nip55Json.toJson(p))
        assertEquals(JsonMapperNip55.toJson(perms), Nip55Json.toJson(perms))
        assertEquals(JsonMapperNip55.toJson(emptyList<Permission>()), Nip55Json.toJson(emptyList()))

        val rs =
            listOf(
                IntentResult("pkg", "r", "{\"e\":\"\\n\"}", "1", false),
                IntentResult(),
                IntentResult(rejected = null),
                IntentResult(result = "\u0001 \u2028 \uD83D\uDE00", rejected = true),
            )
        for (r in rs) assertEquals(JsonMapperNip55.toJson(r), Nip55Json.toJson(r))
    }
}
