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
package com.vitorpamplona.amethyst.commons.ui.note.types.dlists

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.decentralizedLists.description
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.FieldDeclaration
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.FieldRequirement
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.displayHints
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.fieldDeclarations
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.listImage
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.DisplayHint
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.DisplayHintTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.FieldTypeTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.isDeliberatelyUnaffiliated
import com.vitorpamplona.quartz.experimental.decentralizedLists.taggings.TagElementContent
import com.vitorpamplona.quartz.nip01Core.core.TagArray

/**
 * How a D-list header reads: its names, what it says it is, its picture, and the fields its items
 * carry. A header with no `names` falls back to `name`, then to its `d`, as Brainstorm's
 * `headerNames` does; a lone singular stands in for the plural.
 */
@Immutable
data class DListHeaderView(
    val singular: String,
    val plural: String,
    val description: String?,
    val image: String?,
    val fields: List<FieldDeclaration>,
    val hints: List<DisplayHint>,
    /** A copy withdrawn on purpose (`["b", "b-tag-deferred"]`): affiliated with no list. */
    val unaffiliated: Boolean,
) {
    fun hint(role: String) = hints.firstOrNull { it.role == role }?.field
}

/** One "label: value" line of an item. [label] null reads the field's name as words. */
@Immutable
data class DListFact(
    val field: String,
    val label: String?,
    val value: String,
    /** How many more values the item gives the same field. */
    val extra: Int,
    /**
     * What the value opens, when its field's declared type says and the value is one ([fieldHref]):
     * a `url` field's http(s) URL, a `tel` field's `tel:`, an `email` field's `mailto:`. Never
     * guessed from the value: an untyped field or an undeclared tag is text, whatever it looks like.
     */
    val href: String?,
)

/**
 * How a D-list item reads under its header (Brainstorm `lib/itemPresentation.ts`): the header's
 * display hints decide the title, summary, picture and link; without them the first required field
 * is the title and a declared `summary`/`description` is the summary. Every other field the item
 * fills in is a fact, declared fields first in header order, then the tags no field declares.
 */
@Immutable
data class DListItemView(
    val title: String?,
    val summary: String?,
    val image: String?,
    val link: String?,
    val facts: List<DListFact>,
    /** Required fields the item leaves empty. */
    val missing: List<String>,
)

/** The trimmed value when it is an http(s) URL. */
fun httpUrl(value: String?): String? {
    val trimmed = value?.trim() ?: return null
    val lower = trimmed.lowercase()
    if (!lower.startsWith("https://") && !lower.startsWith("http://")) return null
    return if (trimmed.length > lower.indexOf("://") + 3) trimmed else null
}

/**
 * A `tel` field's value as a call (provisional): its digits and a leading `+`, once the separators
 * people write (spaces, dots, dashes, brackets) are gone; null when anything else is left. The
 * header said it is a phone number; this only spells it the way a `tel:` link wants. Brainstorm's
 * `telHref` (`lib/dlistFields.ts`).
 */
fun telHref(value: String?): String? {
    val trimmed = value?.trim() ?: return null
    val cleaned = StringBuilder(trimmed.length)
    for (c in trimmed) {
        if (c.isWhitespace() || c == '(' || c == ')' || c == '.' || c == '-') continue
        cleaned.append(c)
    }
    if (cleaned.isEmpty()) return null
    val digitsFrom = if (cleaned[0] == '+') 1 else 0
    if (digitsFrom == cleaned.length) return null
    for (i in digitsFrom until cleaned.length) {
        if (cleaned[i] !in '0'..'9') return null
    }
    return "tel:$cleaned"
}

private val SINGLE_EMAIL = Regex("[^\\s@?#:]+@[^\\s@?#:]+\\.[^\\s@?#:]+")

/**
 * An `email` field's value as a mail (provisional): one address, `name@host.tld`, nothing around it
 * (no second address, no `?` headers, no `mailto:` of its own); null otherwise. Brainstorm's `emailHref`.
 */
fun emailHref(value: String?): String? {
    val trimmed = value?.trim() ?: return null
    return if (SINGLE_EMAIL.matches(trimmed)) "mailto:$trimmed" else null
}

/**
 * What a typed field's value opens: one obvious rendering per type, none for any other. A `url`
 * field links an http(s) value, a `tel` field calls, an `email` field mails. `text`, no type, or a
 * type no reader knows is text: a value's look never decides its type.
 */
fun fieldHref(
    type: String?,
    value: String,
): String? =
    when (type) {
        FieldTypeTag.URL -> httpUrl(value)
        FieldTypeTag.TEL -> telHref(value)
        FieldTypeTag.EMAIL -> emailHref(value)
        else -> null
    }

/** A field name as words: `opening-hours` -> "Opening hours". */
fun fieldWords(name: String): String =
    name
        .replace('-', ' ')
        .replace('_', ' ')
        .trim()
        .replaceFirstChar { it.uppercase() }

private fun TagArray.firstValue(name: String): String? = firstOrNull { it.size > 1 && it[0] == name && it[1].isNotBlank() }?.get(1)

/**
 * Every non-blank value of an item's tags, by tag name, read in one pass: a header can declare
 * hundreds of fields and an item carry thousands of tags, and looking each field up with its own
 * scan multiplies the two.
 */
private fun TagArray.valuesByName(): Map<String, List<String>> {
    val out = HashMap<String, MutableList<String>>()
    for (tag in this) {
        if (tag.size > 1 && tag[1].isNotBlank()) out.getOrPut(tag[0]) { ArrayList(1) }.add(tag[1])
    }
    return out
}

fun dListHeaderView(
    tags: TagArray,
    dTag: String?,
): DListHeaderView {
    val names = tags.firstOrNull { it.size > 1 && it[0] == "names" && it[1].isNotBlank() }
    val singular = names?.get(1) ?: tags.firstValue("name") ?: dTag ?: ""
    val plural = names?.getOrNull(2)?.ifBlank { null } ?: singular
    val fields = tags.fieldDeclarations()
    return DListHeaderView(
        singular = singular,
        plural = plural,
        description = tags.description(),
        image = httpUrl(tags.listImage()),
        fields = fields,
        hints = tags.displayHints(fields.map { it.name }),
        unaffiliated = tags.isDeliberatelyUnaffiliated(),
    )
}

/** Protocol tags and NIP metadata every event may carry: never fields. */
private val METADATA_TAGS = setOf("alt", "client", "expiration", "json", "polarity")

/** The item-level tags an item reader shows on their own, not as facts. */
private val ITEM_TEXT_TAGS = setOf("name", "title", "description", "comments", "slug")

/**
 * [dListItemView] for a whole event: a Tapestry tag element keeps its name and description in a
 * JSON `content` (`{"tag": {"slug", "name", "description"}}`) rather than in tags, so when the
 * tags give no title the content is read for one.
 */
fun dListItemView(
    item: TagArray,
    content: String,
    header: DListHeaderView?,
): DListItemView {
    val view = dListItemView(item, header)
    if (view.title != null || !content.startsWith("{")) return view
    val tag = TagElementContent.parse(content)?.tag ?: return view
    return view.copy(
        title = tag.name?.ifBlank { null } ?: tag.slug.ifBlank { null },
        summary = view.summary ?: tag.description?.ifBlank { null },
    )
}

fun dListItemView(
    item: TagArray,
    header: DListHeaderView?,
): DListItemView {
    if (header == null || header.fields.isEmpty()) return undeclaredItemView(item)

    val fields = header.fields
    val declared = fields.associateBy { it.name }
    val values = item.valuesByName()

    fun valueOf(field: String?): String? = field?.takeIf { it in declared }?.let { values[it]?.firstOrNull() }

    val titleField = header.hint(DisplayHintTag.TITLE) ?: fields.firstOrNull { it.requirement == FieldRequirement.REQUIRED }?.name ?: fields.first().name
    val summaryField =
        header.hint(DisplayHintTag.SUMMARY)
            ?: listOf("summary", "description").firstOrNull { it in declared && it != titleField }
    val imageField = header.hint(DisplayHintTag.IMAGE)
    val linkField = header.hint(DisplayHintTag.LINK)

    // Each field shows once: the ones a role already draws are not facts as well.
    val shown = setOfNotNull(titleField, summaryField, imageField, linkField)

    val factHints = header.hints.filter { it.role == DisplayHintTag.FACT && it.field !in shown }
    val labels = factHints.associate { it.field to it.label }
    // Hinted facts first, in the header's order; then every other declared field the item fills in.
    // Single-letter fields (`t`, `g`, `p`...) are protocol tags the card draws its own way, and
    // `alt` restates the rest, so neither is listed as a fact even when a header declares it.
    val order =
        (factHints.map { it.field } + fields.map { it.name }.filter { it !in shown && it !in labels })
            .filter { it.length > 1 && it !in METADATA_TAGS }

    val facts =
        order.mapNotNull { name ->
            val fieldValues = values[name] ?: return@mapNotNull null
            val value = fieldValues.firstOrNull() ?: return@mapNotNull null
            DListFact(
                field = name,
                label = labels[name],
                value = value,
                extra = fieldValues.size - 1,
                href = fieldHref(declared[name]?.type, value),
            )
        } + undeclaredFacts(item, declared.keys)

    return DListItemView(
        title = valueOf(titleField),
        summary = if (summaryField == titleField) null else valueOf(summaryField),
        image = httpUrl(valueOf(imageField)),
        link = httpUrl(valueOf(linkField)),
        facts = facts,
        missing = fields.filter { it.requirement == FieldRequirement.REQUIRED && values[it.name] == null }.map { it.name },
    )
}

/** An item whose header is unknown or declares nothing: its own name, title and description, and its other tags as facts. */
private fun undeclaredItemView(item: TagArray): DListItemView =
    DListItemView(
        title = item.firstValue("name") ?: item.firstValue("title") ?: item.firstValue("t"),
        summary = item.firstValue("description") ?: item.firstValue("comments"),
        image = null,
        link = null,
        facts = undeclaredFacts(item, ITEM_TEXT_TAGS),
        missing = emptyList(),
    )

/**
 * The item's tags its header never declared, in tag order. Single-letter tags are the protocol's
 * (`d`, `z`, `p`, `e`, `t`, `g`, ...), not fields, and are left out, as is NIP metadata.
 */
private fun undeclaredFacts(
    item: TagArray,
    declared: Set<String>,
): List<DListFact> {
    // Keyed by field, in first-seen order, so repeats are counted without rescanning the list.
    val out = LinkedHashMap<String, DListFact>()
    for (tag in item) {
        if (tag.size < 2) continue
        val name = tag[0]
        if (name.length < 2 || name in declared || name in METADATA_TAGS) continue
        val value = tag[1]
        if (value.isBlank()) continue
        val existing = out[name]
        out[name] =
            if (existing != null) {
                existing.copy(extra = existing.extra + 1)
            } else {
                // Undeclared, so untyped: text, even when it looks like a link.
                DListFact(name, null, value, 0, null)
            }
    }
    return out.values.toList()
}
