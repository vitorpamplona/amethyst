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
import com.vitorpamplona.quartz.nip01Core.links.props.LinkProps
import com.vitorpamplona.quartz.nip01Core.tags.aTag.AddressReferenceTag
import com.vitorpamplona.quartz.nip01Core.tags.events.GenericETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PubKeyReferenceTag

/**
 * Collects an event's links. A class's `links()` hands it values its Tag classes parsed (a
 * [GenericETag], a [PubKeyReferenceTag], an [AddressReferenceTag], or an id / address / value an
 * accessor returned): reading tag slots is the Tag classes' job, not the builder's or the
 * event's.
 *
 * Every relation takes only its declared props type ([Relation]`<P>`), so a mismatched pairing
 * does not compile. Targets are checked once more here (64-hex ids and keys, `kind:<64-hex>:d`
 * coordinates, non-blank values) as the last guard before a value becomes a node; hex is
 * lowercased so one key is one node, props with no value present are dropped, and exact
 * duplicates collapse, keeping the first occurrence's order.
 */
class LinkBuilder {
    private val links = LinkedHashSet<Link<*>>()

    fun <P : LinkProps> add(link: Link<P>) {
        links.add(link)
    }

    fun <P : LinkProps> event(
        relation: Relation<P>,
        id: String?,
        via: String? = null,
        props: P? = null,
    ) {
        val hex = normalizedHex(id) ?: return
        links.add(Link(relation, LinkTarget.Event(hex), via, props.orNull()))
    }

    fun <P : LinkProps> event(
        relation: Relation<P>,
        tag: GenericETag?,
        via: String? = null,
        props: P? = null,
    ) {
        if (tag != null) event(relation, tag.eventId, via, props)
    }

    fun <P : LinkProps> user(
        relation: Relation<P>,
        pubkey: String?,
        via: String? = null,
        props: P? = null,
    ) {
        val hex = normalizedHex(pubkey) ?: return
        links.add(Link(relation, LinkTarget.User(hex), via, props.orNull()))
    }

    fun <P : LinkProps> user(
        relation: Relation<P>,
        tag: PubKeyReferenceTag?,
        via: String? = null,
        props: P? = null,
    ) {
        if (tag != null) user(relation, tag.pubKey, via, props)
    }

    fun <P : LinkProps> address(
        relation: Relation<P>,
        address: String?,
        via: String? = null,
        props: P? = null,
    ) {
        val value = normalizedAddress(address) ?: return
        links.add(Link(relation, LinkTarget.Address(value), via, props.orNull()))
    }

    fun <P : LinkProps> address(
        relation: Relation<P>,
        address: Address?,
        via: String? = null,
        props: P? = null,
    ) {
        if (address != null) address(relation, address.toValue(), via, props)
    }

    fun <P : LinkProps> address(
        relation: Relation<P>,
        tag: AddressReferenceTag?,
        via: String? = null,
        props: P? = null,
    ) {
        if (tag != null) address(relation, tag.toAddressId(), via, props)
    }

    /** An event id or an address, told apart by shape: for slots that hold either (`q`, a NIP-22 scope). */
    fun <P : LinkProps> eventOrAddress(
        relation: Relation<P>,
        value: String?,
        via: String? = null,
        props: P? = null,
    ) {
        if (value == null) return
        if (value.length == 64) event(relation, value, via, props) else address(relation, value, via, props)
    }

    /**
     * A value that is not an event, a user or an address (a hashtag, a url, an external id, a
     * group id), parsed by its Tag class. [name] is that class's `TAG_NAME`: it says how to read
     * [value], and is part of the target's identity.
     */
    fun <P : LinkProps> tag(
        relation: Relation<P>,
        name: String,
        value: String?,
        via: String? = name,
        props: P? = null,
    ) {
        if (value.isNullOrBlank() || name.isEmpty()) return
        links.add(Link(relation, LinkTarget.Tag(name, value), via, props.orNull()))
    }

    // TRANSITIONAL raw-Map props overloads (see UntypedPropsShim.kt), deleted before the refactor lands.
    @Deprecated("Use the relation's typed props class")
    fun <P : LinkProps> event(
        relation: Relation<P>,
        id: String?,
        via: String? = null,
        props: Map<String, Any>?,
    ) = event(relation, id, via, untyped<P>(props))

    @Deprecated("Use the relation's typed props class")
    fun <P : LinkProps> user(
        relation: Relation<P>,
        pubkey: String?,
        via: String? = null,
        props: Map<String, Any>?,
    ) = user(relation, pubkey, via, untyped<P>(props))

    @Deprecated("Use the relation's typed props class")
    fun <P : LinkProps> address(
        relation: Relation<P>,
        address: String?,
        via: String? = null,
        props: Map<String, Any>?,
    ) = address(relation, address, via, untyped<P>(props))

    @Deprecated("Use the relation's typed props class")
    fun <P : LinkProps> eventOrAddress(
        relation: Relation<P>,
        value: String?,
        via: String? = null,
        props: Map<String, Any>?,
    ) = eventOrAddress(relation, value, via, untyped<P>(props))

    @Deprecated("Use the relation's typed props class")
    fun <P : LinkProps> tag(
        relation: Relation<P>,
        name: String,
        value: String?,
        via: String? = name,
        props: Map<String, Any>?,
    ) = tag(relation, name, value, via, untyped<P>(props))

    fun build(): List<Link<*>> = if (links.isEmpty()) emptyList() else links.toList()

    /** Props whose every value is absent are no props: `MemberProps()` qualifies nothing. */
    private fun <P : LinkProps> P?.orNull(): P? = this?.takeUnless { it.toMap().isEmpty() }

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
inline fun links(block: LinkBuilder.() -> Unit): List<Link<*>> = LinkBuilder().apply(block).build()
