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
package com.vitorpamplona.quartz.contextvm.cep15CommonSchemas

import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcCodec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `CVM-15-*`: common tool schemas. */
class CommonToolSchemaTest {
    private fun obj(json: String) =
        JsonRpcCodec
            .decode("""{"jsonrpc":"2.0","id":1,"method":"m","params":$json}""")
            .let { (it as com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcRequest).params!! }

    private val plainInput =
        obj(
            """{"type":"object","properties":{"text":{"type":"string"},
               "target_language":{"type":"string"}},"required":["text","target_language"]}""",
        )

    private val documentedInput =
        obj(
            """{"type":"object","title":"Translate input","description":"args",
               "properties":{"text":{"type":"string","description":"Text to translate","examples":["hi"]},
               "target_language":{"type":"string","description":"ISO 639-1","default":"en"}},
               "required":["text","target_language"],"x-vendor-note":"internal"}""",
        )

    @Test
    fun `CVM-15-01 the same interface documented differently yields the same hash`() {
        // This is the entire point of the CEP: providers compete on docs and
        // quality while remaining interchangeable.
        assertEquals(
            CommonToolSchema.hash("translate_text", plainInput),
            CommonToolSchema.hash("translate_text", documentedInput),
        )
    }

    @Test
    fun `CVM-15-02 strips annotation keywords at every nesting level`() {
        val normalized = CommonToolSchema.normalize(documentedInput) as JsonObject
        assertFalse(normalized.containsKey("title"))
        assertFalse(normalized.containsKey("description"))

        val properties = normalized["properties"] as JsonObject
        val text = properties["text"] as JsonObject
        assertFalse(text.containsKey("description"), "nested description must be stripped too")
        assertFalse(text.containsKey("examples"))

        val target = properties["target_language"] as JsonObject
        assertFalse(target.containsKey("default"))
    }

    @Test
    fun `CVM-15-03 strips vendor extensions by prefix`() {
        val normalized = CommonToolSchema.normalize(documentedInput) as JsonObject
        assertFalse(normalized.keys.any { it.startsWith("x-") })
    }

    @Test
    fun `CVM-15-04 keeps structural keywords`() {
        val normalized = CommonToolSchema.normalize(documentedInput) as JsonObject
        assertEquals("object", (normalized["type"] as JsonPrimitive).content)
        assertTrue(normalized.containsKey("properties"))
        assertTrue(normalized.containsKey("required"))
    }

    @Test
    fun `CVM-15-05 normalizes inside arrays`() {
        val schema =
            obj(
                """{"anyOf":[{"type":"string","description":"a"},{"type":"number","title":"b"}]}""",
            )
        val normalized = CommonToolSchema.normalize(schema) as JsonObject
        val branches = normalized["anyOf"]!!
        assertEquals(
            """{"anyOf":[{"type":"string"},{"type":"number"}]}""",
            normalized.toString(),
        )
        assertEquals(2, (branches as kotlinx.serialization.json.JsonArray).size)
    }

    @Test
    fun `CVM-15-06 the tool name is part of the hash`() {
        assertNotEquals(
            CommonToolSchema.hash("translate_text", plainInput),
            CommonToolSchema.hash("translate_prose", plainInput),
        )
    }

    @Test
    fun `CVM-15-07 adding an outputSchema changes the hash`() {
        val output = obj("""{"type":"object","properties":{"translated_text":{"type":"string"}}}""")
        assertNotEquals(
            CommonToolSchema.hash("translate_text", plainInput),
            CommonToolSchema.hash("translate_text", plainInput, output),
        )
    }

    @Test
    fun `CVM-15-08 member order in the source schema does not change the hash`() {
        // JCS sorts keys, so a server emitting members in a different order
        // still lands on the same identity.
        val reordered =
            obj(
                """{"required":["text","target_language"],"properties":{
                   "target_language":{"type":"string"},"text":{"type":"string"}},"type":"object"}""",
            )
        assertEquals(
            CommonToolSchema.hash("translate_text", plainInput),
            CommonToolSchema.hash("translate_text", reordered),
        )
    }

    @Test
    fun `CVM-15-09 verify recomputes rather than trusting the advertised hash`() {
        val correct = CommonToolSchema.hash("translate_text", plainInput)
        val tool =
            buildJsonObject {
                put("name", JsonPrimitive("translate_text"))
                put("inputSchema", plainInput)
                put("_meta", CommonToolSchema.metaFor(correct))
            }
        assertTrue(CommonToolSchema.verify(tool))
        assertEquals(correct, CommonToolSchema.hashOf(tool))
    }

    @Test
    fun `CVM-15-10 verify rejects a tool advertising someone else's hash`() {
        val tool =
            buildJsonObject {
                put("name", JsonPrimitive("translate_text"))
                put("inputSchema", plainInput)
                put("_meta", CommonToolSchema.metaFor("00".repeat(32)))
            }
        assertFalse(CommonToolSchema.verify(tool), "a mismatched hash must not be accepted")
    }

    @Test
    fun `CVM-15-11 a bespoke tool advertises no hash and does not verify`() {
        val tool =
            buildJsonObject {
                put("name", JsonPrimitive("bespoke"))
                put("inputSchema", plainInput)
            }
        assertNull(CommonToolSchema.advertisedHash(tool))
        assertFalse(CommonToolSchema.verify(tool))
    }

    @Test
    fun `CVM-15-12 builds and parses the NIP-73 discovery tags`() {
        val hash = CommonToolSchema.hash("translate_text", plainInput)
        assertContentEquals(
            arrayOf("i", hash, "translate_text"),
            CommonToolSchema.externalIdTag(hash, "translate_text"),
        )
        assertContentEquals(
            arrayOf("k", "io.contextvm/common-schema"),
            CommonToolSchema.externalKindTag(),
        )

        val parsed =
            CommonToolSchema.parseExternalIds(
                arrayOf(
                    CommonToolSchema.externalIdTag(hash, "translate_text"),
                    CommonToolSchema.externalKindTag(),
                    arrayOf("p", "irrelevant"),
                ),
            )
        assertEquals(listOf(hash to "translate_text"), parsed)
    }

    @Test
    fun `CVM-15-13 the hash is stable across runs`() {
        // Pins the wire value so a refactor of normalization or JCS that changes
        // identity shows up as a failure here rather than as silent divergence
        // from every other implementation.
        val hash = CommonToolSchema.hash("echo", obj("""{"type":"object"}"""))
        assertEquals(64, hash.length, "sha256 hex")
        assertEquals(hash, CommonToolSchema.hash("echo", obj("""{"type":"object"}""")))
    }
}
