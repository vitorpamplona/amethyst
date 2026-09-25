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
package com.vitorpamplona.quartz.experimental.decentralizedLists.concepts

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * The parsed `json` tag of a Tapestry node: a JSON object keyed by section. Every node in the
 * word-wrapper layout has a [word] section plus one or more role sections (`conceptHeader`,
 * `superset`, `set`, `property`, `graph`, `conceptGraph`, …), readable raw via [section].
 * Plain elements use the same object, keyed by concept slug (`{"dog": {...}}`).
 */
@Immutable
class WordWrapper(
    val root: JsonObject,
) {
    /** One section as raw JSON, or null when absent or not an object. */
    fun section(key: String) = root[key] as? JsonObject

    fun word(): WordSection? = decode(WORD)

    fun conceptHeader(): ConceptHeaderSection? = decode(CONCEPT_HEADER)

    private inline fun <reified T> decode(key: String): T? {
        val element = section(key) ?: return null
        return runCatching { JsonMapper.jsonInstance.decodeFromJsonElement<T>(element) }.getOrNull()
    }

    companion object {
        const val WORD = "word"
        const val CONCEPT_HEADER = "conceptHeader"

        /** Null for anything that is not a JSON object: the tag is authored data, not trusted. */
        fun parse(json: String): WordWrapper? = runCatching { WordWrapper(JsonMapper.jsonInstance.parseToJsonElement(json).jsonObject) }.getOrNull()
    }
}

/** A reference to another node inside a word-wrapper payload. `uuid` carries its a-tag address. */
@Immutable
@Serializable
data class NodeRef(
    val slug: String? = null,
    val uuid: String? = null,
)

/** The universal `word` section. */
@Immutable
@Serializable
data class WordSection(
    val slug: String? = null,
    val name: String? = null,
    val title: String? = null,
    // Nullable: this is authored JSON, and an explicit `null` must read as "absent" rather
    // than fail the whole section. Use [types] / [memberOf] for the non-null view.
    val wordTypes: List<String>? = null,
    /** Set on a concept's core nodes, pointing back at the concept; omitted by the header itself. */
    val coreMemberOf: List<NodeRef>? = null,
) {
    fun types() = wordTypes.orEmpty()

    fun memberOf() = coreMemberOf.orEmpty()
}

@Immutable
@Serializable
data class SingularPluralJson(
    val singular: String? = null,
    val plural: String? = null,
)

/** The `conceptHeader` section of a Concept Header node. */
@Immutable
@Serializable
data class ConceptHeaderSection(
    val description: String? = null,
    val oNames: SingularPluralJson? = null,
    val oSlugs: SingularPluralJson? = null,
    val oKeys: SingularPluralJson? = null,
    val oTitles: SingularPluralJson? = null,
    val oLabels: SingularPluralJson? = null,
)
