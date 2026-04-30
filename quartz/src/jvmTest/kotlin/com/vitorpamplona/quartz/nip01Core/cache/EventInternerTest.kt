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
package com.vitorpamplona.quartz.nip01Core.cache

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EventInternerTest {
    private val signer = NostrSignerSync()
    private lateinit var interner: EventInterner

    @BeforeTest
    fun setUp() {
        Secp256k1Instance
        interner = EventInterner()
    }

    @AfterTest
    fun tearDown() {
        interner.clear()
    }

    @Test
    fun internReturnsFirstInstance() {
        val original = signer.sign(TextNoteEvent.build("hello", createdAt = 100))
        val canonical = interner.intern(original)
        assertSame(original, canonical)
        assertEquals(1, interner.size())
    }

    @Test
    fun internCollapsesDuplicates() {
        val a = signer.sign(TextNoteEvent.build("hello", createdAt = 100))
        // Round-trip through OptimizedJsonMapper directly (NOT
        // Event.fromJson, which would already intern via Default) so
        // we get a non-identical-but-equal event for our isolated
        // interner to deduplicate.
        val b = OptimizedJsonMapper.fromJson(a.toJson()) as TextNoteEvent
        assertEquals(a.id, b.id)

        val firstCanonical = interner.intern(a)
        val secondCanonical = interner.intern(b)
        assertSame(firstCanonical, secondCanonical)
        assertSame(a, secondCanonical)
        assertEquals(1, interner.size())
    }

    @Test
    fun getReturnsLiveEntry() {
        val a = signer.sign(TextNoteEvent.build("hello", createdAt = 100))
        interner.intern(a)
        assertSame(a, interner.get(a.id))
    }

    @Test
    fun getReturnsNullForUnknownId() {
        assertNull(interner.get("0".repeat(64)))
    }

    @Test
    fun getEvictsDeadEntries() {
        // Confine the event to a helper so no strong ref leaks into
        // this method's stack frame after it returns. The interner's
        // WeakReference is then the only thing holding it.
        val id = internAndDiscard(interner)
        for (i in 0..40) {
            System.gc()
            Thread.sleep(20)
            if (interner.get(id) == null) break
        }
        assertNull(interner.get(id))
        assertEquals(0, interner.size(), "dead entry should be evicted on access")
    }

    private fun internAndDiscard(interner: EventInterner): HexKey {
        val ev = signer.sign(TextNoteEvent.build("hello", createdAt = 100))
        interner.intern(ev)
        return ev.id
    }

    @Test
    fun draftChurnDoesNotLeak() {
        // 100 distinct drafts, no strong references retained.
        val ids = ArrayList<HexKey>(100)
        for (i in 0 until 100) {
            val e = signer.sign(TextNoteEvent.build("draft-$i", createdAt = 1_000L + i))
            ids.add(e.id)
            interner.intern(e)
        }
        // Force GC + sweep — calling get() on every id triggers
        // the on-access cleanup path.
        for (i in 0..40) {
            System.gc()
            Thread.sleep(20)
            ids.forEach { interner.get(it) }
            if (interner.size() == 0) break
        }
        assertTrue(interner.size() <= 5, "expected most drafts to be evicted, got ${interner.size()}")
    }

    @Test
    fun defaultInstanceIsShared() {
        val a = signer.sign(TextNoteEvent.build("hello", createdAt = 100))
        val canonical = EventInterner.Default.intern(a)
        // A second intern call from anywhere returns the same canonical.
        val b = EventInterner.Default.intern(a)
        assertSame(canonical, b)
        EventInterner.Default.clear()
    }
}
