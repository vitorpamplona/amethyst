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

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip01Core.links.props.LinkProps

// TRANSITIONAL — deleted before the typed-links refactor lands. Lets code that still passes a raw
// Map as props compile while each package moves to its relation's typed props class.

@Deprecated("Use the relation's typed props class")
data class UntypedProps(
    val map: Map<String, Any>,
) : LinkProps {
    override fun toMap() = map
}

@Suppress("UNCHECKED_CAST", "DEPRECATION")
internal fun <P : LinkProps> untyped(props: Map<String, Any>?): P? = props?.let { UntypedProps(it) as P }

@Deprecated("Use the relation's typed props class")
fun <P : LinkProps> LinkBuilder.eventTags(
    relation: Relation<P>,
    tags: TagArray,
    name: String = "e",
    props: Map<String, Any>?,
) = tags.fastForEach { if (it.size > 1 && it[0] == name) event(relation, it[1], name, untyped<P>(props)) }

@Deprecated("Use the relation's typed props class")
fun <P : LinkProps> LinkBuilder.userTags(
    relation: Relation<P>,
    tags: TagArray,
    name: String = "p",
    props: Map<String, Any>?,
) = tags.fastForEach { if (it.size > 1 && it[0] == name) user(relation, it[1], name, untyped<P>(props)) }

@Deprecated("Use the relation's typed props class")
fun <P : LinkProps> LinkBuilder.addressTags(
    relation: Relation<P>,
    tags: TagArray,
    name: String = "a",
    props: Map<String, Any>?,
) = tags.fastForEach { if (it.size > 1 && it[0] == name) address(relation, it[1], name, untyped<P>(props)) }

/** For golden tests still written with a raw Map. */
@Deprecated("Use the relation's typed props class")
@Suppress("DEPRECATION")
fun <P : LinkProps> Link(
    relation: Relation<P>,
    target: LinkTarget,
    via: String?,
    props: Map<String, Any>,
): Link<P> = Link(relation, target, via, untyped<P>(props))
