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
package com.vitorpamplona.quartz.nip01Core.links.props

/**
 * Values that qualify one link and that a query filters on after choosing the relation: a
 * report's category, a zap's amount, a member's roles.
 *
 * Each [com.vitorpamplona.quartz.nip01Core.links.Relation] declares the one props type it
 * accepts (`Relation<ReportProps>`), so the builder rejects a mismatched pairing at compile
 * time and a consumer finds a relation's schema on its declaration. The props classes hold only
 * plain values (no NIP types), so this package depends on no NIP; the Tag classes that parse the
 * qualifiers produce them.
 *
 * [toMap] is the store form: the keys are the Nostr tag names where one exists (`rank`,
 * `report_raw`), values are strings, numbers, booleans or `List<String>`, and absent values are
 * left out.
 */
interface LinkProps {
    fun toMap(): Map<String, Any>
}

/** A relation whose links carry no qualifiers. */
object NoProps : LinkProps {
    override fun toMap(): Map<String, Any> = emptyMap()
}

/** [LinkProps.toMap] without the absent values: nulls and empty lists. */
internal fun propsOf(vararg entries: Pair<String, Any?>): Map<String, Any> {
    val map = LinkedHashMap<String, Any>(entries.size)
    for ((key, value) in entries) {
        if (value == null || (value is List<*> && value.isEmpty())) continue
        map[key] = value
    }
    return map
}
