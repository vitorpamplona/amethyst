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

import com.vitorpamplona.amethyst.commons.model.SpellCheckType
import com.vitorpamplona.amethyst.commons.service.spellcheck.Misspelling
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopSpellCheckTest {
    private val hunspell by lazy { HunspellSpellChecker.loadBundled() }

    @Test
    fun bundledDictionaryKnowsEnglish() {
        assertTrue(hunspell.isCorrect("receive"))
        assertTrue(hunspell.isCorrect("Separate"))
        assertTrue(hunspell.isCorrect("don't"))
        assertFalse(hunspell.isCorrect("recieve"))
        assertFalse(hunspell.isCorrect(SELF_TEST_NONSENSE))
    }

    @Test
    fun bundledDictionarySuggestsTheObviousFix() {
        assertEquals("receive", hunspell.suggest("recieve").first())
        assertEquals("definitely", hunspell.suggest("definately").first())
        assertTrue("separate" in hunspell.suggest("seperate"))
    }

    @Test
    fun bundledDictionaryLeavesOtherScriptsAlone() {
        assertTrue(hunspell.handles("café"))
        assertFalse(hunspell.handles("привет"))
        assertFalse(hunspell.handles("こんにちは"))
    }

    @Test
    fun userDictionaryRoundTrips() {
        val dir = Files.createTempDirectory("spell").toFile()
        val dictionary = FileUserDictionary(File(dir, "words.txt"))
        assertEquals(emptySet(), dictionary.load())

        dictionary.save(setOf("pamplona", "amethyst"))

        assertEquals(setOf("pamplona", "amethyst"), FileUserDictionary(File(dir, "words.txt")).load())
        dir.deleteRecursively()
    }

    @Test
    fun followsTheSetting() =
        runBlocking {
            val dir = Files.createTempDirectory("spell").toFile()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val setting = MutableStateFlow(SpellCheckType.BUILT_IN)
            val spellCheck = DesktopSpellCheck(dir, "test", setting, scope)
            try {
                // Nothing loads until a composer asks.
                assertNull(spellCheck.service.activeEngine.value)
                spellCheck.service.activate()

                withTimeout(10_000) { spellCheck.service.activeEngine.first { it == "Hunspell (en_US)" } }
                assertEquals(listOf(Misspelling(4, 11, "recieve")), spellCheck.service.findMisspellings("I'd recieve zaps on Nostr"))

                setting.value = SpellCheckType.OFF
                withTimeout(10_000) { spellCheck.service.activeEngine.first { it == null } }
                assertEquals(emptyList(), spellCheck.service.findMisspellings("I'd recieve"))

                spellCheck.service.addToDictionary("recieve")
                setting.value = SpellCheckType.BUILT_IN
                withTimeout(10_000) { spellCheck.service.activeEngine.first { it != null } }
                assertEquals(emptyList(), spellCheck.service.findMisspellings("I'd recieve"))
                assertEquals("recieve\n", File(dir, DesktopSpellCheck.USER_DICTIONARY_FILE).readText())
            } finally {
                scope.cancel()
                dir.deleteRecursively()
            }
        }
}
