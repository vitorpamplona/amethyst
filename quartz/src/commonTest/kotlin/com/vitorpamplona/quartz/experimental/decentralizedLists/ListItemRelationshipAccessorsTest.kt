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
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.ListItemEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class ListItemRelationshipAccessorsTest {
    private val pk1 = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val pk2 = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val eid = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val eid2 = "98b574c3527f0ffb30b7271084e3f07480733c7289f8de424d29eae82e36c758"
    private val relay = "wss://relay.damus.io/"
    private val zero = "00".repeat(32)
    private val sig = "00".repeat(64)
    private val header = "39998:$pk2:animals"
    private val article = "30023:$pk2:article"
    private val set = "39999:$pk2:mammals"

    private fun itemTags() =
        arrayOf(
            arrayOf("z", eid2),
            arrayOf("z", header),
            arrayOf("z", "just a list name"),
            arrayOf("p", pk2, relay),
            arrayOf("p", "nothex"),
            arrayOf("e", eid),
            arrayOf("a", article),
            arrayOf("a", "not:a:coordinate"),
        )

    @Test
    fun listItemNamesEachRelationship() {
        val item = ListItemEvent(zero, pk1, 1, itemTags(), "", sig)
        assertEquals(listOf(pk2), item.itemKeys())
        assertEquals(listOf(eid), item.itemEventIds())
        assertEquals(listOf(article), item.itemAddressIds())
        assertEquals(listOf(eid2), item.parentListEventIds())
        assertEquals(listOf(header), item.parentListAddressIds())

        assertEquals(item.itemKeys(), item.linkedPubKeys())
        assertEquals((item.parentListEventIds() + item.itemEventIds()).toSet(), item.linkedEventIds().toSet())
        assertEquals((item.parentListAddressIds() + item.itemAddressIds()).toSet(), item.linkedAddressIds().toSet())
    }

    @Test
    fun addressableItemAddsItsGraphParents() {
        val item = AddressableListItemEvent(zero, pk1, 1, arrayOf(arrayOf("d", "rex")) + itemTags() + arrayOf(arrayOf("n", set), arrayOf("b", "b-tag-deferred")), "", sig)
        assertEquals(listOf(pk2), item.itemKeys())
        assertEquals(listOf(eid), item.itemEventIds())
        assertEquals(listOf(article), item.itemAddressIds())
        assertEquals(listOf(eid2), item.parentListEventIds())
        assertEquals(listOf(header), item.parentListAddressIds())
        assertEquals(listOf(set), item.graphAddressIds())
        assertEquals(
            (item.itemAddressIds() + item.parentListAddressIds() + item.graphAddressIds()).toSet(),
            item.linkedAddressIds().toSet(),
        )
    }

    @Test
    fun headerGraphAddressIdsAreItsInheritParentsAndConceptGraph() {
        val graph = "39999:$pk1:dogs-concept-graph"
        val event =
            AddressableListHeaderEvent(
                zero,
                pk1,
                1,
                arrayOf(arrayOf("d", "dogs"), arrayOf("b", header, "inherit"), arrayOf("b", "b-tag-deferred"), arrayOf("concept-graph", graph)),
                "",
                sig,
            )
        assertEquals(listOf(header, graph), event.graphAddressIds())
        assertEquals(event.graphAddressIds(), event.linkedAddressIds())
    }
}
