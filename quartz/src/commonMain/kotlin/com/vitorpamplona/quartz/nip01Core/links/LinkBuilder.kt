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
package com.vitorpamplona.quartz.nip01Core.links

import com.vitorpamplona.quartz.experimental.decentralizedLists.CoordinateShape
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.isValid

/**
 * Collects an event's links, validating each target once so no class has to: an event id or a
 * pubkey must be 64-char hex, an address a `kind:<64-hex>:d` coordinate, a tag value non-blank.
 * A value that fails is dropped, not linked: a malformed `p` must not become a user node. Hex is
 * lowercased (NIP-01 hex is lowercase; a mixed-case copy of a key would otherwise be a second
 * node). Exact duplicates collapse, keeping the first occurrence's order.
 */
class LinkBuilder {
    private val links = LinkedHashSet<Link>()

    fun add(link: Link) {
        links.add(link)
    }

    fun event(
        relation: Relation,
        id: String?,
        via: String? = null,
        props: Map<String, Any>? = null,
    ) {
        val hex = normalizedHex(id) ?: return
        links.add(Link(relation, LinkTarget.Event(hex), via, props))
    }

    fun user(
        relation: Relation,
        pubkey: String?,
        via: String? = null,
        props: Map<String, Any>? = null,
    ) {
        val hex = normalizedHex(pubkey) ?: return
        links.add(Link(relation, LinkTarget.User(hex), via, props))
    }

    fun address(
        relation: Relation,
        address: String?,
        via: String? = null,
        props: Map<String, Any>? = null,
    ) {
        val value = normalizedAddress(address) ?: return
        links.add(Link(relation, LinkTarget.Address(value), via, props))
    }

    fun address(
        relation: Relation,
        address: Address?,
        via: String? = null,
        props: Map<String, Any>? = null,
    ) {
        if (address == null) return
        address(relation, address.toValue(), via, props)
    }

    /** An event id or an address, told apart by shape: for slots that hold either (`q`, `e`/`a` pairs written as one value). */
    fun eventOrAddress(
        relation: Relation,
        value: String?,
        via: String? = null,
        props: Map<String, Any>? = null,
    ) {
        if (value == null) return
        if (value.length == 64) event(relation, value, via, props) else address(relation, value, via, props)
    }

    fun tag(
        relation: Relation,
        name: String,
        value: String?,
        via: String? = name,
        props: Map<String, Any>? = null,
    ) {
        if (value.isNullOrBlank() || name.isEmpty()) return
        links.add(Link(relation, LinkTarget.Tag(name, value), via, props))
    }

    fun build(): List<Link> = if (links.isEmpty()) emptyList() else links.toList()

    companion object {
        private fun hasUppercaseHex(value: String) = value.any { it in 'A'..'F' }

        fun normalizedHex(value: String?): String? {
            if (value == null || !value.isValid()) return null
            return if (hasUppercaseHex(value)) value.lowercase() else value
        }

        fun normalizedAddress(value: String?): String? {
            if (value == null || !CoordinateShape.matches(value)) return null
            val firstColon = value.indexOf(':')
            val pubkey = value.substring(firstColon + 1, firstColon + 65)
            if (!hasUppercaseHex(pubkey)) return value
            return value.substring(0, firstColon + 1) + pubkey.lowercase() + value.substring(firstColon + 65)
        }
    }
}

/** Builds a class's [LinkProvider.links]. */
inline fun links(block: LinkBuilder.() -> Unit): List<Link> = LinkBuilder().apply(block).build()
