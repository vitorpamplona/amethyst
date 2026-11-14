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
package com.vitorpamplona.amethyst.commons.nipsOnNostr

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent

/** A kind a NIP defines: its number and, when the `k` tag carries one, the NIP's own name for it. */
@Immutable
data class NipDefinedKind(
    val kind: Int,
    val label: String?,
)

/**
 * What the screens draw for a kind-30817 NIP ("NIPs on Nostr"), worked out once per event.
 *
 * [body] is the Markdown to render: the content without the leading headings that repeat
 * [title] (NostrHub and the nips repo both open with them), and with relative links to other
 * NIPs (`44.md`) pointed at the nips repo, where they resolve. [preview] is the opening prose
 * as plain text, for the feed card when the NIP has no summary.
 */
@Immutable
class NipTextView(
    val number: String?,
    val title: String?,
    val summary: String?,
    val status: String?,
    val flags: List<String>,
    val kinds: List<NipDefinedKind>,
    val body: String,
    val preview: String,
) {
    companion object {
        fun of(event: NipTextEvent): NipTextView {
            val title = event.title()?.trim()?.ifBlank { null }
            val stripped = stripLeadingTitle(event.content, title)
            val status = event.tagValue("status")?.trim()?.ifBlank { null }
            return NipTextView(
                number = stripped.number,
                title = title ?: stripped.firstHeading,
                // The tag, not NipTextEvent.summary(): that one guesses from the content by
                // skipping its first line, which drops the real opening of a NIP with no heading.
                summary = event.tagValue("summary")?.trim()?.ifBlank { null },
                status = status,
                flags = stripped.flags.filterNot { it.equals(status, ignoreCase = true) },
                kinds = definedKinds(event.tags),
                body = linkRelativeNips(stripped.body),
                preview = plainPreview(stripped.body),
            )
        }
    }
}

const val NIPS_REPO_BLOB = "https://github.com/nostr-protocol/nips/blob/master/"

private fun NipTextEvent.tagValue(name: String): String? {
    for (tag in tags) {
        if (tag.size > 1 && tag[0] == name) return tag[1]
    }
    return null
}

/** The `k` tags, once per kind, keeping the first non-blank name any of them gives it. */
internal fun definedKinds(tags: Array<Array<String>>): List<NipDefinedKind> {
    val byKind = LinkedHashMap<Int, String?>()
    for (tag in tags) {
        if (tag.size < 2 || tag[0] != "k") continue
        val kind = tag[1].trim().toIntOrNull() ?: continue
        val label = tag.getOrNull(2)?.trim()?.ifBlank { null }
        if (byKind[kind] == null) byKind[kind] = label
    }
    return byKind.map { (kind, label) -> NipDefinedKind(kind, label) }
}

internal class StrippedNip(
    val number: String?,
    val firstHeading: String?,
    val flags: List<String>,
    val body: String,
)

private val NIP_NUMBER = Regex("^NIP[- ]?([0-9A-Za-z]{2,3})$", RegexOption.IGNORE_CASE)
private val ATX_HEADING = Regex("^#{1,6}\\s+(.*?)\\s*#*\\s*$")
private val SETEXT_UNDERLINE = Regex("^\\s*(=+|-+)\\s*$")

/**
 * Drops the headings a NIP opens with when they repeat what the header already shows: a
 * `NIP-XX` heading (its number goes to [StrippedNip.number]) and a heading equal to [title].
 * Both ATX (`# Title`) and setext (`Title` over `=====`) headings count. Any other opening
 * heading stays, since it is part of the text. A status line right under them
 * (`` `draft` `optional` ``) moves to [StrippedNip.flags].
 */
internal fun stripLeadingTitle(
    content: String,
    title: String?,
): StrippedNip {
    val lines = content.lines()
    var i = 0
    var number: String? = null
    var firstHeading: String? = null

    fun skipBlank() {
        while (i < lines.size && lines[i].isBlank()) i++
    }

    // Two passes at most: `NIP-XX` then the title, as the nips repo writes them.
    repeat(2) {
        skipBlank()
        if (i >= lines.size) return@repeat
        val line = lines[i]
        val atx = ATX_HEADING.find(line.trim())
        val (text, consumed) =
            when {
                atx != null -> atx.groupValues[1].trim() to 1
                i + 1 < lines.size && line.isNotBlank() && SETEXT_UNDERLINE.matches(lines[i + 1]) -> line.trim() to 2
                else -> return@repeat
            }

        val nip = NIP_NUMBER.find(text)
        when {
            nip != null && number == null -> {
                number = nip.groupValues[1].uppercase()
                i += consumed
            }

            title != null && text.equals(title, ignoreCase = true) -> {
                i += consumed
            }

            title == null && firstHeading == null && number != null -> {
                // No title tag: the heading after `NIP-XX` is the title, so the header shows it.
                firstHeading = text
                i += consumed
            }

            else -> {
                return@repeat
            }
        }
    }

    skipBlank()

    // The nips repo's status line, `draft` `optional`, right under the headings.
    // Only single words count, so an opening line of inline code (`kind:1`) stays in the text.
    var flags = emptyList<String>()
    if (i < lines.size && STATUS_LINE.matches(lines[i].trim())) {
        val words = STATUS_FLAG.findAll(lines[i]).map { it.groupValues[1].trim() }.toList()
        if (words.all { FLAG_WORD.matches(it) }) {
            flags = words
            i++
            skipBlank()
        }
    }

    return StrippedNip(number, firstHeading, flags, lines.drop(i).joinToString("\n").trimEnd())
}

// `](44.md)`, `](44.md#section)` and the reference form `[NIP-44]: 44.md`.
private val RELATIVE_NIP_LINK = Regex("(\\]\\(|^\\s{0,3}\\[[^\\]]+\\]:\\s*)(\\.?/?)([0-9A-Fa-f]{2}\\.md(?:#[^)\\s]*)?)", RegexOption.MULTILINE)

/** Points relative links to other NIPs (`[NIP-44](44.md)`) at the nips repo, where they resolve. */
internal fun linkRelativeNips(markdown: String): String = RELATIVE_NIP_LINK.replace(markdown) { m -> m.groupValues[1] + NIPS_REPO_BLOB + m.groupValues[3] }

private val STATUS_LINE = Regex("^(`[^`]+`\\s*)+$")
private val STATUS_FLAG = Regex("`([^`]+)`")
private val FLAG_WORD = Regex("^[A-Za-z][A-Za-z-]{0,23}$")
private val INLINE_LINK = Regex("!?\\[([^\\]]*)\\](\\([^)]*\\)|\\[[^\\]]*\\])")
private val EMPHASIS = Regex("(\\*\\*|__|\\*|_|~~)(\\S(?:.*?\\S)?)\\1")

/**
 * The first paragraph of prose, as plain text: no headings, no `draft` `optional` status line,
 * no code, lists or tables, and inline Markdown reduced to its text.
 */
internal fun plainPreview(markdown: String): String {
    val paragraph = StringBuilder()
    var inFence = false
    for (raw in markdown.lines()) {
        val line = raw.trim()
        if (line.startsWith("```") || line.startsWith("~~~")) {
            inFence = !inFence
            if (paragraph.isNotEmpty()) break
            continue
        }
        if (inFence) continue
        if (line.isEmpty()) {
            if (paragraph.isNotEmpty()) break
            continue
        }
        val isBlock =
            line.startsWith("#") ||
                line.startsWith("|") ||
                line.startsWith(">") ||
                line.startsWith("[") && line.contains("]:") ||
                SETEXT_UNDERLINE.matches(line) ||
                STATUS_LINE.matches(line)
        if (isBlock) {
            if (paragraph.isNotEmpty()) break
            continue
        }
        if (paragraph.isNotEmpty()) paragraph.append(' ')
        paragraph.append(line.removePrefix("- ").removePrefix("* "))
    }

    return paragraph
        .toString()
        .replace(INLINE_LINK) { it.groupValues[1] }
        .replace(EMPHASIS) { it.groupValues[2] }
        .replace("`", "")
}
