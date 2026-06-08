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
package com.vitorpamplona.quartz.utils

import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventFactoryIsKnownKindTest {
    @Test
    fun knownForTypedKinds() {
        assertTrue(EventFactory.isKnownKind(TextNoteEvent.KIND), "kind ${TextNoteEvent.KIND} (text note) should be known")
        assertTrue(EventFactory.isKnownKind(RepostEvent.KIND), "kind ${RepostEvent.KIND} (repost) should be known")
        assertTrue(EventFactory.isKnownKind(GenericRepostEvent.KIND), "kind ${GenericRepostEvent.KIND} (generic repost) should be known")
    }

    @Test
    fun unknownForUntypedKind() {
        // 16767 is a Ditto-proprietary "Active profile theme" event with no Quartz class,
        // so it is parsed as a bare Event and reported as not known.
        assertFalse(EventFactory.isKnownKind(16767), "kind 16767 has no Quartz class and should be unknown")
    }
}
