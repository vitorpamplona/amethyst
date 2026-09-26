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
package com.vitorpamplona.amethyst.commons.nip64Chess

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ChessDismissedGamesStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var seq = 0

    private fun raw(): DataStore<Preferences> {
        val file = File(folder.root, "chess_${seq++}.preferences_pb")
        return PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { file.toOkioPath() },
        )
    }

    @Test
    fun anUnknownUserHasDismissedNothing() =
        runTest {
            assertTrue(ChessDismissedGamesStore(raw()).load("npub1").isEmpty())
        }

    @Test
    fun savedIdsReadBack() =
        runTest {
            val store = ChessDismissedGamesStore(raw())

            store.save("npub1", setOf("game1", "game2"))

            assertEquals(setOf("game1", "game2"), store.load("npub1"))
        }

    /** Two accounts on one device must not see each other's dismissals. */
    @Test
    fun usersAreIsolated() =
        runTest {
            val store = ChessDismissedGamesStore(raw())

            store.save("npub1", setOf("game1"))
            store.save("npub2", setOf("game2"))

            assertEquals(setOf("game1"), store.load("npub1"))
            assertEquals(setOf("game2"), store.load("npub2"))
        }

    /** An empty set removes the key rather than storing an empty one. */
    @Test
    fun savingAnEmptySetClearsTheEntry() =
        runTest {
            val raw = raw()
            val store = ChessDismissedGamesStore(raw)
            store.save("npub1", setOf("game1"))

            store.save("npub1", emptySet())

            assertTrue(store.load("npub1").isEmpty())
            assertFalse(raw.data.first().contains(ChessDismissedGamesStore.keyFor("npub1")))
        }

    @Test
    fun aLaterSaveReplacesTheSet() =
        runTest {
            val store = ChessDismissedGamesStore(raw())

            store.save("npub1", setOf("a", "b"))
            store.save("npub1", setOf("c"))

            assertEquals(setOf("c"), store.load("npub1"))
        }

    /**
     * The desktop factory has to hand back one store, not a new one per call.
     *
     * DataStore registers a live store per file path and only releases it when
     * the owning scope ends — and the factory's own scope never does. Building
     * a fresh one per call meant the second `IllegalStateException: multiple
     * DataStores active for the same file`, thrown from the chess view model's
     * constructor the second time that screen opened.
     */
    @Test
    fun theDesktopFactoryReturnsOneStore() {
        assertSame(desktopChessDismissedGamesStore(), desktopChessDismissedGamesStore())
    }
}
