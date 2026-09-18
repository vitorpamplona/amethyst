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
package com.vitorpamplona.contextvm.cep15CommonSchemas

import com.vitorpamplona.contextvm.core.CvmTags
import com.vitorpamplona.contextvm.json.toPlainJson
import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.jcs.JsonCanonicalization
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * CEP-15 common tool schemas.
 *
 * Two servers implementing the same tool interface produce the same
 * `schemaHash`, so a client can recognise an equivalent service across
 * providers and switch between them without code changes. That only works if
 * documentation differences are stripped before hashing, which is what
 * [normalize] does.
 *
 * The rule that matters most on the client side: the advertised hash is a
 * **verification target, not a label**. [verify] recomputes it from the tool
 * definition; trusting the advertised value would give up everything the CEP
 * provides.
 */
object CommonToolSchema {
    const val META_NAMESPACE = CvmTags.COMMON_SCHEMA_NAMESPACE
    const val SCHEMA_HASH = "schemaHash"

    const val NAME = "name"
    const val INPUT_SCHEMA = "inputSchema"
    const val OUTPUT_SCHEMA = "outputSchema"
    const val META = "_meta"

    /**
     * Annotation and documentation keywords removed at every nesting level.
     *
     * These carry no structural meaning, so two providers describing the same
     * interface differently must still agree on the hash.
     */
    val ANNOTATION_KEYWORDS =
        setOf(
            "title",
            "description",
            "examples",
            "default",
            "deprecated",
            "readOnly",
            "writeOnly",
        )

    /** Vendor extensions are stripped too, by prefix. */
    const val VENDOR_PREFIX = "x-"

    /**
     * Strips annotation and vendor keywords recursively.
     *
     * This applies only to the hashed representation — the tool definition a
     * server actually returns from `tools/list` is untouched.
     */
    fun normalize(schema: JsonElement): JsonElement =
        when (schema) {
            is JsonObject ->
                buildJsonObject {
                    schema.forEach { (key, value) ->
                        if (key !in ANNOTATION_KEYWORDS && !key.startsWith(VENDOR_PREFIX)) {
                            put(key, normalize(value))
                        }
                    }
                }

            is JsonArray -> buildJsonArray { schema.forEach { add(normalize(it)) } }

            else -> schema
        }

    /**
     * The schema hash: `sha256(JCS({name, inputSchema, outputSchema?}))`, hex.
     *
     * The tool name is part of the payload on purpose — MCP invokes tools by
     * name, so a shared hash is only useful if the name is shared too.
     */
    fun hash(
        name: String,
        inputSchema: JsonElement,
        outputSchema: JsonElement? = null,
    ): String {
        val payload =
            buildMap<String, Any?> {
                put(NAME, name)
                put(INPUT_SCHEMA, normalize(inputSchema).toPlainJson())
                // Presence changes the hash, so an omitted output schema is not
                // the same as an empty one.
                if (outputSchema != null) put(OUTPUT_SCHEMA, normalize(outputSchema).toPlainJson())
            }

        return sha256(JsonCanonicalization.canonicalize(payload).encodeToByteArray()).toHexKey()
    }

    /** Computes the hash from a `tools/list` tool definition. */
    fun hashOf(tool: JsonObject): String {
        val name =
            (tool[NAME] as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: throw IllegalArgumentException("tool definition requires a name")
        val input = tool[INPUT_SCHEMA] ?: throw IllegalArgumentException("tool definition requires an inputSchema")
        return hash(name, input, tool[OUTPUT_SCHEMA])
    }

    /** The hash a server claims in `_meta`, or null when the tool is bespoke. */
    fun advertisedHash(tool: JsonObject): String? {
        val meta = tool[META] as? JsonObject ?: return null
        val namespace = meta[META_NAMESPACE] as? JsonObject ?: return null
        return (namespace[SCHEMA_HASH] as? JsonPrimitive)?.takeIf { it.isString }?.content
    }

    /**
     * True when the tool advertises a common schema whose hash matches what its
     * own definition produces.
     *
     * Returns false for a mismatch rather than throwing: a server advertising a
     * wrong hash is a tool to ignore, not a session to fail.
     */
    fun verify(tool: JsonObject): Boolean {
        val advertised = advertisedHash(tool) ?: return false
        return advertised.equals(hashOf(tool), ignoreCase = true)
    }

    /** Builds the `_meta` block a server attaches to a common-schema tool. */
    fun metaFor(schemaHash: String): JsonObject =
        buildJsonObject {
            put(
                META_NAMESPACE,
                buildJsonObject { put(SCHEMA_HASH, JsonPrimitive(schemaHash)) },
            )
        }

    /** NIP-73 `["i", "<hash>", "<tool>"]` marker for an implemented schema. */
    fun externalIdTag(
        schemaHash: String,
        toolName: String,
    ): Tag = arrayOf(CvmTags.EXTERNAL_ID, schemaHash, toolName)

    /** NIP-73 `["k", "io.contextvm/common-schema"]`; one per announcement event. */
    fun externalKindTag(): Tag = arrayOf(CvmTags.EXTERNAL_KIND, META_NAMESPACE)

    /** Reads `(schemaHash, toolName)` pairs off an announcement's `i` tags. */
    fun parseExternalIds(tags: Array<Tag>): List<Pair<String, String?>> =
        tags
            .filter { it.size >= 2 && it[0] == CvmTags.EXTERNAL_ID }
            .map { it[1] to it.getOrNull(2) }
}
