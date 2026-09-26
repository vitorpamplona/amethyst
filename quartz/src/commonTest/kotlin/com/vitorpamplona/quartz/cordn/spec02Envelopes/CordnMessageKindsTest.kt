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
package com.vitorpamplona.quartz.cordn.spec02Envelopes

import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnMessageReferences.PinOp
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnMessageReferences.Target
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The cordn kind catalog and its reference format.
 *
 * `spec/02.md` §6 names chat, thread-reply and reaction and then says there is
 * "no required set of kind values", so edits (1010), deletions (5) and pins
 * (1011) are the reference client's conventions rather than protocol. We follow
 * them; these tests are what "follow" means concretely, and are the place to
 * look when cordn-web changes something.
 */
class CordnMessageKindsTest {
    private val alice = "aa".repeat(32)
    private val bob = "bb".repeat(32)

    private fun target(
        id: String = "1".repeat(64),
        pubKey: String = alice,
        kind: Int = CordnMessageKinds.TEXT,
        tags: TagArray = emptyArray(),
    ) = Target(id, pubKey, kind, tags)

    private fun TagArray.value(name: String): String? = firstOrNull { it.isNotEmpty() && it[0] == name }?.getOrNull(1)

    @Test
    fun `annotations never render as their own row`() {
        listOf(CordnMessageKinds.REACTION, CordnMessageKinds.EDIT, CordnMessageKinds.DELETION, CordnMessageKinds.PIN)
            .forEach { assertTrue(CordnMessageKinds.isAnnotation(it), "kind $it modifies a message, it is not one") }

        listOf(CordnMessageKinds.TEXT, CordnMessageKinds.THREAD_REPLY, CordnMessageKinds.SYSTEM)
            .forEach { assertTrue(!CordnMessageKinds.isAnnotation(it), "kind $it is a message in its own right") }

        assertTrue(CordnMessageKinds.isSystem(CordnMessageKinds.SYSTEM))
        assertTrue(CordnMessageKinds.SYSTEM < 0, "a system row is derived, never sent — it must not collide with a wire kind")
    }

    @Test
    fun `the numbers are cordn-web's, and differ from Marmot's on purpose`() {
        // Pinned because they are the interop surface with the only other cordn
        // client, and because two of them disagree with Marmot (1009 edit, no
        // pin) by decision rather than accident.
        assertEquals(9, CordnMessageKinds.TEXT)
        assertEquals(1111, CordnMessageKinds.THREAD_REPLY)
        assertEquals(7, CordnMessageKinds.REACTION)
        assertEquals(1010, CordnMessageKinds.EDIT)
        assertEquals(5, CordnMessageKinds.DELETION)
        assertEquals(1011, CordnMessageKinds.PIN)
    }

    // ---- outbound ------------------------------------------------------

    @Test
    fun `plain text is kind 9 and trimmed`() {
        val out = CordnMessageReferences.outbound("  hello  ")
        assertEquals(CordnMessageKinds.TEXT, out.kind)
        assertEquals("hello", out.content)
        assertTrue(out.tags.isEmpty())
    }

    @Test
    fun `a reply is kind 1111 and carries NIP-22 root and parent tags`() {
        val out = CordnMessageReferences.outbound("sure", replyTo = target())
        assertEquals(CordnMessageKinds.THREAD_REPLY, out.kind)
        // First reply in a thread: the target is its own root.
        assertEquals("1".repeat(64), out.tags.value("E"))
        assertEquals("1".repeat(64), out.tags.value("e"))
        assertEquals(alice, out.tags.value("P"))
        assertEquals("9", out.tags.value("K"))
        assertEquals("9", out.tags.value("k"))
    }

    @Test
    fun `replying to a reply keeps the original root`() {
        // The case that makes threads flat instead of nested if it is wrong:
        // the root must come from the parent's own E/K/P, not from the parent.
        val rootId = "9".repeat(64)
        val parent =
            target(
                id = "2".repeat(64),
                pubKey = bob,
                kind = CordnMessageKinds.THREAD_REPLY,
                tags =
                    arrayOf(
                        arrayOf("E", rootId, "", alice),
                        arrayOf("K", "9"),
                        arrayOf("P", alice),
                    ),
            )

        val out = CordnMessageReferences.outbound("agreed", replyTo = parent)

        assertEquals(rootId, out.tags.value("E"), "the thread root must survive a nested reply")
        assertEquals(alice, out.tags.value("P"))
        assertEquals("9", out.tags.value("K"))
        assertEquals("2".repeat(64), out.tags.value("e"), "the parent is the message replied to")
        assertEquals(bob, out.tags.value("p"))
        assertEquals("1111", out.tags.value("k"))
    }

    @Test
    fun `a reaction keeps its content untrimmed`() {
        // The emoji is the payload. Trimming it would change what was sent.
        val out = CordnMessageReferences.outbound(" 🤙 ", reactionTo = target())
        assertEquals(CordnMessageKinds.REACTION, out.kind)
        assertEquals(" 🤙 ", out.content)
        assertEquals("9", out.tags.value("k"))
    }

    @Test
    fun `an edit is trimmed and keeps extra tags`() {
        val out =
            CordnMessageReferences.outbound(
                "  fixed  ",
                extraTags = arrayOf(arrayOf("imeta", "url https://example.invalid/x")),
                editTo = target(),
            )
        assertEquals(CordnMessageKinds.EDIT, out.kind)
        assertEquals("fixed", out.content)
        assertEquals("url https://example.invalid/x", out.tags.value("imeta"))
    }

    @Test
    fun `a deletion carries no content and does not name a recipient`() {
        val out = CordnMessageReferences.outbound("ignored", deleteTo = target())
        assertEquals(CordnMessageKinds.DELETION, out.kind)
        assertEquals("", out.content)
        assertNull(out.tags.value("p"), "a deletion names what is removed, not who to notify")
        assertEquals("9", out.tags.value("k"))
    }

    @Test
    fun `a pin carries an op tag and defaults to add`() {
        val add = CordnMessageReferences.outbound("", pinTo = target())
        assertEquals(CordnMessageKinds.PIN, add.kind)
        assertEquals("add", add.tags.value("op"))

        val remove = CordnMessageReferences.outbound("", pinTo = target(), pinOp = PinOp.REMOVE)
        assertEquals("remove", remove.tags.value("op"))
    }

    // ---- inbound -------------------------------------------------------

    @Test
    fun `a reaction needs kind 7, e p k, and content`() {
        val good = CordnMessageReferences.reactionTags(target())
        assertNotNull(CordnMessageReferences.reaction(7, "+", good))

        assertNull(CordnMessageReferences.reaction(9, "+", good), "wrong kind")
        assertNull(CordnMessageReferences.reaction(7, "   ", good), "a blank reaction is an invisible chip")
        assertNull(CordnMessageReferences.reaction(7, "+", arrayOf(arrayOf("e", "1".repeat(64)))), "no p or k")
        assertNull(
            CordnMessageReferences.reaction(7, "+", arrayOf(arrayOf("e", "x"), arrayOf("p", alice), arrayOf("k", "nine"))),
            "a non-numeric k is not a kind",
        )
    }

    @Test
    fun `a thread needs both the uppercase root and the lowercase parent`() {
        val full = CordnMessageReferences.replyTags(target())
        assertNotNull(CordnMessageReferences.thread(full))

        // Half a thread tag is treated as no thread: guessing reparents a reply
        // under the wrong message, which is worse than showing it unthreaded.
        assertNull(CordnMessageReferences.thread(full.filterNot { it[0] == "E" }.toTypedArray()))
        assertNull(CordnMessageReferences.thread(full.filterNot { it[0] == "k" }.toTypedArray()))
        assertNull(CordnMessageReferences.thread(emptyArray()))
    }

    @Test
    fun `a thread pubkey falls back to the e-tag's fourth element`() {
        val tags =
            arrayOf(
                arrayOf("E", "9".repeat(64), "", alice),
                arrayOf("K", "9"),
                arrayOf("e", "2".repeat(64), "", bob),
                arrayOf("k", "9"),
            )
        val thread = assertNotNull(CordnMessageReferences.thread(tags))
        assertEquals(alice, thread.rootPubKey)
        assertEquals(bob, thread.parentPubKey)
    }

    @Test
    fun `an edit needs kind 1010, an e tag, and text`() {
        val tags = CordnMessageReferences.editTags(target())
        assertEquals("1".repeat(64), assertNotNull(CordnMessageReferences.edit(1010, "new", tags)).targetId)

        assertNull(CordnMessageReferences.edit(1009, "new", tags), "1009 is Marmot's edit kind, not cordn's")
        assertNull(CordnMessageReferences.edit(1010, "   ", tags), "an edit to nothing is a deletion")
        assertNull(CordnMessageReferences.edit(1010, "new", emptyArray()))
    }

    @Test
    fun `a deletion needs kind 5 with e and a numeric k`() {
        val tags = CordnMessageReferences.deleteTags(target())
        val ref = assertNotNull(CordnMessageReferences.delete(5, tags))
        assertEquals(CordnMessageKinds.TEXT, ref.targetKind)

        assertNull(CordnMessageReferences.delete(9, tags))
        assertNull(CordnMessageReferences.delete(5, arrayOf(arrayOf("e", "1".repeat(64)))), "no k")
    }

    @Test
    fun `a pin needs kind 1011, an e tag, and a known op`() {
        assertEquals(
            PinOp.ADD,
            assertNotNull(CordnMessageReferences.pin(1011, CordnMessageReferences.pinTags(target(), PinOp.ADD))).op,
        )
        assertEquals(
            PinOp.REMOVE,
            assertNotNull(CordnMessageReferences.pin(1011, CordnMessageReferences.pinTags(target(), PinOp.REMOVE))).op,
        )

        assertNull(
            CordnMessageReferences.pin(1011, arrayOf(arrayOf("e", "1".repeat(64)), arrayOf("op", "toggle"))),
            "an unknown op is neither a pin nor an unpin",
        )
        assertNull(CordnMessageReferences.pin(1011, arrayOf(arrayOf("e", "1".repeat(64)))), "no op")
    }

    @Test
    fun `every builder produces tags its own parser accepts`() {
        // The failure this guards is a client that emits what it cannot read.
        val t = target()
        assertNotNull(CordnMessageReferences.thread(CordnMessageReferences.replyTags(t)))
        assertNotNull(CordnMessageReferences.reaction(7, "+", CordnMessageReferences.reactionTags(t)))
        assertNotNull(CordnMessageReferences.edit(1010, "x", CordnMessageReferences.editTags(t)))
        assertNotNull(CordnMessageReferences.delete(5, CordnMessageReferences.deleteTags(t)))
        assertNotNull(CordnMessageReferences.pin(1011, CordnMessageReferences.pinTags(t, PinOp.ADD)))
    }
}
