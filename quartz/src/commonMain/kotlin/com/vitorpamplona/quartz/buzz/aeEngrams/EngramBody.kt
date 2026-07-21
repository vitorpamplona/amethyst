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
package com.vitorpamplona.quartz.buzz.aeEngrams

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The decrypted body of an Agent Engram (NIP-AE, `kind:30174`). The `slug`
 * discriminates the variant: `core` is the agent's identity surface, everything
 * else (`mem/…`) is one memory entry. Ground truth:
 * `buzz-core/src/engram.rs` (`Body`).
 *
 * Bodies are serialized whitespace-free with `slug` first, and — unlike the rest
 * of the Buzz payloads — `null` is written explicitly (a memory with `value:null`
 * is a **tombstone**, distinct from an absent field), so [JSON] keeps
 * `explicitNulls = true` to match Buzz's byte-exact encoding.
 */
sealed interface EngramBody {
    /** The slug this body addresses. */
    val slug: String

    fun encodeToJson(): String

    companion object {
        const val CORE_SLUG = "core"

        val JSON =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = true
                encodeDefaults = true
            }

        /**
         * Parses a decrypted engram body, dispatching on `slug`: a `core` slug
         * yields an [EngramCoreBody], anything else an [EngramMemoryBody]. Throws
         * if `slug` is missing or not a string.
         */
        fun decodeFromJson(json: String): EngramBody {
            val slug =
                JSON
                    .parseToJsonElement(json)
                    .jsonObject["slug"]
                    ?.jsonPrimitive
                    ?.content
                    ?: throw IllegalArgumentException("Engram body is missing a string `slug`")

            return if (slug == CORE_SLUG) {
                JSON.decodeFromString<EngramCoreBody>(json)
            } else {
                JSON.decodeFromString<EngramMemoryBody>(json)
            }
        }
    }
}

/**
 * A `mem/…` engram entry. [value] `null` is a tombstone (the entry was deleted),
 * which is encoded as an explicit `"value":null` on the wire.
 */
@Serializable
data class EngramMemoryBody(
    override val slug: String,
    val value: String? = null,
) : EngramBody {
    /** `true` for a tombstone — a memory whose [value] is `null`. */
    fun isTombstone(): Boolean = value == null

    override fun encodeToJson(): String = EngramBody.JSON.encodeToString(this)
}

/** The reserved `core` engram — the agent's free-form identity/profile surface. */
@Serializable
data class EngramCoreBody(
    override val slug: String = EngramBody.CORE_SLUG,
    val profile: String,
) : EngramBody {
    override fun encodeToJson(): String = EngramBody.JSON.encodeToString(this)
}
