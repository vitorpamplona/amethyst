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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

/** How a list header constrains a tag on its children. The code is also the tag name. */
enum class TagRuleType(
    val code: String,
) {
    REQUIRED("required"),
    ALLOWED("allowed"),
    RECOMMENDED("recommended"),
    DISALLOWED("disallowed"),
    ;

    companion object {
        fun fromCode(code: String) =
            when (code) {
                REQUIRED.code -> REQUIRED
                ALLOWED.code -> ALLOWED
                RECOMMENDED.code -> RECOMMENDED
                DISALLOWED.code -> DISALLOWED
                else -> null
            }
    }
}

/**
 * One constraint a list header places on its items: `["required", "p"]`, optionally with a
 * human-readable description of what the named tag represents:
 * `["required", "p", "Pubkey of the person being endorsed"]`.
 *
 * The spec requires one tag per constrained name — `["required", "foo"], ["required", "bar"]`,
 * never `["required", "foo", "bar"]`. That makes index 2 unambiguous: it is always the
 * description, never a second tag name. The description is informational only.
 */
@Immutable
data class TagRule(
    val type: TagRuleType,
    val tagName: String,
    val description: String? = null,
) {
    fun toTagArray() = TagRuleTag.assemble(type, tagName, description)
}

class TagRuleTag {
    companion object {
        fun isTag(tag: Array<String>) = tag.has(1) && TagRuleType.fromCode(tag[0]) != null && tag[1].isNotEmpty()

        fun isTag(
            tag: Array<String>,
            type: TagRuleType,
        ) = tag.has(1) && tag[0] == type.code && tag[1].isNotEmpty()

        fun parse(tag: Array<String>): TagRule? {
            ensure(tag.has(1)) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            val type = TagRuleType.fromCode(tag[0]) ?: return null
            return TagRule(type, tag[1], tag.getOrNull(2)?.ifEmpty { null })
        }

        /** The constrained tag name when [tag] is a rule of [type]. Skips building the rule. */
        fun parseTagName(
            tag: Array<String>,
            type: TagRuleType,
        ): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == type.code) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return tag[1]
        }

        fun assemble(
            type: TagRuleType,
            tagName: String,
            description: String? = null,
        ) = arrayOfNotNull(type.code, tagName, description)

        fun assemble(rule: TagRule) = assemble(rule.type, rule.tagName, rule.description)

        fun assemble(rules: List<TagRule>) = rules.map { assemble(it) }
    }
}
