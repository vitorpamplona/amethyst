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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent

/** A group and its (recursively assembled) subgroups. */
@Immutable
data class GroupTreeNode(
    val metadata: GroupMetadataEvent,
    val children: List<GroupTreeNode>,
)

/**
 * Assembles the NIP-29 subgroup hierarchy from a flat set of `kind:39000`
 * metadata events (all from the same relay — the tree is relay-scoped).
 *
 * Structure follows each group's own `parent` tag, which is the single source of
 * truth for who a group's parent is (a group carries at most one `parent`, so a
 * group can never end up under two parents). A parent's advertised `child` tag
 * order is used only to order siblings; children a parent hasn't listed yet are
 * appended after the listed ones in a stable order.
 *
 * Robustness rules, matching the spec's relay behaviour:
 * - A group whose declared parent is not present in the set is treated as a root
 *   (the spec has relays reject such edits, but a client aggregating a partial
 *   view must still show the group rather than drop it).
 * - Cycles cannot occur on a compliant relay (it rejects any `kind:9002` that
 *   would create one), but if malformed data produces one it is broken and the
 *   involved groups surface as roots rather than looping forever.
 * - When multiple `kind:39000` events share a `d` id the newest wins.
 */
object SubgroupTree {
    fun build(events: Collection<GroupMetadataEvent>): List<GroupTreeNode> {
        val byId = LinkedHashMap<String, GroupMetadataEvent>()
        events.forEach { event ->
            val id = event.groupId().ifEmpty { return@forEach }
            val existing = byId[id]
            if (existing == null || event.createdAt >= existing.createdAt) byId[id] = event
        }

        val childrenOf = HashMap<String, MutableList<GroupMetadataEvent>>()
        byId.values.forEach { event ->
            val parent = event.parent()
            if (parent != null && parent in byId) {
                childrenOf.getOrPut(parent) { mutableListOf() }.add(event)
            }
        }

        val placed = HashSet<String>()

        fun node(event: GroupMetadataEvent): GroupTreeNode {
            val id = event.groupId()
            placed.add(id)

            // Order siblings by the parent's advertised `child` tag order; anything
            // the parent hasn't listed keeps its natural order after the listed ones.
            val order = event.children().withIndex().associate { (index, childId) -> childId to index }
            val orderedChildren =
                childrenOf[id]
                    .orEmpty()
                    .filter { it.groupId() !in placed } // cycle guard
                    .sortedBy { order[it.groupId()] ?: Int.MAX_VALUE }

            return GroupTreeNode(event, orderedChildren.map { node(it) })
        }

        val roots =
            byId.values.filter {
                val parent = it.parent()
                parent == null || parent !in byId
            }

        val tree = roots.map { node(it) }.toMutableList()

        // Safety net: any group left unplaced was part of a cycle — surface it as a root.
        byId.values.forEach { event ->
            if (event.groupId() !in placed) tree.add(node(event))
        }

        return tree
    }
}
