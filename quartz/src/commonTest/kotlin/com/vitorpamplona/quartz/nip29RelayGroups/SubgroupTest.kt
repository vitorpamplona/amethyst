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
package com.vitorpamplona.quartz.nip29RelayGroups

import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.EditMetadataEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * NIP-29 §Subgroups: the `parent`/`child` tags on `kind:39000` group metadata and
 * `kind:9002` edit-metadata, plus [SubgroupTree] assembly of a relay's flat group
 * set into the parent/child hierarchy (ordering, orphan-as-root, cycle safety).
 */
class SubgroupTest {
    private val relaySelf = "aa".repeat(32)
    private val sig = "bb".repeat(64)
    private val id = "00".repeat(32)

    private fun metadata(
        groupId: String,
        parent: String? = null,
        children: List<String> = emptyList(),
        createdAt: Long = 100,
    ): GroupMetadataEvent {
        val template = GroupMetadataEvent.build(groupId, name = groupId, parent = parent, children = children, createdAt = createdAt)
        return EventFactory.create(id, relaySelf, template.createdAt, GroupMetadataEvent.KIND, template.tags, "", sig) as GroupMetadataEvent
    }

    @Test
    fun rootGroupHasNoParent() {
        val root = metadata("tech", children = listOf("nostr"))
        assertNull(root.parent())
        assertTrue(root.isRoot())
        assertEquals(listOf("nostr"), root.children())
    }

    @Test
    fun subgroupCarriesParentAndChildOrder() {
        val sub = metadata("nostr", parent = "tech", children = listOf("nip29", "nips"))
        assertEquals("tech", sub.parent())
        assertEquals(false, sub.isRoot())
        assertEquals(listOf("nip29", "nips"), sub.children())
    }

    @Test
    fun editMetadataRoundTripsParentAndChildren() {
        val template = EditMetadataEvent.build("nostr", name = "Nostr", parent = "social", children = listOf("nip29"))
        val event = EventFactory.create(id, relaySelf, template.createdAt, EditMetadataEvent.KIND, template.tags, "", sig) as EditMetadataEvent

        assertEquals("nostr", event.groupId())
        assertEquals("social", event.parent())
        assertEquals(listOf("nip29"), event.children())
    }

    @Test
    fun editMetadataWithoutParentRoots() {
        val template = EditMetadataEvent.build("nostr", name = "Nostr")
        val event = EventFactory.create(id, relaySelf, template.createdAt, EditMetadataEvent.KIND, template.tags, "", sig) as EditMetadataEvent
        assertNull(event.parent())
    }

    @Test
    fun buildsNestedTree() {
        val tree =
            SubgroupTree.build(
                listOf(
                    metadata("tech", children = listOf("nostr")),
                    metadata("nostr", parent = "tech", children = listOf("nip29")),
                    metadata("nip29", parent = "nostr"),
                ),
            )

        assertEquals(1, tree.size)
        val tech = tree[0]
        assertEquals("tech", tech.metadata.groupId())
        assertEquals(1, tech.children.size)
        val nostr = tech.children[0]
        assertEquals("nostr", nostr.metadata.groupId())
        assertEquals(
            "nip29",
            nostr.children
                .single()
                .metadata
                .groupId(),
        )
    }

    @Test
    fun ordersSiblingsByParentChildTags() {
        // Parent lists children in b, a, c order — the tree must follow that, not insertion order.
        val tree =
            SubgroupTree.build(
                listOf(
                    metadata("root", children = listOf("b", "a", "c")),
                    metadata("a", parent = "root"),
                    metadata("b", parent = "root"),
                    metadata("c", parent = "root"),
                ),
            )

        assertEquals(listOf("b", "a", "c"), tree.single().children.map { it.metadata.groupId() })
    }

    @Test
    fun unlistedChildrenComeAfterListedOnes() {
        val tree =
            SubgroupTree.build(
                listOf(
                    metadata("root", children = listOf("a")),
                    metadata("a", parent = "root"),
                    // "b" points at root but root hasn't listed it yet
                    metadata("b", parent = "root"),
                ),
            )

        assertEquals(listOf("a", "b"), tree.single().children.map { it.metadata.groupId() })
    }

    @Test
    fun groupWithMissingParentBecomesRoot() {
        // "nostr" declares a parent that isn't in the set — surface it as a root, not dropped.
        val tree = SubgroupTree.build(listOf(metadata("nostr", parent = "ghost")))
        assertEquals(listOf("nostr"), tree.map { it.metadata.groupId() })
    }

    @Test
    fun multipleRootsAreReturned() {
        val tree =
            SubgroupTree.build(
                listOf(
                    metadata("tech"),
                    metadata("food"),
                    metadata("pizza", parent = "food"),
                ),
            )

        assertEquals(setOf("tech", "food"), tree.map { it.metadata.groupId() }.toSet())
    }

    @Test
    fun cycleDoesNotLoopForever() {
        // Malformed data: a <-> b point at each other. A compliant relay rejects this,
        // but the assembler must terminate and still surface the groups.
        val tree =
            SubgroupTree.build(
                listOf(
                    metadata("a", parent = "b"),
                    metadata("b", parent = "a"),
                ),
            )

        val allIds = mutableSetOf<String>()

        fun collect(node: GroupTreeNode) {
            allIds.add(node.metadata.groupId())
            node.children.forEach(::collect)
        }
        tree.forEach(::collect)
        assertTrue(allIds.containsAll(setOf("a", "b")))
    }

    @Test
    fun newestMetadataWinsForSameId() {
        val tree =
            SubgroupTree.build(
                listOf(
                    metadata("g", children = listOf("old"), createdAt = 100),
                    metadata("g", children = listOf("new"), createdAt = 200),
                ),
            )

        assertEquals(listOf("new"), tree.single().metadata.children())
    }
}
