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
package com.vitorpamplona.amethyst.shared.resources

import com.ibm.icu.text.PluralRules
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * The JVM counterpart to Android's `Resources`.
 *
 * Reads the locale tables emitted by `:amethystShared:generateAndroidResourceTable`
 * from the very same `res/values-*` string tree aapt2 consumes, so a
 * `stringRes(R.string.foo)` call site resolves to the same text on both
 * platforms without the call site knowing which platform it is on.
 *
 * Plural category selection goes through ICU's CLDR rules. Android selects
 * plurals with its own bundled `android.icu`, so the two agree by construction
 * rather than by a hand-maintained table of language rules.
 */
object AndroidResourceTable {
    private const val TABLE_DIR = "amethyst-res"
    private const val DEFAULT_QUALIFIER = "default"

    /** Parsed `<qualifier>.tsv` files, keyed by qualifier. Loaded on demand. */
    private val tables = ConcurrentHashMap<String, Table>()

    @Volatile
    private var currentLocale: Locale = Locale.getDefault()

    /** Qualifier lookup order for [currentLocale], most specific first. */
    @Volatile
    private var lookupChain: List<String> = chainFor(Locale.getDefault())

    @Volatile
    private var pluralRules: PluralRules = PluralRules.forLocale(Locale.getDefault())

    /** Bumped on every locale change so caches keyed on it can invalidate. */
    @Volatile
    var generation: Int = 0
        private set

    val locale: Locale get() = currentLocale

    @Synchronized
    fun setLocale(locale: Locale) {
        if (locale == currentLocale) return
        currentLocale = locale
        lookupChain = chainFor(locale)
        pluralRules = PluralRules.forLocale(locale)
        generation++
    }

    /** The qualifiers this build actually ships, for diagnostics and pickers. */
    fun availableQualifiers(): List<String> =
        readResource("$TABLE_DIR/qualifiers.txt")
            ?.lineSequence()
            ?.filter { it.isNotBlank() }
            ?.toList()
            .orEmpty()

    fun getString(id: Int): String = lookupString(id) ?: missing("string", id)

    fun getString(
        id: Int,
        vararg formatArgs: Any?,
    ): String = format(getString(id), formatArgs)

    fun getQuantityString(
        id: Int,
        quantity: Int,
    ): String = lookupPlural(id, quantity) ?: missing("plural", id)

    fun getQuantityString(
        id: Int,
        quantity: Int,
        vararg formatArgs: Any?,
    ): String = format(getQuantityString(id, quantity), formatArgs)

    /**
     * Formatting failures must not crash a screen: a translation with the wrong
     * placeholder count is a data problem, not a code problem, and Android
     * degrades the same way rather than taking the process down.
     */
    private fun format(
        template: String,
        args: Array<out Any?>,
    ): String =
        try {
            String.format(currentLocale, template, *args)
        } catch (e: java.util.IllegalFormatException) {
            template
        }

    private fun lookupString(id: Int): String? {
        lookupChain.forEach { qualifier ->
            table(qualifier).strings[id]?.let { return it }
        }
        return null
    }

    private fun lookupPlural(
        id: Int,
        quantity: Int,
    ): String? {
        val category = pluralRules.select(quantity.toDouble())
        lookupChain.forEach { qualifier ->
            val byCategory = table(qualifier).plurals[id] ?: return@forEach
            // A translation may omit a category the language's rules allow;
            // Android falls back to "other" in that case, so we do too.
            (byCategory[category] ?: byCategory[PluralRules.KEYWORD_OTHER])?.let { return it }
        }
        return null
    }

    private fun missing(
        kind: String,
        id: Int,
    ): String = "<missing $kind 0x${id.toString(16)}>"

    private fun table(qualifier: String): Table = tables.getOrPut(qualifier) { loadTable(qualifier) }

    /**
     * Candidate qualifiers for a locale, most specific first, always ending at
     * the default table. Mirrors how Android narrows `values-pt-rBR` →
     * `values-pt` → `values`.
     */
    private fun chainFor(locale: Locale): List<String> {
        val language = locale.language.lowercase(Locale.ROOT)
        val country = locale.country.uppercase(Locale.ROOT)
        return buildList {
            if (language.isNotEmpty() && country.isNotEmpty()) add("$language-r$country")
            if (language.isNotEmpty()) add(language)
            add(DEFAULT_QUALIFIER)
        }
    }

    private fun loadTable(qualifier: String): Table {
        val text = readResource("$TABLE_DIR/$qualifier.tsv") ?: return Table.EMPTY
        val strings = HashMap<Int, String>()
        val plurals = HashMap<Int, MutableMap<String, String>>()
        text.lineSequence().forEach { line ->
            if (line.isEmpty()) return@forEach
            val parts = line.split('\t')
            when (parts.getOrNull(0)) {
                "s" ->
                    if (parts.size >= 3) {
                        parts[1].toIntOrNull()?.let { strings[it] = decode(parts[2]) }
                    }
                "p" ->
                    if (parts.size >= 4) {
                        parts[1].toIntOrNull()?.let { id ->
                            plurals.getOrPut(id) { HashMap() }[parts[2]] = decode(parts[3])
                        }
                    }
            }
        }
        return Table(strings, plurals)
    }

    private fun readResource(path: String): String? =
        AndroidResourceTable::class.java.classLoader
            ?.getResourceAsStream(path)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }

    /** Inverse of the generator's `encode`. */
    private fun decode(value: String): String {
        if ('\\' !in value) return value
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    'r' -> out.append('\r')
                    else -> out.append(next)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    private class Table(
        val strings: Map<Int, String>,
        val plurals: Map<Int, Map<String, String>>,
    ) {
        companion object {
            val EMPTY = Table(emptyMap(), emptyMap())
        }
    }
}
