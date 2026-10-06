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
package com.vitorpamplona.quartz.experimental.decentralizedLists

import com.vitorpamplona.quartz.experimental.decentralizedLists.header.AddressableListHeaderEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.AddressableListItemEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DecentralizedListGraphHintProviderTest {
    private val pk1 = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val pk2 = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val eid = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val eid2 = "98b574c3527f0ffb30b7271084e3f07480733c7289f8de424d29eae82e36c758"
    private val relay = "wss://relay.damus.io/"
    private val zero = "00".repeat(32)
    private val sig = "00".repeat(64)

    private val parent = "39998:$pk2:animals"
    private val set = "39999:$pk2:mammals"
    private val superset = "39999:$pk2:vertebrates"
    private val graph = "39999:$pk1:dogs-concept-graph"

    @Test
    fun headerLinksItsInheritParentsAndConceptGraph() {
        val event =
            AddressableListHeaderEvent(
                zero,
                pk1,
                1,
                arrayOf(
                    arrayOf("d", "dogs"),
                    arrayOf("names", "dog", "dogs"),
                    arrayOf("b", parent, "inherit"),
                    arrayOf("b", "b-tag-deferred"),
                    arrayOf("concept-graph", graph),
                ),
                "",
                sig,
            )
        assertEquals(listOf(parent, graph), event.linkedAddressIds())
        assertTrue(event.addressHints().isEmpty())
    }

    @Test
    fun itemLinksItsClassThreadParents() {
        val event =
            AddressableListItemEvent(
                zero,
                pk1,
                1,
                arrayOf(
                    arrayOf("d", "rex"),
                    arrayOf("z", parent),
                    arrayOf("a", "30023:$pk2:article", relay),
                    arrayOf("b", parent, "pointer"),
                    arrayOf("n", set),
                    arrayOf("s", superset),
                    arrayOf("n", "not a coordinate"),
                ),
                "",
                sig,
            )
        assertEquals(listOf(parent, "30023:$pk2:article", parent, set, superset), event.linkedAddressIds())
        assertEquals(listOf("30023:$pk2:article"), event.addressHints().map { it.addressId })
    }
}
