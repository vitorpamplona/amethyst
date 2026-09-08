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
package com.vitorpamplona.quartz.experimental.publications

/**
 * Converts the common AsciiDoc subset into Markdown, so NKBIP-01 publication sections can be
 * rendered by a CommonMark renderer instead of shipping an AsciiDoc engine.
 *
 * **Why a converter rather than a real AsciiDoc processor.** The reference implementation
 * (imwald-android) runs Asciidoctor.js in a headless WebView, because JRuby/AsciidoctorJ cannot
 * run on Android. That is a defensible choice for an Android-only app and the wrong one here:
 * it is Android-only (Quartz targets JVM, iOS and native too), it adds a vendored JS bundle, and
 * it puts a WebView on the text-rendering path of every article. This converter is pure Kotlin
 * in commonMain, has no dependency, and hands the result to the CommonMark renderer Amethyst
 * already uses for kind-30023 long-form.
 *
 * **The trade is fidelity.** This handles the constructs that actually appear in prose —
 * headings, lists, emphasis, links, images, source/literal blocks, block quotes, admonitions.
 * It does not handle tables, includes, conditionals, cross-references or attribute substitution.
 * Anything it does not recognize is **passed through unchanged**, which is the important
 * property: an unconverted construct degrades to the plain text it already was, and never to
 * mangled output.
 *
 * **Literal blocks are sacred.** Every inline rewrite is skipped inside `----`, `....` and
 * `++++` blocks, so a code sample that contains `*stars*` or `[[brackets]]` survives verbatim.
 * Getting this wrong is the classic way these converters corrupt documents.
 */
object AsciiDocToMarkdown {
    /**
     * @param content the section body as published.
     * @param resolveWikilink maps a `[[target]]` to a URI, or null to leave the target as plain
     *   text. Kept as a callback so this file stays free of nostr addressing and UI concerns.
     */
    fun convert(
        content: String,
        resolveWikilink: (String) -> String? = { null },
    ): String {
        if (content.isBlank()) return content

        val out = StringBuilder(content.length + 64)
        val lines = content.split('\n')
        var i = 0
        var openFence: String? = null
        // `[source,kotlin]` sits on the line above the `----` it describes, so the language has
        // to be carried forward one line. A local, never a field: this object is shared and two
        // concurrent conversions would otherwise trade languages.
        var pendingLanguage: String? = null

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trimEnd()

            // Inside a verbatim block: copy until the matching delimiter, rewriting nothing.
            if (openFence != null) {
                if (trimmed.trimStart() == openFence) {
                    out.append("```")
                    openFence = null
                } else {
                    out.append(line)
                }
                out.append('\n')
                i++
                continue
            }

            val delimiter = verbatimDelimiterOf(trimmed)
            if (delimiter != null) {
                // A `[source,kotlin]` attribute line immediately above names the language.
                out.append("```").append(pendingLanguage ?: "").append('\n')
                pendingLanguage = null
                openFence = delimiter
                i++
                continue
            }

            // `[source,kotlin]`, `[quote]`, `[NOTE]` and friends: block attribute lines. They
            // carry no text of their own; the language is picked up above, the rest is dropped
            // rather than printed as literal brackets.
            if (BLOCK_ATTRIBUTE.matches(trimmed)) {
                pendingLanguage = sourceLanguageOf(trimmed) ?: pendingLanguage
                i++
                continue
            }

            // Document attributes (`:toc:`, `:author: X`) and line comments are metadata.
            if (DOC_ATTRIBUTE.matches(trimmed) || trimmed.startsWith("//")) {
                i++
                continue
            }

            // A quote block's `____` fences become a Markdown blockquote over its lines.
            if (trimmed.trimStart() == QUOTE_DELIMITER) {
                val end = lines.indexOfFirst(i + 1) { it.trimEnd().trimStart() == QUOTE_DELIMITER }
                val last = if (end == -1) lines.size else end
                for (q in (i + 1) until last) {
                    out.append("> ").append(inline(lines[q], resolveWikilink)).append('\n')
                }
                out.append('\n')
                i = if (end == -1) lines.size else end + 1
                continue
            }

            out.append(block(line, resolveWikilink)).append('\n')
            i++
        }

        // An unterminated verbatim block still has to close, or the whole tail renders as code.
        if (openFence != null) out.append("```\n")

        return out.toString().trimEnd('\n')
    }

    /** Converts one line's block-level markup, then its inline markup. */
    private fun block(
        line: String,
        resolveWikilink: (String) -> String?,
    ): String {
        val indent = line.takeWhile { it == ' ' || it == '\t' }
        val body = line.substring(indent.length)

        HEADING.matchEntire(body)?.let { m ->
            // `= Title` is level 1, `== Section` level 2, matching Markdown's `#` count.
            return "#".repeat(m.groupValues[1].length) + " " + inline(m.groupValues[2], resolveWikilink)
        }

        UNORDERED_ITEM.matchEntire(body)?.let { m ->
            // AsciiDoc nests with repeated markers (`**`), Markdown with indentation.
            val depth = m.groupValues[1].length - 1
            return "  ".repeat(depth) + "- " + inline(m.groupValues[2], resolveWikilink)
        }

        ORDERED_ITEM.matchEntire(body)?.let { m ->
            val depth = m.groupValues[1].length - 1
            return "  ".repeat(depth) + "1. " + inline(m.groupValues[2], resolveWikilink)
        }

        ADMONITION.matchEntire(body)?.let { m ->
            return "> **${m.groupValues[1]}:** " + inline(m.groupValues[2], resolveWikilink)
        }

        BLOCK_IMAGE.matchEntire(body)?.let { m ->
            val alt = m.groupValues[2].substringBefore(',').trim()
            return "![$alt](${m.groupValues[1]})"
        }

        return indent + inline(body, resolveWikilink)
    }

    /** Converts inline markup. Order matters: link macros before emphasis, so labels survive. */
    private fun inline(
        text: String,
        resolveWikilink: (String) -> String?,
    ): String {
        var s = text

        s = INLINE_IMAGE.replace(s) { m -> "![${m.groupValues[2].substringBefore(',').trim()}](${m.groupValues[1]})" }
        s = LINK_MACRO.replace(s) { m -> "[${m.groupValues[2].ifBlank { m.groupValues[1] }}](${m.groupValues[1]})" }
        s = BARE_URL_WITH_LABEL.replace(s) { m -> "[${m.groupValues[2]}](${m.groupValues[1]})" }

        s =
            WIKILINK.replace(s) { m ->
                // `[[target|label]]` and `[[target]]`.
                val raw = m.groupValues[1]
                val target = raw.substringBefore('|').trim()
                val label = raw.substringAfter('|', target).trim().ifBlank { target }
                val uri = resolveWikilink(target)
                if (uri != null) "[$label]($uri)" else label
            }

        // AsciiDoc bold is `*x*` and italic is `_x_`; Markdown is `**x**` and `*x*`. Both are
        // rewritten in one pass so the bold output is not re-read as two italics.
        s = BOLD.replace(s) { m -> "**${m.groupValues[1]}**" }
        s = ITALIC.replace(s) { m -> "*${m.groupValues[1]}*" }

        return s
    }

    private fun verbatimDelimiterOf(trimmed: String): String? {
        val t = trimmed.trimStart()
        return when {
            t == "----" -> "----"
            t == "...." -> "...."
            t == "++++" -> "++++"
            else -> null
        }
    }

    private fun sourceLanguageOf(trimmed: String): String? {
        val m = SOURCE_ATTRIBUTE.matchEntire(trimmed.trim()) ?: return null
        return m.groupValues[1].trim().ifBlank { null }
    }

    private inline fun List<String>.indexOfFirst(
        from: Int,
        predicate: (String) -> Boolean,
    ): Int {
        for (i in from until size) if (predicate(this[i])) return i
        return -1
    }

    private const val QUOTE_DELIMITER = "____"

    private val HEADING = Regex("""^(={1,6})\s+(\S.*)$""")
    private val UNORDERED_ITEM = Regex("""^(\*{1,5})\s+(\S.*)$""")
    private val ORDERED_ITEM = Regex("""^(\.{1,5})\s+(\S.*)$""")
    private val ADMONITION = Regex("""^(NOTE|TIP|IMPORTANT|WARNING|CAUTION):\s+(\S.*)$""")
    private val BLOCK_ATTRIBUTE = Regex("""^\s*\[[^\]]*]\s*$""")
    private val SOURCE_ATTRIBUTE = Regex("""^\[source\s*,\s*([^\],]*)(?:,[^\]]*)?]$""")
    private val DOC_ATTRIBUTE = Regex("""^:[A-Za-z][A-Za-z0-9_-]*!?:.*$""")
    private val BLOCK_IMAGE = Regex("""^image::(\S+?)\[(.*)]$""")
    private val INLINE_IMAGE = Regex("""image:(\S+?)\[([^\]]*)]""")
    private val LINK_MACRO = Regex("""link:(\S+?)\[([^\]]*)]""")
    private val BARE_URL_WITH_LABEL = Regex("""(?<!\()\b(https?://[^\s\[\]]+)\[([^\]]*)]""")
    private val WIKILINK = Regex("""\[\[([^\[\]]+)]]""")
    private val BOLD = Regex("""(?<![\w*])\*([^\s*][^*]*?)\*(?![\w*])""")
    private val ITALIC = Regex("""(?<![\w_])_([^\s_][^_]*?)_(?![\w_])""")
}
