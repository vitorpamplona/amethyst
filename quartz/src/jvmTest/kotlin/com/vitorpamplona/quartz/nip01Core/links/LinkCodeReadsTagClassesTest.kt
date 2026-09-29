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

import java.io.File
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A Tag class owns the parsing of its tag. Link code — every `links()` body and every function
 * with a [LinkBuilder] receiver — must take its values from Tag-class parsers, `TAG_NAME`
 * constants and the accessors built on them, never from raw tag slots or string-literal tag
 * names, and its props must be the relation's typed class. This reads the sources, so a
 * regression fails here instead of in review.
 */
class LinkCodeReadsTagClassesTest {
    private val checks =
        listOf(
            Regex("""\b\w+\[\d+]""") to "tag slot indexing: read through the Tag class parser",
            Regex("""\b(?:it|tag|entry|t)\.size\b""") to "tag size check: the Tag class parser decides what is well-formed",
            Regex("""\b(?:eventTags|userTags|addressTags|valueTags|userTagsWithRoles)\(""") to "raw-slot helper",
            Regex("""\b(?:mapOf|buildMap|hashMapOf|HashMap)\b""") to "raw-map props: use the relation's props class",
            Regex(""""[A-Za-z][A-Za-z0-9_-]{0,2}"""") to "short string literal: use the Tag class TAG_NAME",
        )

    // TRANSITIONAL: enabled once every package reads its tags through Tag classes.
    @Ignore
    @Test
    fun linkCodeReadsTagsOnlyThroughTheirTagClasses() {
        val root = File("src/commonMain/kotlin/com/vitorpamplona/quartz")
        assertTrue(root.isDirectory, "run from the quartz module directory: ${root.absolutePath}")

        val findings = mutableListOf<String>()
        root
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.invariantSeparatorsPath.contains("/nip01Core/links/") }
            .forEach { file ->
                val source = file.readText()
                if (!source.contains("links()") && !source.contains("LinkBuilder.")) return@forEach
                linkCodeBlocks(source).forEach { (line, block) ->
                    stripComments(block).lines().forEachIndexed { offset, row ->
                        checks.forEach { (regex, why) ->
                            if (regex.containsMatchIn(row)) {
                                findings += "${file.relativeTo(root).invariantSeparatorsPath}:${line + offset}: $why\n    ${row.trim()}"
                            }
                        }
                    }
                }
            }
        assertTrue(findings.isEmpty(), "${findings.size} raw tag access(es) in link code:\n" + findings.joinToString("\n"))
    }

    /** (first line, text) of each `links()` body and each `LinkBuilder`-receiver function body. */
    private fun linkCodeBlocks(source: String): List<Pair<Int, String>> {
        val starts = mutableListOf<Int>()
        Regex("""override fun links\(\)[^{=]*[{=]""").findAll(source).forEach { starts += it.range.last }
        Regex("""fun\s+(?:<[^>]*>\s*)?LinkBuilder\.\w+\s*\(""").findAll(source).forEach { match ->
            var depth = 0
            var i = match.range.last
            while (i < source.length) {
                if (source[i] == '(') depth++
                if (source[i] == ')' && --depth == 0) break
                i++
            }
            while (i < source.length && source[i] != '{' && source[i] != '=') i++
            if (i < source.length) starts += i
        }
        return starts.map { start -> source.substring(0, start).count { it == '\n' } + 1 to bodyAt(source, start) }
    }

    private fun bodyAt(
        source: String,
        start: Int,
    ): String {
        if (source[start] == '{') {
            var depth = 0
            for (i in start until source.length) {
                if (source[i] == '{') depth++
                if (source[i] == '}' && --depth == 0) return source.substring(start, i + 1)
            }
            return source.substring(start)
        }
        // An expression body runs until the next member declaration at class indentation.
        val rest = source.substring(start + 1)
        val end = Regex("""\n {0,4}(?:override |fun |val |var |private |internal |companion |@|}|/\*\*)""").find(rest)
        return rest.substring(0, end?.range?.first ?: rest.length)
    }

    /** Comments out, newlines kept so reported line numbers stay right. */
    private fun stripComments(text: String) =
        text
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)) { it.value.filter { c -> c == '\n' } }
            .replace(Regex("""//[^\n]*"""), "")
}
