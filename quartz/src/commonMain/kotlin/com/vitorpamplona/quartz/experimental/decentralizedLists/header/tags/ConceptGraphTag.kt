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
package com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * Tapestry Concepts: `["concept-graph", "39999:<pubkey>:<d>-concept-graph"]` on a kind 39998
 * concept header, pointing at the concept's Concept Graph core node.
 *
 * The value is computed from the header's own pubkey and `d`, never looked up, so it is right
 * even before that node exists — and a reader facing a header without the tag computes the
 * same address ([compute]).
 */
class ConceptGraphTag {
    companion object {
        const val TAG_NAME = "concept-graph"
        const val SUFFIX = "-concept-graph"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        fun parse(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return tag[1]
        }

        /** The Concept Graph address of the concept whose header is `39998:<pubKey>:<dTag>`. */
        fun compute(
            pubKey: HexKey,
            dTag: String,
        ) = Address.assemble(CONCEPT_GRAPH_NODE_KIND, pubKey, dTag + SUFFIX)

        fun assemble(address: String) = arrayOf(TAG_NAME, address)

        fun assemble(
            pubKey: HexKey,
            dTag: String,
        ) = assemble(compute(pubKey, dTag))

        /** Core nodes are kind 39999 items. */
        const val CONCEPT_GRAPH_NODE_KIND = 39999
    }
}
