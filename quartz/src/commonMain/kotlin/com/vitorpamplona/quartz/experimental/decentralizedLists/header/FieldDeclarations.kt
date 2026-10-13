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
package com.vitorpamplona.quartz.experimental.decentralizedLists.header

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.DisplayHint
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.DisplayHintTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.FieldTypeTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.TagRuleType
import com.vitorpamplona.quartz.nip01Core.core.TagArray

/** How strongly a header asks for a field. `allowed` and `optional` are the same level. */
enum class FieldRequirement {
    REQUIRED,
    RECOMMENDED,
    OPTIONAL,
}

/**
 * One field a list header declares for its items: the item tag it is read from, how strongly it
 * is asked for, the header's own words for it, and its `field-type` (text unless typed).
 */
@Immutable
data class FieldDeclaration(
    val name: String,
    val requirement: FieldRequirement,
    val description: String?,
    val type: String,
) {
    fun isUrl() = type == FieldTypeTag.URL

    /** A phone number (provisional): a `tel:` link when the value is one. */
    fun isTel() = type == FieldTypeTag.TEL

    /** An email address (provisional): a `mailto:` link when the value is one. */
    fun isEmail() = type == FieldTypeTag.EMAIL
}

private fun TagRuleType.requirement() =
    when (this) {
        TagRuleType.REQUIRED -> FieldRequirement.REQUIRED
        TagRuleType.RECOMMENDED -> FieldRequirement.RECOMMENDED
        TagRuleType.ALLOWED, TagRuleType.OPTIONAL -> FieldRequirement.OPTIONAL
        TagRuleType.DISALLOWED -> null
    }

/**
 * The fields a header declares, required first, then recommended, then optional, in header order
 * within each. A field declared twice keeps its strongest level at its first place, and
 * `disallowed` names are not fields. The same rules as Brainstorm's `parseFieldDecls`
 * (a port of Tapestry's reference reader), so the two apps draw one list the same way.
 */
fun TagArray.fieldDeclarations(): List<FieldDeclaration> {
    val byName = LinkedHashMap<String, Pair<Int, FieldDeclaration>>()
    tagRules().forEachIndexed { index, rule ->
        val requirement = rule.type.requirement() ?: return@forEachIndexed
        val previous = byName[rule.tagName]
        if (previous == null || requirement.ordinal < previous.second.requirement.ordinal) {
            byName[rule.tagName] = Pair(previous?.first ?: index, FieldDeclaration(rule.tagName, requirement, rule.description, FieldTypeTag.TEXT))
        }
    }

    val types = HashMap<String, String>()
    for (tag in this) {
        val type = FieldTypeTag.parse(tag) ?: continue
        if (type.field !in types) types[type.field] = type.type
    }

    return byName.values
        .sortedWith(compareBy({ it.second.requirement.ordinal }, { it.first }))
        .map { (_, decl) -> types[decl.name]?.let { decl.copy(type = it) } ?: decl }
}

/**
 * The header's presentation hints, read against its declared fields: hints naming a field the
 * header never declared are dropped; the first per role wins, except `fact`, which repeats.
 */
fun TagArray.displayHints(declared: Collection<String>): List<DisplayHint> {
    val out = ArrayList<DisplayHint>()
    // Sets rather than scans of [out], so a header with thousands of hints stays linear.
    val declaredSet = declared as? Set<String> ?: declared.toHashSet()
    val roles = HashSet<String>()
    val factFields = HashSet<String>()
    for (tag in this) {
        val hint = DisplayHintTag.parse(tag) ?: continue
        if (hint.field !in declaredSet) continue
        val isNew =
            if (hint.role == DisplayHintTag.FACT) {
                factFields.add(hint.field)
            } else {
                roles.add(hint.role)
            }
        if (isNew) out.add(hint)
    }
    return out
}
