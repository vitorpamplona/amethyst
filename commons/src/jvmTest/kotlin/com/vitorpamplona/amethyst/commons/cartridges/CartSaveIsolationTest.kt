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
package com.vitorpamplona.amethyst.commons.cartridges

import com.vitorpamplona.amethyst.commons.model.preferences.AppPreferenceStores
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CartSaveIsolationTest {
    @get:Rule val folder = TemporaryFolder()

    private fun store() = AppPreferenceStores(rootFilesDir = { folder.root.absolutePath.toPath() }).cartSaveStore()

    private fun values(n: Int) = IntArray(CartSaveStore.VALUES) { n }

    // A joke cart that names a popular game's save id must not wipe that game's high scores.
    @Test
    fun aCartCannotReadOrOverwriteAnotherCartsSave() =
        runBlocking {
            val store = store()
            val game = store.forCart { "aa".repeat(32) }
            val joke = store.forCart { "bb".repeat(32) }
            game.save(mapOf("celeste_1" to values(42)))

            assertNull(joke.loadAll()["celeste_1"])
            joke.save(mapOf("celeste_1" to values(0)))

            assertEquals(42, game.loadAll().getValue("celeste_1")[0])
        }

    @Test
    fun theSameFilePostedTwiceSharesItsSaves() =
        runBlocking {
            val store = store()
            store.forCart { "AA".repeat(32) }.save(mapOf("s" to values(7)))

            assertEquals(7, store.forCart { "aa".repeat(32) }.loadAll().getValue("s")[0])
        }

    @Test
    fun nothingIsReadOrWrittenBeforeTheCartIsKnown() =
        runBlocking {
            val store = store()
            val unknown = store.forCart { null }
            unknown.save(mapOf("s" to values(1)))

            assertTrue(unknown.loadAll().isEmpty())
            assertTrue(store.forCart { "aa".repeat(32) }.loadAll().isEmpty())
        }

    @Test
    fun theStoreStopsGrowingAtItsCap() =
        runBlocking {
            val store = store()
            for (cart in 0 until 20) {
                store.save("cart$cart", (0 until 60).associate { "id$it" to values(it) })
            }
            val total = (0 until 20).sumOf { store.loadAll("cart$it").size }

            assertEquals(CartSaveStore.MAX_STORED_SAVES, total)
            // An id already kept is still written past the cap.
            store.save("cart0", mapOf("id0" to values(99)))
            assertEquals(99, store.loadAll("cart0").getValue("id0")[0])
        }

    @Test
    fun onlyTheMostRecentPlayersStayAlive() {
        val released = mutableListOf<String>()
        val live = LivePlayers<String>(2) { released += it }

        live.touch("a")
        live.touch("b")
        live.touch("a")
        live.touch("c")

        assertEquals(listOf("b"), released)
        assertEquals(2, live.size)
    }
}
