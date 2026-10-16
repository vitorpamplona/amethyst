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
package com.vitorpamplona.amethyst.commons.service.spellcheck

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/** A word the engine rejected, at [start] (inclusive) to [end] (exclusive) of the checked text. */
@Immutable
data class Misspelling(
    val start: Int,
    val end: Int,
    val word: String,
)

/** Words the user taught the checker. Kept by the platform, read once and rewritten on change. */
interface UserDictionary {
    fun load(): Set<String>

    fun save(words: Set<String>)

    object None : UserDictionary {
        override fun load(): Set<String> = emptySet()

        override fun save(words: Set<String>) {}
    }
}

/**
 * Spell checking for the text fields, on top of whichever [SpellChecker] the platform picked.
 *
 * It adds what every engine needs and none provides the same way: the Nostr-aware
 * [SpellCheckTokenizer], the [NostrVocabulary], the user's own dictionary (kept by Amethyst rather
 * than the OS, so it survives switching engines and teaches nothing to other apps), and a cache so
 * a keystroke only sends the new word to the engine.
 *
 * Every engine call runs on [dispatcher], which must be single-threaded: engines are not
 * thread-safe, and the native ones are slow to cross into.
 */
class SpellCheckService(
    private val dispatcher: CoroutineDispatcher,
    private val userDictionary: UserDictionary = UserDictionary.None,
) {
    private var engine: SpellChecker? = null

    private val _activeEngine = MutableStateFlow<String?>(null)

    /** The engine in use, by [SpellChecker.name], or null when spell checking is off. */
    val activeEngine: StateFlow<String?> = _activeEngine.asStateFlow()

    private val _revision = MutableStateFlow(0)

    /** Bumps whenever earlier answers may be stale: a new engine, or a word added to the dictionary. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private var learned: MutableSet<String>? = null

    private val verdicts = LruCache<String, Boolean>(VERDICT_CACHE_SIZE)
    private val suggestionCache = LruCache<String, List<String>>(SUGGESTION_CACHE_SIZE)

    suspend fun setEngine(checker: SpellChecker?) =
        withContext(dispatcher) {
            engine = checker
            verdicts.clear()
            suggestionCache.clear()
            _activeEngine.value = checker?.name
            _revision.value++
        }

    /** The misspelled words in [text], in order. Empty when no engine is set. */
    suspend fun findMisspellings(text: String): List<Misspelling> =
        withContext(dispatcher) {
            val checker = engine ?: return@withContext emptyList()
            SpellCheckTokenizer.words(text).mapNotNull { range ->
                val word = text.substring(range.start, range.end)
                if (isCorrect(checker, word)) null else Misspelling(range.start, range.end, word)
            }
        }

    suspend fun suggestions(word: String): List<String> =
        withContext(dispatcher) {
            val checker = engine ?: return@withContext emptyList()
            suggestionCache.get(word)?.let { return@withContext it }
            val result =
                try {
                    checker.suggest(normalize(word)).filter { it != word }.take(MAX_SUGGESTIONS)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Suggestions failed for a word", e)
                    emptyList()
                }
            suggestionCache.put(word, result)
            result
        }

    suspend fun addToDictionary(word: String) =
        withContext(dispatcher) {
            val words = learnedWords()
            if (words.add(word.lowercase())) {
                try {
                    userDictionary.save(words.toSet())
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Could not save the user dictionary", e)
                }
                verdicts.clear()
                _revision.value++
            }
        }

    private fun isCorrect(
        checker: SpellChecker,
        word: String,
    ): Boolean {
        verdicts.get(word)?.let { return it }
        val normalized = normalize(word)
        val verdict =
            when {
                NostrVocabulary.contains(normalized) -> true
                normalized.lowercase() in learnedWords() -> true
                !checker.handles(normalized) -> true
                else ->
                    try {
                        checker.isCorrect(normalized)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        // A word the engine choked on is not the user's mistake.
                        Log.w(TAG, "Spell check failed for a word", e)
                        true
                    }
            }
        verdicts.put(word, verdict)
        return verdict
    }

    private fun learnedWords(): MutableSet<String> =
        learned ?: try {
            userDictionary.load().mapTo(HashSet()) { it.lowercase() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Could not read the user dictionary", e)
            HashSet()
        }.also { learned = it }

    // Dictionaries spell contractions with the ASCII apostrophe; keyboards often type U+2019.
    private fun normalize(word: String) = word.replace('’', '\'')

    companion object {
        private const val TAG = "SpellCheckService"
        private const val VERDICT_CACHE_SIZE = 8192
        private const val SUGGESTION_CACHE_SIZE = 256
        const val MAX_SUGGESTIONS = 5
    }
}

/** A small least-recently-used map. Only touched from the service's single thread. */
private class LruCache<K, V>(
    private val capacity: Int,
) {
    private val map = LinkedHashMap<K, V>()

    fun get(key: K): V? {
        val value = map.remove(key) ?: return null
        map[key] = value
        return value
    }

    fun put(
        key: K,
        value: V,
    ) {
        map.remove(key)
        map[key] = value
        if (map.size > capacity) {
            map.remove(map.keys.first())
        }
    }

    fun clear() = map.clear()
}
