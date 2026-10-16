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
package com.vitorpamplona.amethyst.desktop.spellcheck

import com.vitorpamplona.amethyst.commons.service.spellcheck.SpellChecker
import org.apache.lucene.analysis.hunspell.Dictionary
import org.apache.lucene.analysis.hunspell.Hunspell
import org.apache.lucene.analysis.hunspell.SortingStrategy
import org.apache.lucene.analysis.hunspell.TimeoutPolicy
import java.io.FileNotFoundException

/**
 * The built-in checker: a Hunspell dictionary bundled with the app, run by Lucene's pure-Java
 * port of Hunspell. Works the same on every OS and needs nothing installed, but knows only the
 * languages it ships (American English for now).
 *
 * Measured with the bundled en_US: ~0.4 s to load (~30 MB of garbage on the way, ~2 MB kept), ~2 µs per word to check,
 * 20 to 190 ms per word to suggest (capped at 250 ms by Lucene, returning what it found by then).
 */
class HunspellSpellChecker private constructor(
    private val hunspell: Hunspell,
    locale: String,
) : SpellChecker {
    override val name = "Hunspell ($locale)"

    // An English dictionary would flag every word in another script.
    override fun handles(word: String) = isLatinScript(word)

    override fun isCorrect(word: String) = hunspell.spell(word)

    override fun suggest(word: String): List<String> = hunspell.suggest(word)

    companion object {
        const val BUNDLED_LOCALE = "en_US"

        /** Loads a dictionary from the app's resources (`/spellcheck/<locale>.aff` and `.dic`). */
        fun loadBundled(locale: String = BUNDLED_LOCALE): HunspellSpellChecker {
            val loader = HunspellSpellChecker::class.java
            val affix = loader.getResourceAsStream("/spellcheck/$locale.aff") ?: throw FileNotFoundException("$locale.aff")
            val words = loader.getResourceAsStream("/spellcheck/$locale.dic") ?: throw FileNotFoundException("$locale.dic")
            val dictionary =
                affix.use { aff ->
                    words.use { dic ->
                        Dictionary(aff, listOf(dic), false, SortingStrategy.inMemory())
                    }
                }
            return HunspellSpellChecker(Hunspell(dictionary, TimeoutPolicy.RETURN_PARTIAL_RESULT) {}, locale)
        }
    }
}

/** Whether every letter of [word] is from the Latin script (Basic Latin through Latin Extended-B, plus Extended Additional). */
internal fun isLatinScript(word: String): Boolean =
    word.all { c ->
        !c.isLetter() || c.code < 0x0250 || c.code in 0x1E00..0x1EFF
    }
