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
package com.vitorpamplona.amethyst.commons.relayClient.eoseManagers

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every EOSE manager names what holds the notes it loads, so its cursors live and die with them.
 *
 * A cursor says "everything up to T is in memory", which is only true while something holds those
 * notes: `LocalCache` keeps notes and users weakly. The managers keep no cursors of their own, so a
 * manager whose keys name no holder asks every relay from scratch on every rebuild. That is safe but
 * wasteful, and nothing else notices: it compiles, and the screen looks right. The NIPs screens came
 * in from main that way, written against the managers' old cursor maps.
 *
 * This reads the sources and checks each manager: it overrides `cursorHoldersOf`, its key names
 * holders (`CursorHoldingQuery`, `KeyHeldCursors`), it keeps per-object cursors itself (a note's
 * `childCursors`, a user's `relayCursors`), or it inherits one of those. The few that need no cursor
 * at all are listed in [NEEDS_NO_HOLDER], each with why. See the relay-client skill's
 * `references/filter-assemblers.md`, "Where the cursor lives".
 */
class CursorHolderCoverageTest {
    companion object {
        private const val MARKER = "commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/relayClient/eoseManagers/BaseEoseManager.kt"

        private val SOURCE_DIRS =
            listOf(
                "commons/src/commonMain",
                "commons/src/jvmAndroid",
                "commons/src/jvmMain",
                "commonsUI/src/commonMain",
                "amethyst/src/main",
                "desktopApp/src/jvmMain",
                "cli/src/main",
            )

        private val BASES = setOf("BaseEoseManager", "PerUserEoseManager", "PerUniqueIdEoseManager", "PerUserAndFollowListEoseManager", "SingleSubEoseManager")

        private const val PAGER = "a history pager: its requests are bounded by `until`, which no cursor governs"
        private const val FIXED_FLOOR = "a live tail from a fixed floor (recentBoundary()), never from a cursor"
        private const val NO_SINCE = "reads no `since`: it asks the same thing every time"

        /** Managers whose keys name no holder, and why none is needed. */
        private val NEEDS_NO_HOLDER: Map<String, String> =
            mapOf(
                "AccountGiftWrapsHistoryEoseManager" to PAGER,
                "AccountNotificationsHistoryEoseManager" to PAGER,
                "CashuWalletHistoryEoseManager" to PAGER,
                "ChatroomListNip04HistorySubAssembler" to PAGER,
                "ChatroomNip04HistorySubAssembler" to PAGER,
                "ConcordChannelHistorySubAssembler" to PAGER,
                "RelayGroupOpenChatHistorySubAssembler" to PAGER,
                "RelayGroupOpenThreadsHistorySubAssembler" to PAGER,
                "AccountGiftWrapsEoseManager" to FIXED_FLOOR,
                "ChatroomListNip04SubAssembler" to FIXED_FLOOR,
                "ChatroomNip04SubAssembler" to FIXED_FLOOR,
                "MarmotGroupEventsEoseManager" to NO_SINCE,
                "ProfileAppRecommendationsSubAssembler" to NO_SINCE,
                "ProfileBadgesSubAssembler" to NO_SINCE,
                "ThreadEventLoaderSubAssembler" to NO_SINCE,
                "UserOutboxFinderSubAssembler" to "tracks which relays it tried for a user, not how far they answered",
                "UserCardsSubAssembler" to "keeps each user's cursor in that User's cards cache (User.cards())",
                "UserReportsSubAssembler" to "keeps each user's cursor in that User's reports cache (User.reports())",
            )

        /** Where a class declaration starts; [supertypesAfter] reads the rest. */
        private val CLASS_START = Regex("""(?m)^[ \t]*(?:(?:public|internal|private|abstract|open|sealed|data)\s+)*class\s+(\w+)""")
    }

    private class Declared(
        val name: String,
        val supertypes: String,
        val body: String,
    ) {
        /** The first supertype's simple name. */
        val parent: String? get() =
            Regex("""^\s*([\w.]+)""")
                .find(supertypes)
                ?.groupValues
                ?.get(1)
                ?.substringAfterLast('.')

        /** The first type argument of the first supertype: a manager's key type. */
        val keyType: String? get() =
            Regex("""<\s*([\w.]+)""")
                .find(supertypes)
                ?.groupValues
                ?.get(1)
                ?.substringAfterLast('.')
    }

    private val repoRoot: File by lazy {
        generateSequence(File(".").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, MARKER).isFile }
            ?: fail("cannot locate the repository root from ${File(".").absolutePath}")
    }

    /**
     * The supertypes of the class whose name ends at [from]: past its type parameters and its
     * constructor (balanced brackets, skipped by hand: a regex for nested parentheses overflows the
     * stack on large files), the text after `:` up to its body. Null when it extends nothing.
     */
    private fun supertypesAfter(
        text: String,
        from: Int,
    ): String? {
        var i = from

        fun skipBlank() {
            while (i < text.length && text[i].isWhitespace()) i++
        }

        fun skipBalanced(
            open: Char,
            close: Char,
        ) {
            if (i >= text.length || text[i] != open) return
            var depth = 0
            while (i < text.length) {
                if (text[i] == open) depth++
                if (text[i] == close && --depth == 0) {
                    i++
                    return
                }
                i++
            }
        }
        skipBlank()
        skipBalanced('<', '>')
        skipBlank()
        // A primary constructor, possibly with annotations or a visibility before it.
        while (i < text.length && (text[i].isLetter() || text[i] == '@')) {
            val word = Regex("""\G@?[\w.]+""").find(text, i) ?: break
            if (word.value !in setOf("private", "internal", "protected", "public", "constructor") && !word.value.startsWith("@")) break
            i = word.range.last + 1
            skipBlank()
        }
        skipBlank()
        skipBalanced('(', ')')
        skipBlank()
        if (i >= text.length || text[i] != ':') return null
        val end = sequenceOf(text.indexOf('{', i), text.indexOf("\n\n", i)).filter { it >= 0 }.minOrNull() ?: text.length
        return text.substring(i + 1, end).trim()
    }

    private val classes: Map<String, Declared> by lazy {
        val found = mutableMapOf<String, Declared>()
        SOURCE_DIRS.map { File(repoRoot, it) }.filter { it.isDirectory }.forEach { dir ->
            dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
                val text = file.readText()
                val starts = CLASS_START.findAll(text).toList()
                starts.forEachIndexed { index, match ->
                    val supertypes = supertypesAfter(text, match.range.last + 1) ?: return@forEachIndexed
                    // To the next top-level class: a nested one (a manager's private helper) is part of the body.
                    val end =
                        starts
                            .drop(index + 1)
                            .firstOrNull { text[it.range.first] != ' ' && text[it.range.first] != '\t' }
                            ?.range
                            ?.first ?: text.length
                    found[match.groupValues[1]] = Declared(match.groupValues[1], supertypes, text.substring(match.range.first, end))
                }
            }
        }
        found
    }

    /** Every class that extends one of the managers, through any number of steps, but not the bases. */
    private val managers: List<Declared> by lazy {
        val names = mutableSetOf<String>()
        do {
            val added = classes.values.filter { it.name !in names && it.name !in BASES && (it.parent in BASES || it.parent in names) }
            added.forEach { names.add(it.name) }
        } while (added.isNotEmpty())
        // The no-cursor managers (SingleSubNoEoseCacheEoseManager and its subclasses) by design keep none.
        names.mapNotNull { classes[it] }.filterNot { it.name.contains("NoEoseCache") || it.supertypes.contains("NoEoseCache") }
    }

    private fun keyNamesHolders(type: String?): Boolean {
        var current = type?.let { classes[it] }
        val seen = mutableSetOf<String>()
        while (current != null && seen.add(current.name)) {
            if (current.supertypes.contains("CursorHoldingQuery") || current.supertypes.contains("KeyHeldCursors")) return true
            current = current.parent?.let { classes[it] }
        }
        return false
    }

    /** Why [manager] keeps its cursors with what holds its notes, or null when nothing says it does. */
    private fun holdersOf(manager: Declared): String? {
        var current: Declared? = manager
        val seen = mutableSetOf<String>()
        while (current != null && current.name !in BASES && seen.add(current.name)) {
            if (current.body.contains("override fun cursorHoldersOf")) return "overrides cursorHoldersOf"
            if (current.body.contains("childCursors") || current.body.contains(".relayCursors")) return "keeps per-object cursors"
            if (keyNamesHolders(current.keyType)) return "its key names holders"
            current = current.parent?.let { classes[it] }
        }
        return null
    }

    @Test
    fun theScanFindsTheManagers() {
        // Guards the regexes: a scan that finds nothing would pass every other check here.
        assertTrue(managers.size > 50, "found only ${managers.size} EOSE managers: the source scan is broken")
        listOf("TopNavFeedSubAssembler", "EventWatcherSubAssembler", "NipsSubAssembler", "UserWatcherSubAssembler").forEach { name ->
            assertTrue(managers.any { it.name == name }, "$name was not found as an EOSE manager: the source scan is broken")
        }
    }

    @Test
    fun everyManagerNamesWhatHoldsItsNotes() {
        val missing = managers.filter { it.name !in NEEDS_NO_HOLDER && holdersOf(it) == null }.map { it.name }.sorted()
        assertTrue(
            missing.isEmpty(),
            "These EOSE managers name nothing that holds the notes they load, so they ask every relay from scratch " +
                "on every rebuild. Override cursorHoldersOf (or have the key implement CursorHoldingQuery / KeyHeldCursors) " +
                "with the feed, channel, user, note, account or key that holds them; or, if the manager never reads a " +
                "cursor, add it to NEEDS_NO_HOLDER saying why:\n  " + missing.joinToString("\n  "),
        )
    }

    @Test
    fun theExceptionsAreStillExceptions() {
        NEEDS_NO_HOLDER.keys.forEach { name ->
            val manager = managers.firstOrNull { it.name == name } ?: fail("$name is no longer an EOSE manager: drop it from NEEDS_NO_HOLDER")
            val holders = holdersOf(manager)
            assertTrue(holders == null, "$name now names its holders ($holders): drop it from NEEDS_NO_HOLDER")
        }
    }
}
